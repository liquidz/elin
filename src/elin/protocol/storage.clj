(ns elin.protocol.storage
  (:refer-clojure :exclude [contains? get set]))

(defprotocol IStorage
  (set [this k v] [this k v expire-seconds])
  (get [this k])
  (contains? [this k])
  (delete [this k])
  (clear [this]))
