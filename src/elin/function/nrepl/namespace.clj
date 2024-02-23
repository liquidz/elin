(ns elin.function.nrepl.namespace
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [elin.util.file :as e.u.file]
   [elin.util.sexp :as e.u.sexp]
   [malli.core :as m]))

(m/=> get-cycled-namespace-path [:=> [:cat [:map
                                            [:ns string?]
                                            [:path string?]
                                            [:file-separator string?]]]
                                 [:maybe string?]])
(defn get-cycled-namespace-path
  [{ns-str :ns ns-path :path file-separator :file-separator}]
  (let [ext (e.u.file/get-file-extension ns-path)
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

(defn guess-namespace-from-path
  [path]
  (let [sep (e.u.file/guess-file-separator path)
        recent-file (-> (io/file path)
                        (e.u.file/find-clojure-file-in-parent-directories))
        recent-file-path (.getAbsolutePath recent-file)
        ext (e.u.file/get-file-extension recent-file-path)
        recent-namespace (-> (slurp recent-file)
                             (e.u.sexp/extract-ns-form)
                             (e.u.sexp/extract-namespace))
        recent-relative-name (-> recent-namespace
                                 (str/replace "." sep)
                                 (str/replace "-" "_"))
        base-dir (when-let [idx (str/index-of recent-file-path
                                              (str recent-relative-name ext))]
                   (subs recent-file-path 0 idx))]
    (when (str/starts-with? path base-dir)
      (-> (subs path (count base-dir))
          (str/replace #"\.\w+$" "")
          (str/replace sep ".")
          (str/replace "_" "-")))))
