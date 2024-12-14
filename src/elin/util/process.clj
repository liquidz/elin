(ns elin.util.process
  (:require
   [babashka.process :as proc]
   [clojure.core.async :as async]
   [elin.error :as e]))

(def ^:private manager (atom {}))

(defn alive?
  [process-id]
  (contains? @manager process-id))

(defn start
  ([commands]
   (start (random-uuid) commands))
  ([process-id commands]
   (if (alive? process-id)
     (e/conflict)
     (let [process (apply proc/process commands)]
       (swap! manager assoc process-id process)
       (async/thread
         (deref process)
         (swap! manager dissoc process-id))
       process-id))))

(defn kill
  [process-id]
  (when-let [process (get @manager process-id)]
    (proc/destroy process)))

(defn- executable?*
  [command]
  (try
    (let [process (proc/process [command])]
      (proc/destroy process)
      true)
    (catch Exception _
      false)))
(def executable? (memoize executable?*))
