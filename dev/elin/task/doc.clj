(ns elin.task.doc
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [elin.config :as e.config]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.task.help :as help]
   [rewrite-clj.zip :as r.zip]))

(def ^:private page-dir
  (io/file "doc" "pages" "generated"))

(def ^:private github-base-url
  "https://github.com/liquidz/elin/blob/main")

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
  (when-let [idx (str/index-of file "/liquidz/elin/src/")]
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
  (-> (str handler-symbol)
      (str/replace #"[()]" "")
      (str/replace #"[./#]" "-")))

(defn- into-hash-map
  [key-fn val-fn coll]
  (reduce (fn [accm x]
            (assoc accm (key-fn x) (val-fn x)))
          {} coll))

(defn- format-docstring
  [meta-data]
  (if-let [doc (:doc meta-data)]
    (->> (str/split-lines doc)
         (map #(str/replace % #"^\s{2}?" ""))
         (str/join "\n"))
    "No document."))

(def ^:private commands
  (-> (help/get-grouped-lines)
      (help/parse-grouped-lines)
      (:commands)))

(def ^:private handler-key-mapping-dict
  (let [grouped-lines (help/get-grouped-lines)
        {:keys [commands mappings default-mappings]} (help/parse-grouped-lines grouped-lines)
        command-to-mapping-dict (->> (reverse mappings)
                                     (into-hash-map :command :mapping))
        mapping-to-key-dict (->> (reverse default-mappings)
                                 (into-hash-map :mapping :mapping-key))]
    (-> (into-hash-map :handler :command (reverse commands))
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

(defn- convert-title
  [title]
  (-> (str "_" title)
      (str/replace "/" "")
      (str/replace "-" "_")
      (str/replace "." "_")))

(defn- anchor
  [title]
  (str "<<" (convert-title title) ">>"))

(defn- source-link
  [meta-data]
  (when-let [link (github-link meta-data)]
    (when-let [idx (str/index-of  link "main/src")]
      (format "\n[.text-right]\n[.small]#link:%s[%s]#"
              link
              (subs link (inc (str/index-of link "/src/" idx)))))))
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
  (some-> (re-seq #"\.(interceptor\..+?)$" (str interceptor-sym))
          (first)
          (second)))

(defn- format-interceptor-kind
  [v]
  (condp = (:kind @v)
    e.c.interceptor/all "always executed"
    e.c.interceptor/autocmd "executed on autocmd fired"
    e.c.interceptor/connect "executed on connection"
    e.c.interceptor/disconnect "executed on disconnection"
    e.c.interceptor/evaluate "executed on evaluation"
    e.c.interceptor/handler "executed on calling handler"
    e.c.interceptor/nrepl "executed on requesting to nREPL server"
    e.c.interceptor/output "executed on output from nREPL server"
    e.c.interceptor/raw-nrepl "executed on communicating with nREPL server"
    e.c.interceptor/test "executed on testing"
    e.c.interceptor/quickfix "executed on setting quickfix"
    e.c.interceptor/debug "executed on debugging"
    e.c.interceptor/code-change "executed on changing code"
    nil))

(defn- generate-interceptor-document
  [interceptor-sym]
  (let [title (interceptor-title interceptor-sym)
        v (requiring-resolve interceptor-sym)
        m (meta v)]
    (to-s
     [(str "==== " title)
      (->> (format-interceptor-kind v)
           (format "Executed on %s."))
      ""
      (format-docstring m)
      (source-link m)])))

(defn- generate-interceptor-documents
  []
  (let [global-interceptor-syms (get-in config [:interceptor :includes])
        handler-using-interceptor-syms (->> (get-in config [:handler :config-map])
                                            (vals)
                                            (map e.config/expand-config)
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
(defn- handler-title
  [handler-sym]
  (some-> (re-seq #"\.(handler\..+?)$" (str handler-sym))
          (first)
          (second)))

(defn- generate-handler-document
  [handler-sym]
  (let [title (handler-title handler-sym)
        m (meta (requiring-resolve handler-sym))
        handler-config (-> (get-in config [:handler :config-map handler-sym])
                           (or {})
                           (e.config/expand-config))
        using-interceptor-syms (concat (or (some->> (get-in handler-config [:interceptor :includes])
                                                    (seq)
                                                    (sort))
                                           [])
                                       (or (some->> handler-sym
                                                    (get handler-using-interceptor-kind-dict)
                                                    (get kind-to-interceptor-symbol-dict))
                                           []))]
    (to-s
     [(str "==== " title)
      (format-docstring m)

      (when (get handler-key-mapping-dict handler-sym)
        [""
         (format "Default key mapping: `{%s}`"
                 (format-handler-to-key-map handler-sym))])

      (when (seq using-interceptor-syms)
        [""
         "===== Using interceptors"
         (for [interceptor-sym using-interceptor-syms]
           (str "* " (anchor (interceptor-title interceptor-sym))))
         ""])

      (source-link m)])))

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

(defn- generate-command-documents
  []
  (->> commands
       (keep (fn [{:keys [command handler]}]
               (when-let [title (handler-title (symbol handler))]
                 [(format "===== %s" command)
                  ""
                  (format "Calls %s handler." (anchor title))
                  ""])))
       (flatten)
       (str/join "\n")
       (spit (io/file page-dir "commands.adoc"))))

(defn -main
  [& _]
  (.mkdir page-dir)
  (generate-handler-documents)
  (generate-interceptor-documents)
  (generate-default-key-mapping-variables)
  (generate-command-documents)
  (println "Generated asciidoc files."))

(comment
  (with-redefs [spit println]
    (generate-interceptor-documents)))
