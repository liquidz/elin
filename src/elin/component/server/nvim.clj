(ns elin.component.server.nvim
  "https://github.com/msgpack-rpc/msgpack-rpc/blob/master/spec.md"
  (:require
   [clojure.core.async :as async]
   [elin.protocol.host.rpc :as e.p.h.rpc]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.util.id :as e.u.id]
   [elin.util.server :as e.u.server]
   [msgpack.clojure-extensions]
   [msgpack.core :as msg]
   [taoensso.timbre :as timbre])
  (:import
   (java.io
    DataInputStream
    EOFException)))

(defrecord NvimMessage
  [host message response-manager]
  e.p.h.rpc/IRpcMessage
  (request? [_]
    (= 0 (first message)))

  (response? [_]
    (= 1 (first message)))

  (parse-message [_]
    (condp = (first message)
      ;; request
      0 (let [[_ id method [params options]] message]
          {:id id
           :method (keyword method)
           :params params
           :options (-> (e.u.server/unformat options)
                        ;; Do not allow 'callback' option for request
                        (dissoc :callback))})
      ;; response
      1 (let [[_ id error result] message]
          {:id id
           :error error
           :result result})
      ;; notify
      2 (let [[_ method [params options :as args]] message
              method' (keyword method)
              options' (e.u.server/unformat options)]
          (if (= :nvim_error_event method')
            {:method :elin.handler.internal/error
             :params args
             :options options'}
            {:method method'
             :params params
             :options options'}))
      {})))

(defrecord NvimHost
  [output-stream response-manager]
  e.p.h.rpc/IRpc
  (request! [_ content]
    (let [id (e.u.id/next-id)
          ch (async/promise-chan)]
      (swap! response-manager assoc id ch)
      (->> (concat [0 id] content)
           (msg/pack)
           (.write output-stream))
      ch))

  (notify! [_ content]
    (->> (concat [2] content)
         (msg/pack)
         (.write output-stream)))

  (response! [_ id error result]
    (when id
      (->> [1 id error result]
           (msg/pack)
           (.write output-stream))))

  (flush! [_]
    (.flush output-stream))

  e.p.rpc/IFunction
  (call-function [this method params]
    (e.p.h.rpc/request! this ["nvim_call_function" [method params]]))

  (notify-function [this method params]
    (e.p.h.rpc/notify! this ["nvim_call_function" [method params]]))

  (echo-text [this text]
    (e.p.h.rpc/notify! this ["nvim_echo" [[[text "Normal"]] false {}]]))
  (echo-text [this text highlight]
    (e.p.h.rpc/notify! this ["nvim_echo" [[[text highlight]] false {}]]))

  (echo-message [this text]
    (e.p.rpc/echo-message this text "Normal"))
  (echo-message [this text highlight]
    (e.p.h.rpc/notify! this ["nvim_echo" [[[text highlight]] true {}]])))

(defn start-server
  [{:keys [host server-socket on-accept stop-signal]}]
  (let [response-manager (atom {})]
    ;; Client accepting loop
    (loop []
      (try
        (with-open [client-sock (.accept server-socket)]
          (let [output-stream (.getOutputStream client-sock)
                data-input-stream (DataInputStream. (.getInputStream client-sock))]
            ;; Client message reading loop
            (loop []
              (let [[raw-msg ch] (async/alts!! [stop-signal
                                                (async/thread
                                                  (try
                                                    (msg/unpack-stream data-input-stream)
                                                    (catch Exception ex ex)))])]
                (when (and (not= stop-signal ch)
                           (not (instance? Exception raw-msg)))
                  ;; (e.log/debug "Neovim server received message:" (pr-str raw-msg))
                  (on-accept {:message (map->NvimMessage {:host host
                                                          :message raw-msg
                                                          :response-manager response-manager})
                              :host (map->NvimHost {:output-stream output-stream
                                                    :response-manager response-manager})})
                  (when-not (.isClosed client-sock)
                    (recur))))))
          (timbre/debug "Client socket is closed"))
        (catch EOFException _
          nil)
        (catch Exception ex
          (timbre/debug "Client connection is closed" (ex-message ex))))
      (when-not (.isClosed server-socket)
        (recur)))))
