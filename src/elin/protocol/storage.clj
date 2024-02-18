(ns elin.protocol.storage
  (:refer-clojure :exclude [set get contains?]))

(defprotocol IStorage
  (set [this k v] [this k v expire-seconds])
  (get [this k])
  (contains? [this k])
  (delete [this k])
  (clear [this]))
