(ns elin.util.file
  (:require
   [clojure.java.io :as io]
   [elin.util.schema :as e.u.schema]
   [malli.core :as m]))

(m/=> find-file-in-parent-directories
      [:function
       [:=> [:cat string?] [:maybe e.u.schema/?File]]
       [:=> [:cat string? string?] [:maybe e.u.schema/?File]]])
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
