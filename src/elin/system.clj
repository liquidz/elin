(ns elin.system
  (:require
   [com.stuartsierra.component :as component]
   [elin.component.handler :as elin.c.handler]
   [elin.component.server :as elin.c.server]
   [msgpack.clojure-extensions]))

(defn new-system
  ([]
   (new-system {:server {:port 0}}))
  ([config]
   (component/system-map
    :handler (elin.c.handler/map->Handler {})
    :server (component/using
             (elin.c.server/map->Server (or (:server config) {}))
             [:handler]))))
