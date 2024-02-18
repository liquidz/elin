(ns elin.function.vim.sign
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [elin.function.vim :as e.f.vim]))

(def ^:private prefix "elin_")
(def ^:private default-group "default")

(defn- exists?
  [file]
  (.exists (io/file file)))

(defn place
  [host {:keys [name lnum file group]}]
  (when (and name lnum file (exists? file))
    (let [group' (or group default-group)
          name' (str prefix name)]
      (e.f.vim/notify host "elin#internal#sign#place" [name' lnum file group']))))

(defn unplace-by
  ([host]
   (unplace-by host {}))
  ([host {:keys [group file name id]}]
   (let [group' (or group default-group)
         file' (if (and (seq file)
                        (exists? file))
                 file
                 "")
         name' (str prefix name)
         options {:group group'
                  :file file'
                  :name name'
                  :id id}]
     (e.f.vim/notify host "elin#internal#sign#unplace_by" [options]))))

(defn list-in-buffer!!
  ([host]
   (->> (e.f.vim/call!! host "elin#internal#sign#list_in_buffer" [])
        (filter #(str/starts-with? (:name %) prefix))))
  ([host target-buffer]
   (->> (e.f.vim/call!! host "elin#internal#sign#list_in_buffer" [target-buffer])
        (filter #(str/starts-with? (:name %) prefix)))))

(defn list-all!!
  [host]
  (e.f.vim/call!! host "elin#internal#sign#list_all" []))
