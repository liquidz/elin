(ns elin.task.help
  (:require
   [cheshire.core :as json]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def ^:private plugin-elin-vim-file
  (io/file "plugin" "elin.vim"))

(def ^:private help-file
  (io/file "doc" "elin-default-key.txt"))

(defn- parse-command
  [command]
  (let [start-idx (str/index-of command "{\"config\"")
        end-idx (when start-idx
                  (str/index-of command ")<CR>" start-idx))
        config (when (and start-idx end-idx)
                 (-> (subs command start-idx end-idx)
                     (json/parse-string keyword)
                     (:config)
                     (edn/read-string)))]
    {:handler (->> command
                   (re-seq #"elin\.handler\.[^'\"]+")
                   (first))
     :interceptor (get-in config [:interceptor :includes])}))

(defn- parse-mapping-line
  [s]
  (let [[map-type mapping-key command] (-> s
                                           (subs (inc (str/index-of s "(")))
                                           (str/split #",\s*" 3))
        command (->> (str/trim command)
                     (re-seq #"^['\"](.+?)['\"]\)$")
                     (first)
                     (second))]
    (merge
     {:map-type (->> (str/trim map-type)
                     (re-seq #"^['\"](.+?)['\"]$")
                     (first)
                     (second))
      :mapping-key (->> (str/trim mapping-key)
                        (re-seq #"^['\"](.+?)['\"]$")
                        (first)
                        (second))
      :command command}
     (parse-command command))))

(defn- get-mapping-lines
  []
  (->> (slurp plugin-elin-vim-file)
       (str/split-lines)
       (map str/trim)
       (filter #(str/starts-with? % "call s:define_mapping("))))

(defn- format-map-type
  [map-type]
  (case map-type
    "nmap" "n"
    "-"))

(defn- format-command
  [command]
  (->> command
       (re-seq #"elin#[^<]+")
       (first)))

(defn- repeat-char
  [n c]
  (apply str (repeat n c)))

(defn- generate-help-contents
  []
  (let [parsed-lines (->> (get-mapping-lines)
                          (map parse-mapping-line))
        max-mapping-key-length (apply max (map (comp count :mapping-key) parsed-lines))
        max-handler-length (apply max (map (comp count :handler) parsed-lines))
        format-str (format "%%-6s %%-%ds %%s"
                           max-mapping-key-length)]
    (->> parsed-lines
         (mapcat (fn [{:keys [map-type mapping-key command handler interceptor]}]
                   (concat
                    [[(format-map-type map-type)
                      mapping-key
                      (or handler
                          (format-command command))]]
                    (map #(vector "" "" (str "Uses: " %)) interceptor))))
         ;; (map (juxt :map-type :mapping-key :handler))
         (map #(apply format format-str %))
         (cons (->> [(repeat-char 6 "-")
                     (repeat-char max-mapping-key-length "-")
                     (repeat-char max-handler-length "-")]
                    (str/join " ")))
         (cons (format format-str "{mode}" "{lhs}" "{rhs}"))
         (str/join "\n"))))

(defn -main
  [& _]
  (->> ["------------------------------------------------------------------------------"
        "DEFAULT KEYS                                 *elin-configuration-default-keys*"
        ""
        (generate-help-contents)
        ""
        "=============================================================================="
        "vim:tw=78:ts=8:ft=help:norl:noet:fen:fdl=0:"]
       (str/join "\n")
       (spit help-file))
  (println "Generated help files."))

(comment
  (with-redefs [spit (fn [_ content]
                       (println content))]
    (-main)))
