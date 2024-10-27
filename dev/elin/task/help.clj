(ns elin.task.help
  (:require
   [cheshire.core :as json]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [elin.config :as e.config]))

(def ^:private width 78)

(def ^:private plugin-elin-vim-file
  (io/file "plugin" "elin.vim"))

(def ^:private help-file
  (io/file "doc" "elin-mapping.txt"))

(def ^:private elin-config
  (with-redefs [e.config/load-user-config (constantly {})
                e.config/load-project-local-config (constantly {})]
    (e.config/load-config "." {:server {:host "" :port 0}
                               :env {:cwd "."}})))

(defn- into-hash-map
  [key-fn val-fn coll]
  (reduce (fn [accm x]
            (assoc accm (key-fn x) (val-fn x)))
          {} coll))

(defn- repeat-char
  [n c]
  (apply str (repeat n c)))

(defn- section-title
  [title tag-name]
  (format "%s%s%s"
          (str/upper-case title)
          (repeat-char (- width (count title) (count tag-name)) " ")
          (format "*%s*" tag-name)))

(defn get-grouped-lines
  []
  (->> (slurp plugin-elin-vim-file)
       (str/split-lines)
       (map str/trim)
       (reduce (fn [accm line]
                 (cond
                   (str/starts-with? line "call s:define_mapping(")
                   (update accm :default-mapping conj line)

                   (str/starts-with? line "nnoremap <silent>")
                   (update accm :mapping conj line)

                   (str/starts-with? line "command!")
                   (update accm :command conj line)

                   :else accm))
               {:command [] :mapping [] :default-mapping []})))

(defn- parse-command-line
  [line]
  (let [[command _ func] (-> line
                             (str/replace-first #"command! (-nargs=. )?" "")
                             (str/split #"\s+" 3))

        [handler _params config] (if (re-seq #"^elin#(notify|request)" func)
                                   (-> func
                                       (subs (inc (str/index-of func "(")))
                                       (str/split #",\s*" 3))
                                   [func nil nil])
        handler (str/replace handler #"['\"]" "")
        config (-> config
                   (json/parse-string keyword)
                   (:config)
                   (edn/read-string))
        alias-definition (get-in elin-config [:handler :aliases (symbol handler)])]
    {:command command
     :handler (if alias-definition
                (str (:handler alias-definition))
                handler)
     :interceptor (let [interceptor (-> (if alias-definition
                                          (e.config/merge-configs (:config alias-definition)
                                                                  config)
                                          config)
                                        (get-in [:interceptor]))]
                    (->> (:uses interceptor)
                         (partition 2)
                         (map first)
                         (concat (:includes interceptor))
                         (distinct)
                         (sort)
                         (mapv str)))}))

(defn- parse-mapping-line
  [line]
  (when-let [[_ mapping command] (some->> line
                                          (re-seq #"(<Plug>\(.+?\)).+(Elin[^<]+)")
                                          (first))]
    {:mapping mapping
     :command command}))

(defn- parse-default-mapping-line
  [line]
  (let [[map-type mapping-key mapping] (-> line
                                           (subs (inc (str/index-of line "(")))
                                           (str/split #",\s*" 3))]
    {:map-type (->> (str/trim map-type)
                    (re-seq #"^['\"](.+?)['\"]$")
                    (first)
                    (second))
     :mapping-key (->> (str/trim mapping-key)
                       (re-seq #"^['\"](.+?)['\"]$")
                       (first)
                       (second))
     :mapping (->> (str/trim mapping)
                   (re-seq #"^['\"](.+?)['\"]\)$")
                   (first)
                   (second))}))

(defn parse-grouped-lines
  [grouped-lines]
  {:commands (->> (:command grouped-lines)
                  (map parse-command-line))
   :mappings (->> (:mapping grouped-lines)
                  (map parse-mapping-line))
   :default-mappings (->> (:default-mapping grouped-lines)
                          (map parse-default-mapping-line))})

(defn- generate-command-help-contents
  [{:keys [commands mappings]}]
  (let [command-to-mapping-dict (into-hash-map :command :mapping mappings)]
    (->> (for [{:keys [command handler interceptor]} commands]
           (let [tag (format "%s*%s*"
                             (repeat-char (- width (count command)) " ")
                             command)
                 tail (if (seq interceptor)
                        (str "\n  with the following interceptors:\n"
                             (->> interceptor
                                  (map #(format "  - `%s`" %))
                                  (str/join "\n"))
                             "\n")
                        ".")]
             (->> [tag
                   command
                   (if (str/includes? handler "#")
                     (format "  Calls `%s`%s" handler tail)
                     (format "  Calls `%s` handler%s" handler tail))
                   (format "  Key is mapped to |%s|." (get command-to-mapping-dict command))]
                  (str/join "\n"))))
         (str/join "\n\n"))))

(defn- generate-mapping-help-contents
  [{:keys [mappings]}]
  (->> (for [{:keys [mapping command]} mappings]
         (let [tag (format "%s*%s*"
                           (repeat-char (- width (count mapping)) " ")
                           mapping)]
           (->> [tag
                 mapping
                 (format "  Same as |%s|." command)]
                (str/join "\n"))))
       (str/join "\n\n")))

(defn- format-map-type
  [map-type]
  (case map-type
    "nmap" "n"
    "-"))

(defn- generate-default-mapping-help-contents
  [{:keys [default-mappings]}]
  (let [max-mapping-key-length (apply max (map (comp count :mapping-key) default-mappings))
        format-str (format "%%-6s %%-%ds %%s"
                           max-mapping-key-length)]
    (->> (for [{:keys [map-type mapping-key mapping]} default-mappings]
           (format format-str
                   (format-map-type map-type)
                   mapping-key
                   mapping))
         (cons (format format-str "{mode}" "{lhs}" "{rhs}"))
         (str/join "\n"))))

(defn- section
  [title content]
  (let [tag-name (-> (str "elin " title)
                     (str/lower-case)
                     (str/replace " " "-"))]
    (->> [(repeat-char width "-")
          (section-title title tag-name)
          ""
          content
          ""]
         (str/join "\n"))))

(defn -main
  [& _]
  (let [grouped-lines (get-grouped-lines)
        {:keys [commands mappings default-mappings]} (parse-grouped-lines grouped-lines)]
    (->> [(section "commands"
                   (generate-command-help-contents {:commands commands :mappings mappings}))

          (section "mappings"
                   (generate-mapping-help-contents {:mappings mappings}))

          (section "default mappings"
                   (generate-default-mapping-help-contents {:default-mappings default-mappings}))
          (repeat-char width "=")
          (str "vim:tw=" width ":ts=8:ft=help:norl:noet:fen:fdl=0:")]
         (str/join "\n")
         (spit help-file)))
  (println "Generated help files."))

(comment
  (def grouped-lines (get-grouped-lines))
  (def commands (->> (:command grouped-lines)
                     (map parse-command-line)))
  (def mappings (->> (:mapping grouped-lines)
                     (map parse-mapping-line)))
  (def default-mappings (->> (:default-mapping grouped-lines)
                             (map parse-default-mapping-line)))

  (with-redefs [spit (fn [_ content]
                       (println content))]
    (-main)))
