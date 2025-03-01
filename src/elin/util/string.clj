(ns elin.util.string
  (:require
   [clojure.string :as str]
   [malli.core :as m]))

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

(m/=> trim-indent [:function
                   [:=> [:cat integer? string?] string?]
                   [:=> [:cat integer? string? integer?] string?]])
(defn trim-indent
  "Trim indentation of a string."
  ([n s]
   (trim-indent n s 0))
  ([n s skip-line-count]
   (let [[skip-lines target-lines] (split-at skip-line-count (str/split-lines s))]
     (->> (concat skip-lines
                  (map #(subs % n) target-lines))
          (str/join "\n")))))
