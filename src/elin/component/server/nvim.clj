(ns elin.component.server.nvim
  "https://github.com/msgpack-rpc/msgpack-rpc/blob/master/spec.md"
  (:require
   [clojure.core.async :as async]
   [elin.log :as e.log]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.util.id :as e.u.id]
   [msgpack.clojure-extensions]
   [msgpack.core :as msg])
  (:import
   (java.io
    DataInputStream
    EOFException)))

(defrecord NvimMessage
  [host message output-stream response-manager]
  e.p.rpc/IMessage
  (request? [_]
    (= 0 (first message)))

  (response? [_]
    (= 1 (first message)))

  (parse-message [_]
    (condp = (first message)
      ;; request
      0 (let [[_ id method [params]] message]
          {:id id
           :method (keyword method)
           :params params})
      ;; response
      1 (let [[_ id error result] message]
          {:id id
           :error error
           :result result})
      ;; notify
      2 (let [[_ method [params callback]] message]
          {:method (keyword method)
           :params params
           :callback callback})
      {}))

  (request! [_ content]
    (let [id (e.u.id/next-id)
          ch (async/chan)]
      (swap! response-manager assoc id ch)
      (->> (concat [0 id] content)
           (msg/pack)
           (.write output-stream))
      ch))

  (notify! [_ content]
    (->> (concat [2] content)
         (msg/pack)
         (.write output-stream)))

  (response! [this error result]
    (when-let [id (:id (e.p.rpc/parse-message this))]
      (->> [1 id error result]
           (msg/pack)
           (.write output-stream))))

  e.p.rpc/IFunction
  (call-function [this method params]
    (e.p.rpc/request! this ["nvim_call_function" [method params]]))

  (echo-text [this text]
    (e.p.rpc/notify! this ["nvim_echo" [[text "Normal"]] false {}]))

  (echo-message [this text]
    (e.p.rpc/echo-message this text "Normal"))
  (echo-message [this text highlight]
    (e.p.rpc/notify! this ["nvim_echo" [[[text highlight]] true {}]])))

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
                  (e.log/debug "Neovim server received message:" (pr-str raw-msg))
                  (on-accept (map->NvimMessage {:host host
                                                :message raw-msg
                                                :output-stream output-stream
                                                :response-manager response-manager}))
                  (when-not (.isClosed client-sock)
                    (recur))))))
          (e.log/debug "Client socket is closed"))
        (catch EOFException _
          nil)
        (catch Exception ex
          (e.log/debug "Client connection is closed" (ex-message ex))))
      (when-not (.isClosed server-socket)
        (recur)))))
