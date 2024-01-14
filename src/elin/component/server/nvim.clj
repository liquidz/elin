(ns elin.component.server.nvim
  "https://github.com/msgpack-rpc/msgpack-rpc/blob/master/spec.md"
  (:require
   [elin.log :as e.log]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.util.id :as e.u.id]
   [msgpack.clojure-extensions]
   [msgpack.core :as msg])
  (:import
   (java.io
    DataInputStream
    EOFException)))

(defrecord NvimRequestMap
  [host request output-stream]
  e.p.rpc/IRpc
  (request? [_]
    (= 0 (first request)))

  (parse-request [_]
    (condp = (first request)
      ;; request
      0 (let [[_ id method [params]] request]
          {:id id
           :method (keyword method)
           :async? false
           :params params})
      ;; notify
      2 (let [[_ method [params callback]] request]
          {:method (keyword method)
           :async? (some? callback)
           :params params
           :callback callback})
      {}))

  (request! [_ content]
    (->> (concat [0 (e.u.id/next-id)] content)
         (msg/pack)
         (.write output-stream)))

  (notify! [_ content]
    (->> (concat [2] content)
         (msg/pack)
         (.write output-stream)))

  (response! [this error result]
    (when-let [id (:id (e.p.rpc/parse-request this))]
      (->> [1 id error result]
           (msg/pack)
           (.write output-stream))))

  e.p.rpc/IHost
  (call-function [this method params]
    (e.p.rpc/notify! this ["nvim_call_function" [method params]])))

(defn start-server
  [{:keys [host server-socket handler]}]
  (loop []
    (try
      (with-open [client-sock (.accept server-socket)]
        (let [output-stream (.getOutputStream client-sock)
              data-input-stream (DataInputStream. (.getInputStream client-sock))]
          (loop []
            (let [req (msg/unpack-stream data-input-stream)
                  _ (e.log/info "received" (pr-str req))
                  req-map (map->NvimRequestMap {:host host
                                                :request req
                                                :output-stream output-stream})
                  [res err] (try
                              (when (sequential? req)
                                [(handler req-map)])
                              (catch Exception ex
                                [nil (ex-message ex)]))]

              (when (e.p.rpc/request? req-map)
                (e.p.rpc/response! req-map err res)
                (.flush output-stream)))
            (when-not (.isClosed client-sock)
              (recur))))
        (e.log/info "FIXME client-sock closed..."))
      (catch EOFException _
        nil)
      (catch Exception ex
        (println "closed client connection: " (ex-message ex))))
    (when-not (.isClosed server-socket)
      (recur))))
