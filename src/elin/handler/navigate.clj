(ns elin.handler.navigate
  (:require
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [elin.error :as e]
   [elin.function.clj-kondo :as e.f.clj-kondo]
   [elin.function.evaluate :as e.f.evaluate]
   [elin.function.lookup :as e.f.lookup]
   [elin.function.nrepl :as e.f.nrepl]
   [elin.function.nrepl.namespace :as e.f.n.namespace]
   [elin.function.quickfix :as e.f.quickfix]
   [elin.function.sexpr :as e.f.sexpr]
   [elin.protocol.clj-kondo :as e.p.clj-kondo]
   [elin.protocol.host :as e.p.host]
   [elin.schema.handler :as e.s.handler]
   [elin.util.file :as e.u.file]
   [elin.util.handler :as e.u.handler]
   [malli.core :as m]
   [pogonos.core :as pogonos]))

(defn- select-ns-and-sym-str [ns-str sym-str]
  (let [sym (symbol sym-str)]
    (cond
      (or (seq ns-str)
          (not (qualified-symbol? sym)))
      {:ns-str ns-str :sym-str sym-str}

      :else
      {:ns-str (namespace sym) :sym-str (name sym)})))

(defn- normalize-var-code
  [code]
  (str/replace-first code #"^#?'" ""))

(m/=> jump [:=> [:cat e.s.handler/?Elin] any?])
(defn jump
  "Jump to the specified location."
  [{:component/keys [host] :keys [message]}]
  (let [{:keys [path lnum col]} (->> message
                                     (:params)
                                     (first)
                                     (e.u.file/decode-path))]
    (when path
      (async/<!! (e.p.host/jump! host path lnum col)))))

(m/=> jump-to-definition [:=> [:cat e.s.handler/?Elin] [:alt e.s.handler/?JumpToFile boolean?]])
(defn jump-to-definition
  "Jump to the definition of the symbol under the cursor."
  [{:as elin :component/keys [host]}]
  (e/let [{:keys [lookup-config]} (e.u.handler/config elin #'jump-to-definition)
          {:keys [lnum col]} (async/<!! (e.p.host/get-cursor-position! host))
          ns-str (e/error-or (e.f.sexpr/get-namespace elin)
                             "")
          {:keys [code]} (e.f.sexpr/get-expr elin lnum col)
          code (normalize-var-code code)
          {:keys [ns-str sym-str]} (select-ns-and-sym-str ns-str code)
          {:keys [file line column protocol-implementations]} (e.f.lookup/lookup elin ns-str sym-str lookup-config)]
    (cond
      (seq protocol-implementations)
      (do (e.p.host/echo-text host "Multiple implementations found. See location list.")
          (->> protocol-implementations
               (map #(hash-map :filename (:filename %)
                               :lnum (:row %)
                               :col (:col %)
                               :text (str (:impl-ns %)
                                          ": "
                                          (:protocol-name %)
                                          "/"
                                          (:method-name %))
                               :type "Protocol"))
               (cons {:filename file
                      :lnum line
                      :col column
                      :text (str ns-str "/" sym-str)
                      :type "Definition"})
               (e.f.quickfix/set-location-list elin))
          true)

      (and file line)
      (e.u.handler/jump-to-file-response file line (or column 1)))))

(m/=> cycle-source-and-test [:=> [:cat e.s.handler/?Elin] e.s.handler/?JumpToFile])
(defn cycle-source-and-test
  "Cycle source code and test code."
  [{:as elin :component/keys [host]}]
  (let [ns-path (async/<!! (e.p.host/get-current-file-path! host))
        ns-str (e.f.sexpr/get-namespace elin)
        file-sep (e.u.file/guess-file-separator ns-path)
        cycled-path (e.f.n.namespace/get-cycled-namespace-path
                      {:ns ns-str :path ns-path :file-separator file-sep})]
    (e.u.handler/jump-to-file-response cycled-path)))

(m/=> cycle-function-and-test [:=> [:cat e.s.handler/?Elin] e.s.handler/?JumpToFile])
(defn cycle-function-and-test
  [elin]
  (e/let [{:keys [template]} (e.u.handler/config elin #'cycle-function-and-test)
          {:keys [options]} (e.f.evaluate/get-var-name-from-current-top-list elin)
          {ns-str :ns var-name :var-name path :file} options
          file-sep (e.u.file/guess-file-separator path)
          cycled-path (e.f.n.namespace/get-cycled-namespace-path
                        {:ns ns-str :path path :file-separator file-sep})
          cycled-ns-str (or (e.f.n.namespace/guess-namespace-from-path cycled-path)
                            ;; TODO fallback to another process
                            (e/fault))
          var-name (-> var-name
                       (str/split #"/" 2)
                       (second))
          cycled-var-name (e.f.nrepl/get-cycled-var-name var-name)
          lookup-resp (e/error-or (e.f.lookup/lookup elin cycled-ns-str cycled-var-name))]
    (if lookup-resp
      (e.u.handler/jump-to-file-response (:file lookup-resp)
                                         (:line lookup-resp)
                                         (:column lookup-resp))
      (if (not (str/ends-with? cycled-ns-str "-test"))
        (e.u.handler/jump-to-file-response cycled-path)
        (e/let [cycled-file (io/file cycled-path)
                ext (e.u.file/get-file-extension cycled-path)
                ns-template (or (get-in template [(keyword ext) :test])
                                (e/not-found))
                var-template (or (get-in template [(keyword ext) :test-var])
                                 (e/not-found))
                params {:path cycled-path
                        :ns cycled-ns-str
                        :source-ns ns-str
                        :test? true
                        :name cycled-var-name
                        :source-name var-name}]
          (when-not (.exists (io/file cycled-path))
            (spit cycled-path
                  (pogonos/render-string ns-template params)))

          (let [tail-lnum (-> (slurp cycled-file)
                              (str/split-lines)
                              (count))]
            (spit cycled-file
                  (str "\n" (pogonos/render-string var-template params))
                  :append true)
            (e.u.handler/jump-to-file-response cycled-path
                                               (+ 2 tail-lnum)
                                               1)))))))

;; (m/=> references [:=> [:cat e.s.handler/?Elin] (e.schema/error-or e.s.handler/?JumpToFile)])
(defn references
  "Find the places where the symbol under the cursor is used, and jump if there is only one.
  If there are multiple, add them to the location list."
  [{:as elin :component/keys [host clj-kondo]}]
  (e/let [{:keys [lnum col]} (async/<!! (e.p.host/get-cursor-position! host))
          ns-str (e/error-or (e.f.sexpr/get-namespace elin)
                             "")
          {:keys [code]} (e.f.sexpr/get-expr elin lnum col)
          code (normalize-var-code code)
          {:keys [ns-str sym-str]} (select-ns-and-sym-str ns-str code)
          refs (e.f.clj-kondo/references clj-kondo ns-str sym-str)]
    (cond
      (empty? refs)
      (e/not-found)

      (= 1 (count refs))
      (let [{:keys [filename lnum col]} (first refs)]
        (e.u.handler/jump-to-file-response filename lnum col))

      :else
      (do (e.p.host/echo-text host "Multiple references found. See location list.")
          (->> refs
               (map #(hash-map :filename (:filename %)
                               :lnum (:lnum %)
                               :col (:col %)
                               :text (str (:ns %))
                               :type "Reference"))
               (e.f.quickfix/set-location-list elin))))))

(defn local-references
  [{:as elin :component/keys [host clj-kondo]}]
  (e/let [path (async/<!! (e.p.host/get-current-file-path! host))
          {cur-lnum :lnum cur-col :col} (async/<!! (e.p.host/get-cursor-position! host))
          {expr :code} (e.f.sexpr/get-expr elin cur-lnum cur-col)
          expr (normalize-var-code expr)
          {ns-code :code} (e.f.sexpr/get-namespace-sexpr elin)
          ns-code-line-count (count (str/split-lines ns-code))
          {code :code base-lnum :lnum base-col :col} (e.f.sexpr/get-top-list elin cur-lnum cur-col)
          ;; NOTE ns-code is required for clj-kondo to analyze correctly if you use plumatic/schema etc.
          code' (str ns-code "\n" code)
          resp (e.p.clj-kondo/analyze-code!! clj-kondo code')
          local-usages (some->> (get-in resp [:analysis :local-usages])
                                (filter #(= expr (str (:name %)))))
          calc-lnum #(+ base-lnum (- % ns-code-line-count 1))
          calc-col #(+ base-col % -1)]
    (cond
      (empty? local-usages)
      (e/not-found)

      (= 1 (count local-usages))
      (let [{:keys [row col]} (first local-usages)]
        (e.u.handler/jump-to-file-response path (calc-lnum row) (calc-col col)))

      :else
      (do (e.p.host/echo-text host "Multiple references found. See location list.")
          (->> local-usages
               (map #(hash-map :filename path
                               :lnum (calc-lnum (:row %))
                               :col (calc-col (:col %))
                               :text (str (:name %))
                               :type "Reference"))
               (e.f.quickfix/set-location-list elin))))))
