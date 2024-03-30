(ns elin.function.file
  (:require
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [elin.protocol.host :as e.p.host]))

(defn open-as
  [{:component/keys [host]} path]
  (if (.exists (io/file path))
    (async/<!! (e.p.host/jump! host path 1 1))
    (let [path' (async/<!! (e.p.host/input! host "Open this file?: " path))]
      (when (seq path')
        (.mkdirs (.getParentFile (io/file path')))
        (async/<!! (e.p.host/jump! host path' 1 1))))))
