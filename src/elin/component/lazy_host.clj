(ns elin.component.lazy-host
  (:require
   [clojure.core.async :as async]
   [com.stuartsierra.component :as component]
   [elin.error :as e]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.host.rpc :as e.p.h.rpc]
   [elin.protocol.lazy-host :as e.p.lazy-host]
   [elin.protocol.rpc :as e.p.rpc]
   [taoensso.timbre :as timbre]))

(defmacro ^:private execute [{:keys [host protocol method args queue]}]
  `(if-let [host# ~host]
     (if (satisfies? ~protocol host#)
       (apply ~method host# ~args)
       (e/unsupported))
     (async/put! ~queue [~method ~@args])))

(defrecord LazyHost
  [;; PARAMS
   host-store
   host-channel]
  component/Lifecycle
  (start [this]
    (let [ch (async/chan)]
      (async/go-loop []
        (if-let [host @host-store]
          (let [[type-or-fn & args] (async/<! ch)]
            (cond
              (= ::request! type-or-fn)
              (let [[ch & args] args
                    res (async/<! (apply e.p.h.rpc/request! host args))]
                (async/put! ch res))

              (some? type-or-fn)
              (apply type-or-fn this args))

            (when type-or-fn
              (recur)))
          (do
            (async/<! (async/timeout 100))
            (recur))))

      (timbre/info "LazyHost component: Started")
      (assoc this :host-channel ch)))
  (stop [this]
    (reset! host-store nil)
    (async/close! host-channel)
    (timbre/info "LazyHost component: Stopped")
    (dissoc this :host-channel))

  e.p.lazy-host/ILazyHost
  (set-host! [_ host]
    (reset! host-store host))

  e.p.h.rpc/IRpc
  (request! [_ content]
    (if-let [host @host-store]
      (e.p.h.rpc/request! host content)
      (let [ch (async/promise-chan)]
        (async/put! host-channel [::request! ch content])
        ch)))
  (notify! [_ content]
    (execute {:host @host-store
              :protocol e.p.h.rpc/IRpc
              :method e.p.h.rpc/notify!
              :args [content]
              :queue host-channel}))
  (response! [_ id error result]
    (execute {:host @host-store
              :protocol e.p.h.rpc/IRpc
              :method e.p.h.rpc/response!
              :args [id error result]
              :queue host-channel}))
  (flush! [_]
    (execute {:host @host-store
              :protocol e.p.h.rpc/IRpc
              :method e.p.h.rpc/flush!
              :args []
              :queue host-channel}))

  e.p.host/IEcho
  (echo-text [_ text]
    (execute {:host @host-store
              :protocol e.p.host/IEcho
              :method e.p.host/echo-text
              :args [text]
              :queue host-channel}))
  (echo-text [_ text highlight]
    (execute {:host @host-store
              :protocol e.p.host/IEcho
              :method e.p.host/echo-text
              :args [text highlight]
              :queue host-channel}))
  (echo-message [_ text]
    (execute {:host @host-store
              :protocol e.p.host/IEcho
              :method e.p.host/echo-message
              :args [text]
              :queue host-channel}))
  (echo-message [_ text highlight]
    (execute {:host @host-store
              :protocol e.p.host/IEcho
              :method e.p.host/echo-message
              :args [text highlight]
              :queue host-channel}))

  e.p.host/ISign
  (place-sign [_ m]
    (execute {:host @host-store
              :protocol e.p.host/ISign
              :method e.p.host/place-sign
              :args [m]
              :queue host-channel}))
  (unplace-signs-by [_ m]
    (execute {:host @host-store
              :protocol e.p.host/ISign
              :method e.p.host/unplace-signs-by
              :args [m]
              :queue host-channel}))
  (list-current-signs!! [_]
    (execute {:host @host-store
              :protocol e.p.host/ISign
              :method e.p.host/list-current-signs!!
              :args []
              :queue host-channel}))
  (list-all-signs!! [_]
    (execute {:host @host-store
              :protocol e.p.host/ISign
              :method e.p.host/list-all-signs!!
              :args []
              :queue host-channel}))
  (refresh-signs [_]
    (execute {:host @host-store
              :protocol e.p.host/ISign
              :method e.p.host/refresh-signs
              :args []
              :queue host-channel}))

  e.p.rpc/IFunction
  (call-function [_ method params]
    (if-let [host @host-store]
      (e.p.rpc/call-function host method params)
      (let [ch (async/promise-chan)]
        (async/put! host-channel [::call-function ch method params])
        ch)))
  (notify-function [_ method params]
    (if-let [host @host-store]
      (e.p.rpc/notify-function host method params)
      (async/put! host-channel [::notify-function method params]))))

(defn new-lazy-host
  [_]
  (map->LazyHost {:host-store (atom nil)}))
