(ns elin.interceptor.http
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.protocol.host.rpc :as e.p.h.rpc]
   [elin.util.http :as e.u.http]))

(defrecord ApiMessage
  [host message method params]
  e.p.h.rpc/IRpcMessage
  (request? [_] true)
  (response? [_] false)
  (parse-message [_]
    {:id -1
     :method method
     :params params}))

(defn- api-request?
  [{:keys [request-method headers]}]
  (and (= :post request-method)
       (= "application/json" (get headers "content-type"))))

(defn- new-message
  [server-host method params]
  (map->ApiMessage {:host server-host
                    :message []
                    :method (keyword method)
                    :params (or params [])}))

(defn- handle-api
  [{:component/keys [handler] :keys [server-host request]}]
  (if-not (api-request? request)
    (e.u.http/bad-request)
    (let [handler' (:handler handler)
          {:keys [body]} request
          {:keys [method params]} (json/parse-stream (io/reader body) keyword)]
      (if (not method)
        (e.u.http/bad-request)
        (-> (new-message server-host
                         (keyword method)
                         (or params []))
            (handler')
            (e.u.http/json))))))

(def api-route
  "Add http route as `/api/v1` for API request.

  .e.g.
  [source,shell]
  ----
  curl -XPOST -H \"Content-Type: application/json\" -d '{\"method\": \"elin.handler.complete/complete\", \"params\": [\"ma\"]}' http://localhost:12345/api/v1
  ----"
  {:kind e.c.interceptor/http-route
   :enter #(assoc-in % [:routes "/api/v1" :post] handle-api)})
