(ns elin.task.check-documents
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def ^:private project-root-dir-file
  (-> (io/file *file*)
      (.getParentFile) ; dev/elin/task
      (.getParentFile) ; dev/elin
      (.getParentFile) ; dev)
      (.getParentFile)))

(def ^:private bb-edn
  (-> (io/file project-root-dir-file  "bb.edn")
      (slurp)
      (edn/read-string)))

(def ^:private command-deps
  (get-in bb-edn [:__elin_internal__ :command :deps]))

(defn- relative-path
  [file]
  (subs (.getAbsolutePath file)
        (inc (count (.getAbsolutePath project-root-dir-file)))))

(defn- extract-deps
  [line]
  (let [start (str/index-of line "'{:deps")
        end (str/index-of line "'" (inc start))]
    (edn/read-string (subs line (inc start) end))))

(defn- line-deps-errors
  [line-deps]
  (->> (:deps line-deps)
       (keep (fn [[lib actual]]
               (let [expected (get command-deps lib)]
                 (when (not= expected
                             actual)
                   (format "%s is outdated. expected: %s, actual: %s" lib expected actual)))))))

(defn- command-deps-errors-in-document
  [file]
  (->> (slurp file)
       (str/split-lines)
       (filter #(str/includes? % "'{:deps"))
       (map extract-deps)
       (mapcat line-deps-errors)
       (map #(format "%s: %s" (relative-path file) %))))

(defn- command-deps-errors-in-documents
  []
  (->> [(io/file "doc" "pages" "getting_started" "index.adoc")]
       (mapcat command-deps-errors-in-document)))

(defn -main
  [& _]
  (let [results (command-deps-errors-in-documents)]
    (if (seq results)
      (do (doseq [result results]
            (println result))
          (System/exit 1))
      (println "No outdated dependencies."))))
