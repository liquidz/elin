(ns elin.component.server.http
  (:require
   [cheshire.core :as json]
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [com.stuartsierra.component :as component]
   [elin.constant.server :as e.c.server]
   [elin.function.vim :as e.f.vim]
   [elin.protocol.rpc :as e.p.rpc]
   [org.httpkit.server :as h.server])
  (:import
   (java.net
    ServerSocket
    URLDecoder)))

(defn- valid-request?
  [{:keys [request-method headers]}]
  (and (= :post request-method)
       (= "application/json" (get headers "content-type"))))

(defn- get-empty-port
  []
  (with-open [sock (ServerSocket. 0)]
    (.getLocalPort sock)))

(defprotocol IHttpHandler
  (new-message [this request params])
  (handle [this request]))

(defrecord ApiMessage
  [host message method params]
  e.p.rpc/IMessage
  (request? [_] true)
  (response? [_] false)
  (parse-message [_]
    {:id -1
     :method method
     :params params}))

(defn- ok
  [resp]
  {:body resp})

(defn- bad-request
  [& [m]]
  (merge {:status 400 :body "Bad request"}
         m))

(defn- not-found
  [& [m]]
  (merge {:status 404 :body "Not found"}
         m))

(defrecord HttpServer
  [lazy-writer handler host port stop-server]
  component/Lifecycle
  (start [this]
    (let [port' (get-empty-port)]
      (async/go
        (e.f.vim/set-variable! lazy-writer
                               e.c.server/http-server-port-variable
                               port'))
      (assoc this
             :port port'
             :stop-server (h.server/run-server
                           #(handle this %)
                           {:port port'}))))
  (stop [this]
    (stop-server)
    (dissoc this :stop-server :port))

  IHttpHandler
  (new-message [_ method params]
    (map->ApiMessage {:host host
                      :message []
                      :method (keyword method)
                      :params (or params [])}))

  (handle [this {:as req :keys [uri body]}]
    (let [uri (URLDecoder/decode uri)]
      (condp = uri
        "/api/v1"
        (if-not (valid-request? req)
          (not-found)
          (let [handler' (:handler handler)
                {:keys [method params]} (json/parse-stream (io/reader body) keyword)]
            (if (not method)
              (bad-request)
              (-> (new-message this
                               (keyword method)
                               (or params []))
                  (handler')
                  (json/generate-string)
                  (ok)))))

        (not-found)))))

(defn new-http-server
  [config]
  (-> (or (:http-server config) {})
      (merge {:host (get-in config [:server :host])})
      (map->HttpServer)))
