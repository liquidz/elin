(ns elin.component.session-storage
  (:require
   [com.stuartsierra.component :as component]
   [elin.protocol.storage :as e.p.storage]))

(defn- now
  []
  (int (/ (System/currentTimeMillis) 1000)))

(def ^:private default-limit
  "1 week"
  (* 60 60 24 7))

(defrecord SessionStorage
  [memory]
  component/Lifecycle
  (start [this]
    (assoc this :memory (atom {})))
  (stop [this]
    (dissoc this :memory))

  e.p.storage/IStorage
  (set [this k v]
    (e.p.storage/set this k v default-limit))
  (set [_ k v expire-seconds]
    (let [expires (+ (now) expire-seconds)]
      (swap! memory assoc k {:value v :expires expires})
      v))

  (get [this k]
    (let [{:keys [value expires]} (get @memory k)]
      (when (and value expires)
        (if (< (now) expires)
          value
          (do (e.p.storage/delete this k)
              nil)))))

  (contains? [this k]
    (boolean
     (when-let [expires (get-in @memory [k :expires])]
       (if (< (now) expires)
         true
         (do (e.p.storage/delete this k)
             false)))))

  (delete [_ k]
    (swap! memory dissoc k)
    nil)

  (clear [_]
    (reset! memory {})))

(defn new-session-storage
  [_]
  (map->SessionStorage {}))
