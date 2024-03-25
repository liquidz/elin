(ns elin.component.server.impl.sign
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [elin.component.server.impl.function :as e.c.s.function]
   [elin.component.server.nvim]
   [elin.component.server.vim]
   [elin.protocol.host :as e.p.host]))

(def ^:private prefix "elin_")
(def ^:private default-group "default")

(defn- exists?
  [file]
  (.exists (io/file file)))

(defn- place*
  [host {:keys [name lnum file group]}]
  (when (and name lnum file (exists? file))
    (let [group' (or group default-group)
          name' (str prefix name)]
      (e.c.s.function/notify host "elin#internal#sign#place" [name' lnum file group']))))

(defn- unplace-by*
  ([host]
   (unplace-by* host {}))
  ([host {:keys [group file name id]}]
   (let [group' (or group default-group)
         file' (if (and (seq file)
                        (exists? file))
                 file
                 "")
         name' (some->> name
                        (str prefix))
         options {:group group'
                  :file file'
                  :name name'
                  :id id}]
     (e.c.s.function/notify host "elin#internal#sign#unplace_by" [options]))))

(defn- list-in-buffer!!*
  ([host]
   (->> (e.c.s.function/request!! host "elin#internal#sign#list_in_buffer" [])
        (filter #(str/starts-with? (get % "name") prefix))))
  ([host target-buffer]
   (->> (e.c.s.function/request!! host "elin#internal#sign#list_in_buffer" [target-buffer])
        (filter #(str/starts-with? (get % "name") prefix)))))

(defn- list-all!!*
  [host]
  (e.c.s.function/request!! host "elin#internal#sign#list_all" []))

(defn- refresh*
  ([host]
   (e.c.s.function/notify host "elin#internal#sign#refresh" []))
  ([host signs]
   (e.c.s.function/notify host "elin#internal#sign#refresh" [{:signs signs}])))

(extend-protocol e.p.host/ISign
  elin.component.server.vim.VimHost
  (place-sign [this m] (place* this m))
  (unplace-signs-by [this m] (unplace-by* this m))
  (list-current-signs!! [this] (list-in-buffer!!* this))
  (list-all-signs!! [this] (list-all!!* this))
  (refresh-signs [this] (refresh* this))

  elin.component.server.nvim.NvimHost
  (place-sign [this m] (place* this m))
  (unplace-signs-by [this m] (unplace-by* this m))
  (list-current-signs!! [this] (list-in-buffer!!* this))
  (list-all-signs!! [this] (list-all!!* this))
  (refresh-signs [this] (refresh* this)))
