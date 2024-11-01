(ns elin.task.doc.common
  (:require
   [clojure.string :as str]))

(def ^:private github-base-url
  "https://github.com/liquidz/elin/blob/main")

(defn find-first
  [pred coll]
  (some #(and (pred %) %) coll))

(defn github-link
  [{:keys [file line]}]
  (when-let [idx (str/index-of file "/liquidz/elin/src/")]
    (format "%s%s#L%d"
            github-base-url
            (subs file (str/index-of file "/src/" idx))
            line)))
