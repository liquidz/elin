(ns elin.component.writer-store
  (:require
   [com.stuartsierra.component :as component]
   [elin.log :as e.log]
   [elin.protocol.rpc :as e.p.rpc]))

(defrecord WriterStore
  [writer-store]
  component/Lifecycle
  (start [this]
    (e.log/debug "Writer component: Started")
    this)
  (stop [this]
    (reset! writer-store nil)
    (e.log/debug "Writer component: Stopped")
    this)

  e.p.rpc/IWriterStore
  (set-writer! [_ writer]
    (reset! writer-store writer))

  e.p.rpc/IWriter
  (request! [_ content]
    (when-let [writer @writer-store]
      (e.p.rpc/request! writer content)))
  (notify! [_ content]
    (when-let [writer @writer-store]
      (e.p.rpc/notify! writer content)))
  (response! [_ id error result]
    (when-let [writer @writer-store]
      (e.p.rpc/response! writer id error result)))
  (flush! [_]
    (when-let [writer @writer-store]
      (e.p.rpc/flush! writer))))

(defn new-writer-store
  [_]
  (map->WriterStore {:writer-store (atom nil)}))
