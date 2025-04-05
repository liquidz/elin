(ns elin.task.doc
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [elin.config :as e.config]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.task.doc.analysis :as analysis]
   [elin.task.doc.asciidoc :as asciidoc]
   [elin.task.help :as help]))

;; Utils {{{
(def ^:private page-dir
  (io/file "doc" "pages" "generated"))

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

(defn- format-command-to-key-map
  [command-str]
  (str command-str "-default-mapping"))

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

(def ^:private command-key-mapping-dict
  (let [grouped-lines (help/get-grouped-lines)
        {:keys [mappings default-mappings]} (help/parse-grouped-lines grouped-lines)
        command-to-mapping-dict (->> (reverse mappings)
                                     (into-hash-map :command :mapping))
        mapping-to-key-dict (->> (reverse default-mappings)
                                 (into-hash-map :mapping :mapping-key))]
    (update-vals command-to-mapping-dict
                 #(get mapping-to-key-dict %))))

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

(defn- to-s
  [arr]
  (str (->> arr
            (flatten)
            (str/join "\n"))
       "\n\n"))
;; }}}

;; Interceptors {{{
(defn- interceptor-title
  [interceptor-sym]
  (some-> (re-seq #"\.(interceptor\..+?)$" (str interceptor-sym))
          (first)
          (second)))

(defn- format-interceptor-kind
  [v]
  (condp = (:kind @v)
    e.c.interceptor/all "Always executed."
    e.c.interceptor/autocmd "Executed on autocmd fired."
    e.c.interceptor/connect "Executed on connection."
    e.c.interceptor/disconnect "Executed on disconnection."
    e.c.interceptor/evaluate "Executed on evaluation."
    e.c.interceptor/handler "Executed on calling handler."
    e.c.interceptor/nrepl "Executed on requesting to nREPL server."
    e.c.interceptor/output "Executed on output from nREPL server."
    e.c.interceptor/raw-nrepl "Executed on communicating with nREPL server."
    e.c.interceptor/test "Executed on testing."
    e.c.interceptor/quickfix "Executed on setting quickfix."
    e.c.interceptor/debug "Executed on debugging."
    e.c.interceptor/modify-code "Executed on modifying code."
    e.c.interceptor/tap "Executed on tapping some values."
    e.c.interceptor/http-route "Executed on creating http routes."
    e.c.interceptor/http-request "Executed on handling requests for HTTP server."
    nil))

(defn- generate-interceptor-document
  [interceptor-sym]
  (let [title (interceptor-title interceptor-sym)
        v (requiring-resolve interceptor-sym)
        m (meta v)]
    (to-s
      [(str "==== " title)
       (format-interceptor-kind v)
       ""
       (format-docstring m)
       (asciidoc/source-link m)])))

(defn- generate-interceptor-documents
  []
  (let [global-interceptor-syms (get-in config [:interceptor :includes])
        handler-using-interceptor-syms (->> (get-in config [:handler :config-map])
                                            (vals)
                                            (map e.config/expand-config)
                                            (keep (comp seq :includes :interceptor))
                                            (flatten)
                                            (distinct))
        alias-interceptor-syms  (->> (get-in config [:handler :aliases])
                                     (vals)
                                     (mapcat #(-> (:config %)
                                                  (e.config/expand-config)
                                                  (get-in [:interceptor :includes]))))
        interceptor-syms (->> (concat global-interceptor-syms
                                      handler-using-interceptor-syms
                                      alias-interceptor-syms
                                      '[elin.interceptor.handler.evaluate/hook])
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
                                                    (get analysis/handler-using-interceptor-kind-dict)
                                                    (get analysis/kind-to-interceptor-symbol-dict))
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
            (str "* " (asciidoc/anchor (interceptor-title interceptor-sym))))
          ""])

       (asciidoc/source-link m)])))

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

(defn- generate-default-key-mapping-variables
  []
  (let [handler-to-mapping (map (fn [[handler-symbol mapping-key]]
                                  (format ":%s: %s" (format-handler-to-key-map handler-symbol) mapping-key))
                                handler-key-mapping-dict)
        command-to-mapping (map (fn [[command mapping-key]]
                                  (format ":%s: %s" (format-command-to-key-map command) mapping-key))
                                command-key-mapping-dict)]

    (->> (concat handler-to-mapping
                 command-to-mapping)
         (str/join "\n")
         (spit (io/file page-dir "variables.adoc")))))

(defn- generate-command-documents
  []
  (->> commands
       (keep (fn [{:keys [command handler interceptor]}]
               (when-let [title (handler-title (symbol handler))]
                 (let [body (if (seq interceptor)
                              [(format "Calls %s handler with the following interceptors:" (asciidoc/anchor title))
                               ""
                               (->> interceptor
                                    (map #(format "* %s" (asciidoc/anchor (interceptor-title %)))))]
                              (format "Calls %s handler." (asciidoc/anchor title)))]
                   [(format "==== %s" command)
                    ""
                    body
                    ""]))))
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
