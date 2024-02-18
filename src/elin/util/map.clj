(ns elin.util.map
  (:require
   [clojure.string :as str]
   [malli.core :as m]))

(m/=> select-keys-by-namespace [:=> [:cat map? keyword?] map?])
(defn select-keys-by-namespace
  [m key-ns-kw]
  (reduce-kv (fn [accm k v]
               (if (= key-ns-kw (keyword (namespace k)))
                 (assoc accm k v)
                 accm))
             {} m))

(m/=> map->str [:=> [:cat map? [:sequential keyword?]] string?])
(defn map->str [m keyseq]
  (let [max-key-length (apply max (map #(count (name %)) keyseq))]
    (->> (select-keys m keyseq)
         (mapcat (fn [[k v]]
                   (let [lines (str/split-lines v)]
                     (cons (format (str "%" max-key-length "s: %s") (name k) (first lines))
                           (map #(str (apply str (repeat (+ 2 max-key-length) " "))
                                      %)
                                (rest lines))))))
         (str/join "\n"))))
