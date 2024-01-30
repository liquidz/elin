(ns elin.util.file
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [elin.schema :as e.schema]
   [malli.core :as m]))

(m/=> find-file-in-parent-directories
      [:function
       [:=> [:cat string?] [:maybe e.schema/?File]]
       [:=> [:cat string? string?] [:maybe e.schema/?File]]])
(defn find-file-in-parent-directories
  ([file-name]
   (find-file-in-parent-directories "." file-name))
  ([cwd
    file-name]
   (loop [dir (.getAbsoluteFile (io/file cwd))]
     (when dir
       (let [file (io/file dir file-name)]
         (if (.exists file)
           file
           (recur (.getParentFile dir))))))))

(m/=> normalize-path [:=> [:cat string?] string?])
(defn normalize-path [path]
  (let [path (str/replace-first path #"^file:" "")]
    (if (str/starts-with? path "jar:")
      (-> path
          (str/replace-first #"^jar:file:" "zipfile://")
          (str/replace #"!/" "::"))
      path)))
