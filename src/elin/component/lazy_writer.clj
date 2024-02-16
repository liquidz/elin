(ns elin.component.lazy-writer
  (:require
   [clojure.core.async :as async]
   [com.stuartsierra.component :as component]
   [elin.log :as e.log]
   [elin.protocol.rpc :as e.p.rpc]))

(defrecord LazyWriter
  [writer-store writer-channel]
  component/Lifecycle
  (start [this]
    (e.log/debug "Writer component: Started")
    (let [ch (async/chan)]
      (async/go-loop []
        (if-let [writer @writer-store]
          (let [[type & args] (async/<! ch)]
            (case type
              ::request! (let [[ch & args] args
                               res (async/<! (apply e.p.rpc/request! writer args))]
                           (async/put! ch res))
              ::notify! (apply e.p.rpc/notify! writer args)
              ::response! (apply e.p.rpc/response! writer args)
              ::flush! (e.p.rpc/flush! writer)
              ::call-function (let [[ch & args] args
                                    res (async/<! (apply e.p.rpc/call-function writer args))]
                                (async/put! ch res))
              ::notify-function (apply e.p.rpc/notify-function writer args)
              ::echo-text (apply e.p.rpc/echo-text writer args)
              ::echo-message (apply e.p.rpc/echo-message writer args)
              nil)
            (when type
              (recur)))
          (do
            (async/<! (async/timeout 100))
            (recur))))

      (assoc this :writer-channel ch)))
  (stop [this]
    (reset! writer-store nil)
    (async/close! writer-channel)
    (e.log/debug "Writer component: Stopped")
    (dissoc this :writer-channel))

  e.p.rpc/ILazyWriter
  (set-writer! [_ writer]
    (reset! writer-store writer))

  e.p.rpc/IHost
  (request! [_ content]
    (if-let [writer @writer-store]
      (e.p.rpc/request! writer content)
      (let [ch (async/promise-chan)]
        (async/put! writer-channel [::request! ch content])
        ch)))
  (notify! [_ content]
    (if-let [writer @writer-store]
      (e.p.rpc/notify! writer content)
      (async/put! writer-channel [::notify! content])))
  (response! [_ id error result]
    (if-let [writer @writer-store]
      (e.p.rpc/response! writer id error result)
      (async/put! writer-channel [::response! id error result])))
  (flush! [_]
    (if-let [writer @writer-store]
      (e.p.rpc/flush! writer)
      (async/put! writer-channel [::flush!])))

  e.p.rpc/IFunction
  (call-function [_ method params]
    (if-let [writer @writer-store]
      (e.p.rpc/call-function writer method params)
      (let [ch (async/promise-chan)]
        (async/put! writer-channel [::call-function ch method params])
        ch)))
  (notify-function [_ method params]
    (if-let [writer @writer-store]
      (e.p.rpc/notify-function writer method params)
      (async/put! writer-channel [::notify-function method params])))
  (echo-text [_ text]
    (if-let [writer @writer-store]
      (e.p.rpc/echo-text writer text)
      (async/put! writer-channel [::echo-text text])))
  (echo-message [_ text]
    (if-let [writer @writer-store]
      (e.p.rpc/echo-message writer text)
      (async/put! writer-channel [::echo-message text])))
  (echo-message [_ text highlight]
    (if-let [writer @writer-store]
      (e.p.rpc/echo-message writer text highlight)
      (async/put! writer-channel [::echo-message text highlight]))))

(defn new-lazy-writer
  [_]
  (map->LazyWriter {:writer-store (atom nil)}))
