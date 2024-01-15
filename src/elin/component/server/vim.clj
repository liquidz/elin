(ns elin.component.server.vim
  "https://vim-jp.org/vimdoc-en/channel.html#channel-use"
  (:require
   [cheshire.core :as json]
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [elin.log :as e.log]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.util.id :as e.u.id]
   [msgpack.clojure-extensions])
  (:import
   java.io.EOFException))

(defrecord VimRequestMap
  [host request output-stream response-manager]
  e.p.rpc/IRpc
  (request? [_]
    (and (sequential? request)
         (int? (first request))
         (not= 0 (first request))
         (not (contains? @response-manager (first request)))))

  (response? [_]
    (and (sequential? request)
         (= 2 (count request))
         (int? (first request))
         (contains? @response-manager (first request))))

  (parse-request [this]
    (cond
      (e.p.rpc/response? this)
      (let [[id result] request]
        {:id id
         :result result})

      (e.p.rpc/request? this)
      (let [[id [method params]] request]
        {:id id
         :method (keyword method)
         :async? false
         :params params})

      ;; notify
      :else
      (let [[_ [method params callback]] request]
        {:method (keyword method)
         :async? (some? callback)
         :params params
         :callback callback})))

  (request! [_ [method :as content]]
    (let [id (cond
               (= "call" method) (nth content 3)
               (= "expr" method) (nth content 2))
          maybe-ch (when id (async/chan))]
      (when (and id maybe-ch)
        (swap! response-manager assoc id maybe-ch))
      (json/generate-stream content (io/writer output-stream))
      maybe-ch))

  (notify! [_ content]
    (json/generate-stream content (io/writer output-stream)))

  (response! [this error result]
    (when-let [id (:id (e.p.rpc/parse-request this))]
      (-> [id (or error result)]
          (json/generate-stream  (io/writer output-stream)))))

  e.p.rpc/IHost
  (call-function [this method params]
    (e.p.rpc/request! this ["call" method params (e.u.id/next-id)])))

(defn start-server
  [{:keys [host server-socket handler]}]
  (let [response-manager (atom {})]
    (loop []
      (try
        (with-open [client-sock (.accept server-socket)]
          (let [output-stream (.getOutputStream client-sock)
                input-stream (io/reader (.getInputStream client-sock))]
            (loop []
              (let [req (json/parse-stream input-stream)
                    req-map (map->VimRequestMap {:host host
                                                 :request req
                                                 :output-stream output-stream
                                                 :response-manager response-manager})
                    _ (e.log/info "received" (pr-str req))]
                (if (e.p.rpc/response? req-map)
                  (let [{:keys [id result]} (e.p.rpc/parse-request req-map)]
                    (when-let [ch (get @response-manager id)]
                      (swap! response-manager dissoc id)
                      (async/go (async/>! ch result))))
                  (let [[res err] (when-not (e.p.rpc/response? req-map)
                                    (try
                                      (when (sequential? req)
                                        [(handler req-map)])
                                      (catch Exception ex
                                        [nil (ex-message ex)])))]

                    (when (e.p.rpc/request? req-map)
                      (e.p.rpc/response! req-map err res)
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
