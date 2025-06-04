(ns elin.util.file
  (:refer-clojure :exclude [slurp])
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [elin.constant.project :as e.c.project]
   [elin.error :as e]
   [elin.schema :as e.schema]
   [elin.util.os :as e.u.os]
   [malli.core :as m])
  (:import
   (java.io FileInputStream FileNotFoundException)
   (java.util.zip ZipEntry ZipInputStream)))

(m/=> find-file-in-parent-directories-by-string
      [:=> [:cat string? string?] [:maybe e.schema/?File]])
(defn- find-file-in-parent-directories-by-string
  [cwd file-name]
  (loop [dir (.getAbsoluteFile (io/file cwd))]
    (when dir
      (let [file (io/file dir file-name)]
        (if (.exists file)
          file
          (recur (.getParentFile dir)))))))

(defn- find-file-in-parent-directories-by-pattern
  [cwd file-name-pattern]
  (loop [dir (io/file cwd)]
    (when dir
      (if-let [target-file (->> (file-seq dir)
                                (some #(and (re-seq file-name-pattern (.getName %))
                                            %)))]
        target-file
        (recur (.getParentFile dir))))))

(m/=> find-file-in-parent-directories
      [:=>
       [:cat string? [:or string? e.schema/?Pattern]]
       [:maybe e.schema/?File]])
(defn find-file-in-parent-directories
  [cwd file-name]
  (if (string? file-name)
    (find-file-in-parent-directories-by-string cwd file-name)
    (find-file-in-parent-directories-by-pattern cwd file-name)))

(m/=> normalize-path [:=> [:cat [:maybe string?]] [:maybe string?]])
(defn normalize-path [path]
  (when-let [path (some-> path
                          (str/replace-first #"^file:" ""))]
    (if (str/starts-with? path "jar:")
      (-> path
          (str/replace-first #"^jar:file:" "zipfile://")
          (str/replace #"!/" "::"))
      path)))

(m/=> get-cache-directory [:=> :cat string?])
(defn get-cache-directory
  []
  (let [home (System/getenv "HOME")
        xdg-cache-home (System/getenv "XDG_CACHE_HOME")
        file (cond
               e.u.os/mac?
               (io/file home "Library" "Caches" e.c.project/name)

               (seq xdg-cache-home)
               (io/file xdg-cache-home e.c.project/name)

               :else
               (io/file home ".cache" e.c.project/name))]
    (.mkdirs file)
    (.getAbsolutePath file)))

(m/=> get-config-directory [:-> string?])
(defn get-config-directory
  []
  (let [home (System/getenv "HOME")
        xdg-config-home (System/getenv "XDG_CONFIG_HOME")
        file (cond
               (seq xdg-config-home)
               (io/file xdg-config-home e.c.project/name)

               :else
               (io/file home ".config" e.c.project/name))]
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

(m/=> get-project-root-directory [:=> [:cat string?] [:maybe e.schema/?File]])
(defn get-project-root-directory
  [cwd]
  (some-> (find-file-in-parent-directories cwd ".git")
          (.getParentFile)))

(m/=> encode-path [:function
                   [:-> string? string?]
                   [:-> string? [:maybe int?] string?]
                   [:-> string? [:maybe int?] [:maybe int?] string?]])
(defn encode-path
  ([path]
   (encode-path path nil nil))
  ([path lnum]
   (encode-path path lnum nil))
  ([path lnum col]
   (str path
        (when lnum (str ":" lnum))
        (when col (str ":" col)))))

(m/=> decode-path [:-> string? [:map
                                [:path string?]
                                [:lnum int?]
                                [:col int?]]])
(defn decode-path
  [path]
  (if-let [[_ path' lnum col] (re-find #"^(.+?)(?::(\d+))(?::(\d+))?$" path)]
    {:path path'
     :lnum (or (some-> lnum parse-long)
               1)
     :col (or (some-> col parse-long)
              1)}
    {:path path :lnum 1 :col 1}))

(m/=> zipfile-path? [:=> [:cat string?] boolean?])
(defn zipfile-path?
  [path]
  (str/starts-with? path "zipfile:"))

(m/=> slurp-zipfile [:=> [:cat string?] (e.schema/error-or string?)])
(defn slurp-zipfile
  "Expected a string like 'zipfile:/path/to/jar.jar::path/to/entry.clj' as path."
  [path]
  (e/let [[_ jar-file entry-name] (first (re-seq #"^zipfile:(.+?)::(.+?)$" path))
          _ (or (seq jar-file) (e/incorrect))
          _ (or (seq entry-name) (e/incorrect))]
    (try
      (with-open [zis (ZipInputStream. (FileInputStream. jar-file))]
        (or (loop [entry (ZipInputStream/.getNextEntry zis)]
              (when entry
                (if (= entry-name (ZipEntry/.getName entry))
                  (clojure.core/slurp (io/reader zis))
                  (recur (ZipInputStream/.getNextEntry zis)))))
            (e/not-found)))
      (catch FileNotFoundException ex
        (e/not-found {:message (ex-message ex)} ex))
      (catch Exception ex
        (e/fault {:message (ex-message ex)} ex)))))

(m/=> slurp [:=> [:cat string?] (e.schema/error-or string?)])
(defn slurp
  [path]
  (if (zipfile-path? path)
    (slurp-zipfile path)
    (try
      (clojure.core/slurp path)
      (catch FileNotFoundException ex
        (e/not-found {:message (ex-message ex)} ex))
      (catch Exception ex
        (e/fault {:message (ex-message ex)} ex)))))
