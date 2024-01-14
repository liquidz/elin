(ns elin.log
  (:require
    [clojure.string :as str]))

(def ^:dynamic *log-file* "/tmp/elin.log")

(defn log
  [& messages]
  (let [s (->> (map str messages)
               (str/join " "))]
    (println s)
    (spit *log-file* (str s "\n") :append true)))

(defn info
  [& messages]
  (let [s (->> (map str messages)
               (str/join " "))]
    (println s)
    (spit *log-file* (str s "\n") :append true)))
