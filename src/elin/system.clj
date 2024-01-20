(ns elin.system
  (:require
   [com.stuartsierra.component :as component]
   [elin.component.handler :as e.c.handler]
   [elin.component.interceptor :as e.c.interceptor]
   [elin.component.nrepl :as e.c.nrepl]
   [elin.component.server :as e.c.server]
   [msgpack.clojure-extensions]))

(defn new-system
  ([]
   (new-system {:server {:port 0}}))
  ([config]
   (component/system-map
    :nrepl (e.c.nrepl/new-nrepl config)
    :interceptor (e.c.interceptor/new-interceptor config)
    :handler (component/using
              (e.c.handler/new-handler config)
              [:nrepl :interceptor])
    :server (component/using
             (e.c.server/new-server config)
             [:handler]))))
