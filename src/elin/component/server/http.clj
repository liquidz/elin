(ns elin.component.server.http
  (:require
   [com.stuartsierra.component :as component]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.util.http :as e.u.http]
   [org.httpkit.server :as h.server])
  (:import
   (java.net URLDecoder)))

(defprotocol IHttpHandler
  (handle [this request]))

(defrecord HttpServer
  [handler server-host port
   ;; Parameters set at start
   stop-server
   context
   routes]
  component/Lifecycle
  (start [this]
    (let [context {;; Other components
                   :component/nrepl (:nrepl handler)
                   :component/interceptor (:interceptor handler)
                   :component/host (:lazy-host handler)
                   :component/handler handler
                   :component/session-storage (:session-storage handler)
                   :component/clj-kondo (:clj-kondo handler)
                   ;; This component parameters
                   :server-host server-host
                   :port port}
          routes (:routes (e.p.interceptor/execute
                            (:interceptor handler)
                            e.c.interceptor/http-route
                            (assoc context :routes {})
                            identity))
          this' (assoc this
                       :context context
                       :routes routes)]
      (assoc this' :stop-server (h.server/run-server
                                  #(handle this' %)
                                  {:port port}))))
  (stop [this]
    (stop-server)
    (dissoc this :stop-server :context))

  IHttpHandler
  (handle [_ request]
    (let [context' (assoc context
                          :routes routes
                          :request request)]
      (:response
        (e.p.interceptor/execute
          (:interceptor handler)
          e.c.interceptor/http-request
          context'
          (fn [{:as ctx :keys [routes request]}]
            (let [uri (URLDecoder/decode (:uri request))
                  route-fn (get routes uri)]
              (assoc ctx :response
                (if (and route-fn
                         (fn? route-fn))
                  (route-fn ctx)
                  (e.u.http/not-found))))))))))

(defn new-http-server
  [config]
  (-> (or (:http-server config) {})
      (merge {:server-host (get-in config [:server :host])})
      (map->HttpServer)))
