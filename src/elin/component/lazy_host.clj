(ns elin.component.lazy-host
  (:require
   [clojure.core.async :as async]
   [com.stuartsierra.component :as component]
   [elin.protocol.rpc :as e.p.rpc]))

(defrecord LazyHost
  [host-store host-channel]
  component/Lifecycle
  (start [this]
    (let [ch (async/chan)]
      (async/go-loop []
        (if-let [host @host-store]
          (let [[type & args] (async/<! ch)]
            (case type
              ::request! (let [[ch & args] args
                               res (async/<! (apply e.p.rpc/request! host args))]
                           (async/put! ch res))
              ::notify! (apply e.p.rpc/notify! host args)
              ::response! (apply e.p.rpc/response! host args)
              ::flush! (e.p.rpc/flush! host)
              ::call-function (let [[ch & args] args
                                    res (async/<! (apply e.p.rpc/call-function host args))]
                                (async/put! ch res))
              ::notify-function (apply e.p.rpc/notify-function host args)
              ::echo-text (apply e.p.rpc/echo-text host args)
              ::echo-message (apply e.p.rpc/echo-message host args)
              nil)
            (when type
              (recur)))
          (do
            (async/<! (async/timeout 100))
            (recur))))

      (assoc this :host-channel ch)))
  (stop [this]
    (reset! host-store nil)
    (async/close! host-channel)
    (dissoc this :host-channel))

  e.p.rpc/ILazyHost
  (set-host! [_ host]
    (reset! host-store host))

  e.p.rpc/IHost
  (request! [_ content]
    (if-let [host @host-store]
      (e.p.rpc/request! host content)
      (let [ch (async/promise-chan)]
        (async/put! host-channel [::request! ch content])
        ch)))
  (notify! [_ content]
    (if-let [host @host-store]
      (e.p.rpc/notify! host content)
      (async/put! host-channel [::notify! content])))
  (response! [_ id error result]
    (if-let [host @host-store]
      (e.p.rpc/response! host id error result)
      (async/put! host-channel [::response! id error result])))
  (flush! [_]
    (if-let [host @host-store]
      (e.p.rpc/flush! host)
      (async/put! host-channel [::flush!])))

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
      (async/put! host-channel [::notify-function method params])))
  (echo-text [_ text]
    (if-let [host @host-store]
      (e.p.rpc/echo-text host text)
      (async/put! host-channel [::echo-text text])))
  (echo-message [_ text]
    (if-let [host @host-store]
      (e.p.rpc/echo-message host text)
      (async/put! host-channel [::echo-message text])))
  (echo-message [_ text highlight]
    (if-let [host @host-store]
      (e.p.rpc/echo-message host text highlight)
      (async/put! host-channel [::echo-message text highlight]))))

(defn new-lazy-host
  [_]
  (map->LazyHost {:host-store (atom nil)}))
