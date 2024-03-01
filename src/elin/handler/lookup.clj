(ns elin.handler.lookup
  (:require
   [clojure.string :as str]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.error :as e]
   [elin.function.core :as e.f.core]
   [elin.function.vim :as e.f.vim]
   [elin.function.vim.popup :as e.f.v.popup]
   [elin.function.vim.sexp :as e.f.v.sexp]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.schema.handler :as e.s.handler]
   [malli.core :as m]))

(def ^:private subsection-separator
  "----------------------------------------------------------------------------")

;; (def ^:private spec-fn-set
;;   #{"clojure.spec.alpha/fspec"
;;     "clojure.spec.alpha/cat"
;;     "clojure.spec.alpha/keys"
;;     "clojure.spec.alpha/or"})
;;
;; (defn- add-indent [n s]
;;   (if (zero? n)
;;     s
;;     (let [spc (apply str (repeat n " "))]
;;       (str/replace s #"\r?\n" (str "\n" spc)))))
;;
;; (defn- format-spec
;;   [spec]
;;   (let [p #(if (empty? %) "nil" (str %))
;;         fn-name (first spec)]
;;     (cond
;;       (not (sequential? spec))
;;       (p spec)
;;
;;       (contains? spec-fn-set fn-name)
;;       (let [res (map (fn [[k v]]
;;                        (let [v' (if (sequential? v)
;;                                   (format-spec v)
;;                                   (p v))]
;;                          (format "  %s %s" k (add-indent (count k) v'))))
;;                      (partition 2 (rest spec)))]
;;         (if (= 1 (count res))
;;           (format "(%s %s)" fn-name (str/trim (first res)))
;;           (format "(%s\n%s)" fn-name (str/join "\n" res))))
;;
;;       (= \: (ffirst spec))
;;       (format "[%s]" (str/join " " spec)))))

(defn- generate-javadoc
  [lookup-resp]
  (e/let [title [(format "*%s*" (if-let [member-str (:member lookup-resp)]
                                  (format "%s/%s" (:class lookup-resp) member-str)
                                  (:class lookup-resp)))]
          arglist (when-let [arglists-str (:arglists-str lookup-resp)]
                    (->> (str/split-lines arglists-str)
                         (map #(str "  " %))))
          doc-str (or (:doc lookup-resp) "No document")
          doc-str (if (string? doc-str) doc-str "No document")
          docs (->> (str "  " doc-str)
                    (str/split-lines))
          returns (when-let [returns (:returns lookup-resp)]
                    [""
                     subsection-separator
                     "*Returns*"
                     (format "  %s" returns)])
          javadoc (when-let [javadoc (:javadoc lookup-resp)]
                    [""
                     javadoc])]
    (->> (concat title arglist docs returns javadoc)
         (remove nil?)
         (str/join "\n"))))

(defn- generate-cljdoc
  [lookup-resp]
  (e/let [_ (when (not (contains? lookup-resp :name))
              (e/fault))
          title [(format "*%s*" (if-let [ns-str (:ns lookup-resp)]
                                  (format "%s/%s" ns-str (:name lookup-resp))
                                  (:name lookup-resp)))]
          arglist (when-let [arglists-str (:arglists-str lookup-resp)]
                    (->> (str/split-lines arglists-str)
                         (map #(str "  " %))))
          doc-str (or (:doc lookup-resp) "No document")
          doc-str (if (string? doc-str) doc-str "No document")
          docs (->> (str "  " doc-str)
                    (str/split-lines))
          ;; TODO spec
          see-also (when-let [see-also (:see-also lookup-resp)]
                     (concat
                      [""
                       subsection-separator
                       "*see-also*"]
                      (map #(format " - %s" %) see-also)))]
    (->> (concat title arglist docs see-also)
         (remove nil?)
         (str/join "\n"))))

(defn- generate-doc
  [lookup-resp]
  (if (contains? lookup-resp :javadoc)
    (generate-javadoc lookup-resp)
    (generate-cljdoc lookup-resp)))

(m/=> lookup [:=> [:cat e.s.handler/?Elin] any?])
(defn lookup
  [{:as elin :component/keys [host interceptor]}]
  (e/let [{:keys [lnum col]} (e.f.vim/get-cursor-position!! host)
          ns-str (e.f.v.sexp/get-namespace!! host)
          {:keys [code]} (e.f.v.sexp/get-expr!! host lnum col)
          resp (e.f.core/lookup!! elin ns-str code)
          context (assoc elin
                         :lookup resp
                         :popup-options {:line "near-cursor"
                                         :border []
                                         :filetype "help"
                                         :moved "current-line"})
          {:keys [popup-body popup-options]} (e.p.interceptor/execute
                                              interceptor
                                              e.c.interceptor/lookup
                                              context
                                              (fn [{:as ctx :keys [lookup]}]
                                                (update ctx :popup-body #(or % (generate-doc lookup)))))]
    (e.f.v.popup/open!! host popup-body popup-options)))
