(ns elin.util.file
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [elin.constant.project :as e.c.project]
   [elin.schema :as e.schema]
   [elin.util.os :as e.u.os]
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

(m/=> get-cache-directory [:=> :cat string?])
(defn get-cache-directory
  []
  (let [home (System/getenv "HOME")
        xdg-cache-home (System/getenv "$XDG_CACHE_HOME")
        file (cond
               e.u.os/mac?
               (io/file home "Library" "Caches" e.c.project/name)

               (seq xdg-cache-home)
               (io/file xdg-cache-home e.c.project/name)

               :else
               (io/file home ".cache" e.c.project/name))]
    (.mkdirs file)
    (.getAbsolutePath file)))

(m/=> get-file-extension [:=> [:cat string?] [:maybe string?]])
(defn get-file-extension
  [path]
  (when-let [idx (str/last-index-of path ".")]
    (subs path idx)))

(m/=> guess-file-separator [:=> [:cat string?] string?])
(defn guess-file-separator
  [path]
  (if (= \/ (first path))
    "/"
    "\\"))
