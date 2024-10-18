(ns elin.function.nrepl.namespace
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [elin.error :as e]
   [elin.schema :as e.schema]
   [elin.util.file :as e.u.file]
   [elin.util.sexpr :as e.u.sexpr]
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

(m/=> guess-namespace-from-path [:=> [:cat string?] (e.schema/error-or string?)])
(defn guess-namespace-from-path
  [path]
  (e/let [sep (e.u.file/guess-file-separator path)
          file (io/file path)
          find-regexp (if (.isDirectory file)
                        #"\.clj[csd]?$"
                        (let [filename (.getName file)
                              only-name (some->> (str/last-index-of filename ".")
                                                 (subs filename 0))]
                          (re-pattern (str "(?<!" only-name ")\\.clj[csd]?$"))))
          recent-file (e.u.file/find-file-in-parent-directories
                       (.getAbsolutePath (.getParentFile file))
                       find-regexp)
          recent-file-path (.getAbsolutePath recent-file)
          ext (e.u.file/get-file-extension recent-file-path)
          recent-namespace (e/-> (slurp recent-file)
                                 (e.u.sexpr/extract-ns-form)
                                 (e.u.sexpr/extract-namespace))
          recent-relative-name (-> recent-namespace
                                   (str/replace "." sep)
                                   (str/replace "-" "_"))
          base-dir (when-let [idx (str/index-of recent-file-path
                                                (str recent-relative-name ext))]
                     (subs recent-file-path 0 idx))
          _ (when-not (str/starts-with? path base-dir)
              (e/not-found))]
    (-> (subs path (count base-dir))
        (str/replace #"\.\w+$" "")
        (str/replace sep ".")
        (str/replace "_" "-"))))
