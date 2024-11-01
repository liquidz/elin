(ns elin.task.doc.asciidoc
  (:require
   [clojure.string :as str]
   [elin.task.doc.common :as common]))

(defn- convert-title
  [title]
  (-> (str "_" title)
      (str/replace "/" "")
      (str/replace "-" "_")
      (str/replace "." "_")))

(defn anchor
  [title]
  (str "<<" (convert-title title) ">>"))

(defn source-link
  [meta-data]
  (when-let [link (common/github-link meta-data)]
    (when-let [idx (str/index-of  link "main/src")]
      (format "\n[.text-right]\n[.small]#link:%s[%s]#"
              link
              (subs link (inc (str/index-of link "/src/" idx)))))))
