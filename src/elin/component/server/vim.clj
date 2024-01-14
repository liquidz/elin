(ns elin.component.server.vim
  "https://vim-jp.org/vimdoc-en/channel.html#channel-use"
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [elin.log :as e.log]
   [elin.protocol.rpc :as e.p.rpc]
   [msgpack.clojure-extensions])
  (:import
   java.io.EOFException))

(defrecord VimRequestMap
  [host request output-stream]
  e.p.rpc/IRpc
  (request? [_]
    (not= 0 (first request)))

  (parse-request [_]
    (if (= 0 (first request))
      ;; notify
      (let [[_ [method params callback]] request]
        {:method (keyword method)
         :async? (some? callback)
         :params params
         :callback callback})
      ;; request
      (let [[id [method params]] request]
        {:id id
         :method (keyword method)
         :async? false
         :params params})))

  (request! [_ content]
    (json/generate-stream content (io/writer output-stream)))

  (notify! [_ content]
    (json/generate-stream content (io/writer output-stream)))

  (response! [this error result]
    (when-let [id (:id (e.p.rpc/parse-request this))]
      (-> [id (or error result)]
          (json/generate-stream  (io/writer output-stream)))))

  e.p.rpc/IHost
  (call-function [this method params]
    (e.p.rpc/request! this ["call" method params])))

(defn start-server
  [{:keys [host server-socket handler]}]
  (loop []
    (try
      (with-open [client-sock (.accept server-socket)]
        (let [output-stream (.getOutputStream client-sock)
              input-stream (io/reader (.getInputStream client-sock))]
          (loop []
            (let [req (json/parse-stream input-stream)
                  req-map (map->VimRequestMap {:host host
                                               :request req
                                               :output-stream output-stream})
                  _ (e.log/info "received" (pr-str req))
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
