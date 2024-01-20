(ns elin.system
  (:require
   [com.stuartsierra.component :as component]
   [elin.component.handler :as e.c.handler]
   [elin.component.nrepl :as e.c.nrepl]
   [elin.component.server :as e.c.server]
   [msgpack.clojure-extensions]))

(defn new-system
  ([]
   (new-system {:server {:port 0}}))
  ([config]
   (component/system-map
    :nrepl (e.c.nrepl/map->Nrepl {})
    :handler (component/using
              (e.c.handler/map->Handler {})
              [:nrepl])
    :server (component/using
             (e.c.server/map->Server (or (:server config) {}))
             [:handler]))))
