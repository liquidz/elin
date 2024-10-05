(ns elin.util.string
  (:require
   [clojure.string :as str]))

(defn- upper-char?
  [c]
  (<= 65 (int c) 90))

(defn starts-with-upper?
  [s]
  (upper-char? (first s)))

(defn java-class-name?
  [s]
  (-> (str/split s #"[\.\$]")
      (last)
      (starts-with-upper?)))

(defn render [s m]
  (reduce-kv
   (fn [accm k v]
     (str/replace accm (str "{{" (subs (str k) 1) "}}") (str v)))
   s m))
