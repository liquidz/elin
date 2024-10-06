(ns elin.handler.navigate
  (:require
   [clojure.core.async :as async]
   [clojure.string :as str]
   [elin.error :as e]
   [elin.function.clj-kondo :as e.f.clj-kondo]
   [elin.function.file :as e.f.file]
   [elin.function.lookup :as e.f.lookup]
   [elin.function.nrepl.namespace :as e.f.n.namespace]
   [elin.function.quickfix :as e.f.quickfix]
   [elin.function.sexpr :as e.f.sexpr]
   [elin.protocol.host :as e.p.host]
   [elin.schema.handler :as e.s.handler]
   [elin.util.file :as e.u.file]
   [malli.core :as m]))

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

(m/=> jump-to-definition [:=> [:cat e.s.handler/?Elin] any?])
(defn jump-to-definition
  "Jump to the definition of the symbol under the cursor."
  [{:as elin :component/keys [host]}]
  (e/let [{:keys [lnum col]} (async/<!! (e.p.host/get-cursor-position! host))
          ns-str (e/error-or (e.f.sexpr/get-namespace elin)
                             "")
          {:keys [code]} (e.f.sexpr/get-expr elin lnum col)
          code (normalize-var-code code)
          {:keys [ns-str sym-str]} (select-ns-and-sym-str ns-str code)
          {:keys [file line column protocol-implementations]} (e.f.lookup/lookup elin ns-str sym-str)]
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
               (e.f.quickfix/set-location-list elin)))

      (and file line)
      (async/<!! (e.p.host/jump! host file line (or column 1))))

    true))

(m/=> cycle-source-and-test [:=> [:cat e.s.handler/?Elin] any?])
(defn cycle-source-and-test
  "Cycle source code and test code."
  [{:as elin :component/keys [host]}]
  (let [ns-path (async/<!! (e.p.host/get-current-file-path! host))
        ns-str (e.f.sexpr/get-namespace elin)
        file-sep (e.u.file/guess-file-separator ns-path)
        cycled-path (e.f.n.namespace/get-cycled-namespace-path
                     {:ns ns-str :path ns-path :file-separator file-sep})]
    (e.f.file/open-as elin cycled-path)))

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
        (async/<!! (e.p.host/jump! host filename lnum col)))

      :else
      (do (e.p.host/echo-text host "Multiple references found. See location list.")
          (->> refs
               (map #(hash-map :filename (:filename %)
                               :lnum (:lnum %)
                               :col (:col %)
                               :text (str (:ns %))
                               :type "Reference"))
               (e.f.quickfix/set-location-list elin))))))
