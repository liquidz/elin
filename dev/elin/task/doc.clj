(ns elin.task.doc
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [elin.config :as e.config]
   [elin.task.help :as help]
   [elin.util.interceptor :as e.u.interceptor]
   [rewrite-clj.node :as r.node]
   [rewrite-clj.parser :as r.parser]
   [rewrite-clj.zip :as r.zip]))

(def ^:private page-dir
  (io/file "doc" "pages" "generated"))

(def ^:private github-base-url
  "https://github.com/liquidz/vim-elin/blob/main")

(defn- find-first
  [pred coll]
  (some #(and (pred %) %) coll))

(defn- page-file
  [target-sym]
  (let [handler-ns (namespace target-sym)
        handler-name (name target-sym)
        dir-file (io/file page-dir handler-ns)]
    (.mkdir dir-file)
    (io/file dir-file (str handler-name ".adoc"))))

(defn- page-path
  [target-sym]
  (str "."
       (subs (.getPath (page-file target-sym))
             (count (.getPath page-dir)))))

(defn- github-link
  [{:keys [file line]}]
  (when-let [idx (str/index-of file "/liquidz/vim-elin/src/")]
    (format "%s%s#L%d"
            github-base-url
            (subs file (str/index-of file "/src/" idx))
            line)))

(def ^:private config
  (with-redefs [e.config/load-user-config (constantly {})
                e.config/load-project-local-config (constantly {})]
    (e.config/load-config "." {:server {:host "" :port 0}
                               :env {:cwd "."}})))

(defn- format-handler-to-key-map
  [handler-symbol]
  (str/replace (str handler-symbol) #"[./]" "-"))

(defn- into-hash-map
  [key-fn val-fn coll]
  (reduce (fn [accm x]
            (assoc accm (key-fn x) (val-fn x)))
          {} coll))

(def ^:private handler-key-mapping-dict
  (let [grouped-lines (help/get-grouped-lines)
        {:keys [commands mappings default-mappings]} (help/parse-grouped-lines grouped-lines)
        command-to-mapping-dict (into-hash-map :command :mapping mappings)
        mapping-to-key-dict (into-hash-map :mapping :mapping-key default-mappings)]
    (-> (into-hash-map :handler :command commands)
        (update-vals #(some->> %
                               (get command-to-mapping-dict)
                               (get mapping-to-key-dict)))
        (update-keys symbol))))

;; Analysis {{{
(def ^:private analysis
  (some-> "dev/analysis.edn"
          (slurp)
          (edn/read-string)
          (:analysis)))

(def ^:private var-usages
  "[{:fixed-arities #{1 3 2}
     :end-row 20
     :name-end-col 5
     :name-end-row 20
     :name-row 20
     :name def
     :filename \"src/elin/component/interceptor.clj\"
     :from elin.component.interceptor
     :macro true
     :col 1
     :name-col 2
     :end-col 40
     :arity 2
     :row 20
     :to clojure.core}]"
  (:var-usages analysis))

(defn- using?
  ([qualified-symbol usage]
   (if (qualified-symbol? qualified-symbol)
     (using? (symbol (namespace qualified-symbol))
             (symbol (name qualified-symbol))
             usage)
     false))
  ([ns-sym name-sym usage]
   (and (= ns-sym (:to usage))
        (= name-sym (:name usage)))))

(defn- used?
  ([qualified-symbol usage]
   (if (qualified-symbol? qualified-symbol)
     (used? (symbol (namespace qualified-symbol))
            (symbol (name qualified-symbol))
            usage)
     false))
  ([ns-sym name-sym usage]
   (and (= ns-sym (:from usage))
        (= name-sym (:from-var usage)))))

(defn- recursive-usage?
  [usage]
  (and (= (:from usage) (:to usage))
       (= (:from-var usage) (:name usage))))

(defn- usage->qualified-symbol
  [usage]
  (when-let [from-var (:from-var usage)]
    (symbol (str (:from usage))
            (str from-var))))
;; }}}

;; AsciiDoc {{{
(defn- to-s
  [arr]
  (str (->> arr
            (flatten)
            (str/join "\n"))
       "\n\n"))

(defn- anchor
  [title]
  (str "<<_"
       (-> title
           (str/replace "/" "")
           (str/replace "-" "_")
           (str/replace "." "_"))
       ">>"))
;; }}}

;; Interceptors {{{
(def interceptor-execute-usages
  (filter (partial using? 'elin.protocol.interceptor/execute)
          var-usages))

(defn- dest-usage?
  [usage]
  (some?
   (when-let [from (:from usage)]
     (re-seq #"^elin\.(handler|interceptor)\."
             (name from)))))

(defn traverse-dest-usage
  [base-usages]
  (loop [base-usages base-usages
         dest-usages []]
    (let [next-usages (->> base-usages
                           (mapcat (fn [base-usage]
                                     (filter #(using? (:from base-usage) (:from-var base-usage) %)
                                             var-usages)))
                           (distinct)
                           (remove recursive-usage?))
          {next-dest-usages true next-usages false} (group-by dest-usage? next-usages)]
      (if (seq next-usages)
        (recur next-usages (concat dest-usages next-dest-usages))
        (concat dest-usages next-dest-usages)))))

(defn- interceptor-execute-usage->interceptor-kind
  [{:keys [filename row col]}]
  (let [content (slurp filename)
        zloc (-> (r.zip/of-string content {:track-position? true})
                 (r.zip/find-last-by-pos [row col]))]

    (some-> (if (qualified-symbol? (r.zip/sexpr zloc))
              zloc
              (r.zip/down zloc))
            (r.zip/next)
            (r.zip/next)
            (r.zip/sexpr)
            (name))))

(def ^:private handler-using-interceptor-kind-dict
  (->> interceptor-execute-usages
       (mapcat (fn [usage]
                 (if (usage->qualified-symbol usage)
                   (let [kind (interceptor-execute-usage->interceptor-kind usage)]
                     (->> [usage]
                          (traverse-dest-usage)
                          (keep #(some-> %
                                         (usage->qualified-symbol)
                                         (vector kind)))))
                   [])))
       (into {})))

(def ^:private kind-to-interceptor-symbol-dict
  (->> (get-in config [:interceptor :includes])
       (group-by #(-> (requiring-resolve %)
                      (deref)
                      (:kind)
                      (name)))))

(defn- interceptor-title
  [interceptor-sym]
  (some-> (re-seq #"\.interceptor\.(.+?)$" (str interceptor-sym))
          (first)
          (second)))

(defn- normalize-interceptor
  [interceptor-sym]
  (-> interceptor-sym
      (e.u.interceptor/parse)
      (:symbol)))

(defn- generate-interceptor-document
  [interceptor-sym]
  (let [title (interceptor-title interceptor-sym)
        m (meta (requiring-resolve interceptor-sym))
        link (github-link m)]
    (to-s
     [(str "==== " title)
      ""
      (when link
        [(str "[.text-right]\n[small]#link:" link "[source]#")
         ""])

      (or (:doc m) "TODO?")])))

(defn- generate-interceptor-documents
  []
  (let [global-interceptor-syms (->> (get-in config [:interceptor :includes])
                                     (map normalize-interceptor))
        handler-using-interceptor-syms (->> (get-in config [:handler :config-map])
                                            (vals)
                                            (keep (comp seq :includes :interceptor))
                                            (flatten)
                                            (distinct))
        interceptor-syms (->> (concat global-interceptor-syms handler-using-interceptor-syms)
                              (distinct)
                              (sort))]
    (doseq [interceptor-sym interceptor-syms]
      (println "Generating interceptor:" interceptor-sym)
      (let [file (page-file interceptor-sym)]
        (spit file (generate-interceptor-document interceptor-sym))))

    (spit (io/file page-dir "interceptors.adoc")
          (->> interceptor-syms
               (map #(str "include::" (page-path %) "[]"))
               (str/join "\n")))))
;; }}}

;; Handlers {{{
(defn- generate-handler-document
  [handler-sym]
  (let [title (some-> (re-seq #"\.handler\.(.+?)$" (str handler-sym))
                      (first)
                      (second))
        m (meta (requiring-resolve handler-sym))
        link (github-link m)
        handler-config (or (get-in config [:handler :config-map handler-sym])
                           {})
        using-interceptor-syms (concat (or (some->> (get-in handler-config [:interceptor :includes])
                                                    (seq)
                                                    (map normalize-interceptor)
                                                    (sort))
                                           [])
                                       (or (some->> handler-sym
                                                    (get handler-using-interceptor-kind-dict)
                                                    (get kind-to-interceptor-symbol-dict))
                                           []))]
    (to-s
     [(str "==== " title)
      (when link
        (str "link:" link "[source]"))

      ""
      (or (:doc m) "TODO")

      (when (get handler-key-mapping-dict handler-sym)
        [""
         (format "Default key mapping: `{%s}`"
                 (format-handler-to-key-map handler-sym))])

      (when (seq using-interceptor-syms)
        [""
         "===== Using interceptors"
         (for [interceptor-sym using-interceptor-syms]
           (str "* " (anchor (interceptor-title interceptor-sym))))
         ""])])))

(some->> 'elin.handler.navigate/references
         (get handler-using-interceptor-kind-dict)
         (get kind-to-interceptor-symbol-dict))

(defn- excluded-handler?
  [handler-sym]
  (or (some? (re-seq #"^elin\.handler\.(internal)"
                     (namespace handler-sym)))
      (str/ends-with? (name handler-sym) "*")))

(defn- generate-handler-documents
  []
  (let [handler-syms (->> (get-in config [:handler :includes])
                          (remove excluded-handler?)
                          (sort))]
    (doseq [handler-sym handler-syms]
      (let [file (page-file handler-sym)]
        (spit file (generate-handler-document handler-sym))))

    (spit (io/file page-dir "handlers.adoc")
          (->> handler-syms
               (map #(str "include::" (page-path %) "[]"))
               (str/join "\n")))))
;; }}}



;; ====================================================



(def sample-interceptor-execute-usages
  (filter #(= "src/elin/function/quickfix.clj"
              (:filename %))
          interceptor-execute-usages))


(comment
  (traverse-dest-usage sample-interceptor-execute-usages)

  (->> (traverse-dest-usage sample-interceptor-execute-usages)
       (map usage->qualified-symbol))
  (let [target (first sample-interceptor-execute-usages)
        kind-usage (find-first
                    #(and (used? (:from target) (:from-var target) %)
                          (= 'elin.constant.interceptor (:to %)))
                    var-usages)]
    {:kind (:name kind-usage)
     :dest (->> [target]
                (traverse-dest-usage)
                (map #(symbol (str (:from %)) (str (:from-var %)))))}))

(defn detect-interceptor-kind [usage]
  (some->> var-usages
           (find-first #(and (used? (:from usage) (:from-var usage) %)
                             (= 'elin.constant.interceptor (:to %))))
           (:name)))

(defn- generate-default-key-mapping-variables
  []
  (->> handler-key-mapping-dict
       (map (fn [[handler-symbol mapping-key]]
              (format ":%s: %s" (format-handler-to-key-map handler-symbol) mapping-key)))
       (str/join "\n")
       (spit (io/file page-dir "variables.adoc"))))

(defn -main
  [& _]
  (.mkdir page-dir)
  (generate-handler-documents)
  (generate-interceptor-documents)
  (generate-default-key-mapping-variables)
  (println "Generated asciidoc files."))
