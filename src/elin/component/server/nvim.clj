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
  e.p.rpc/IRpc
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
           :async? false
           :params params})
      ;; response
      1 (let [[_ id error result] message]
          {:id id
           :error error
           :result result})
      ;; notify
      2 (let [[_ method [params callback]] message]
          {:method (keyword method)
           :async? (some? callback)
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

  e.p.rpc/IHost
  (call-function [this method params]
    (e.p.rpc/request! this ["nvim_call_function" [method params]]))

  (echo-text [this text]
    (e.p.rpc/notify! this ["nvim_echo" [[text "Normal"]] false {}]))

  (echo-message [this text]
    (e.p.rpc/echo-message this text "Normal"))
  (echo-message [this text highlight]
    (e.p.rpc/notify! this ["nvim_echo" [[[text highlight]] true {}]])))

(defn start-server
  [{:keys [host server-socket handler]}]
  (let [response-manager (atom {})]
    (loop []
      (try
        (with-open [client-sock (.accept server-socket)]
          (let [output-stream (.getOutputStream client-sock)
                data-input-stream (DataInputStream. (.getInputStream client-sock))]
            (loop []
              (let [raw-msg (msg/unpack-stream data-input-stream)
                    _ (e.log/info "received" (pr-str raw-msg))
                    msg (map->NvimMessage {:host host
                                           :message raw-msg
                                           :output-stream output-stream
                                           :response-manager response-manager})]
                (if (e.p.rpc/response? msg)
                  (let [{:keys [id error result]} (e.p.rpc/parse-message msg)]
                    (if error
                      ;; FIXME
                      nil
                      (when-let [ch (get @response-manager id)]
                        (swap! response-manager dissoc id)
                        (async/go (async/>! ch result))))
                    (swap! response-manager dissoc id))
                  (let [[res err] (try
                                    (when (sequential? raw-msg)
                                      [(handler msg)])
                                    (catch Exception ex
                                      [nil (ex-message ex)]))]
                    (when (e.p.rpc/request? msg)
                      (e.p.rpc/response! msg err res)
                      (.flush output-stream)))))
              (when-not (.isClosed client-sock)
                (recur))))
          (e.log/info "FIXME client-sock closed..."))
        (catch EOFException _
          nil)
        (catch Exception ex
          (println "closed client connection: " (ex-message ex))))
      (when-not (.isClosed server-socket)
        (recur)))))
