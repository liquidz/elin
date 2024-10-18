(ns elin.function.callback
  (:require
   [clojure.core.async :as async]
   [elin.protocol.storage :as e.p.storage]
   [elin.schema :as e.schema]
   [elin.schema.handler :as e.s.handler]
   [malli.core :as m]))

(m/=> register [:=> [:cat e.s.handler/?Elin] [:cat string? e.schema/?ManyToManyChannel]])
(defn register
  [{:component/keys [session-storage]}]
  (let [ch (async/promise-chan)
        id (str "elin.function.callback:" (random-uuid))]
    (e.p.storage/set session-storage id ch)
    [id ch]))

(m/=> callback [:=> [:cat e.s.handler/?Elin string? any?] :nil])
(defn callback
  [{:component/keys [session-storage]} id result]
  (when-let [ch (e.p.storage/get session-storage id)]
    (e.p.storage/delete session-storage id)
    (if result
      (async/put! ch result)
      (async/close! ch)))
  nil)
