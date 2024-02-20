(ns elin.function.nrepl.namespace
  (:require
   [clojure.string :as str]
   [malli.core :as m]))

(defn- get-file-extension
  [path]
  (when-let [idx (str/last-index-of path ".")]
    (subs path idx)))

(m/=> get-cycled-namespace-path [:=> [:cat [:map
                                            [:ns string?]
                                            [:path string?]
                                            [:file-separator string?]]]
                                 [:maybe string?]])
(defn get-cycled-namespace-path
  [{ns-str :ns ns-path :path file-separator :file-separator}]
  (let [ext (get-file-extension ns-path)
        relative-name (-> ns-str
                          (str/replace "." file-separator)
                          (str/replace "-" "_"))
        test? (str/ends-with? relative-name "_test")
        base-dir (when-let [idx (str/index-of ns-path (str relative-name ext))]
                   (subs ns-path 0 idx))]
    (if test?
      (some-> base-dir
              (str/reverse)
              (str/replace-first "/tset/" "/crs/")
              (str/reverse)
              (str (str/replace-first relative-name #"_test$" "")
                   ext))
      (some-> base-dir
              (str/reverse)
              (str/replace-first "/crs/" "/tset/")
              (str/reverse)
              (str relative-name "_test" ext)))))
