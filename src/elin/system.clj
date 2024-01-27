(ns elin.system
  (:require
   [com.stuartsierra.component :as component]
   [elin.component.handler :as e.c.handler]
   [elin.component.interceptor :as e.c.interceptor]
   [elin.component.nrepl :as e.c.nrepl]
   [elin.component.server :as e.c.server]
   [elin.component.writer-store :as e.c.writer-store]
   [msgpack.clojure-extensions]))

(defn new-system
  ([]
   (new-system {:server {:port 0}}))
  ([config]
   (component/system-map
    :writer-store (e.c.writer-store/new-writer-store config)
    :interceptor (component/using
                  (e.c.interceptor/new-interceptor config)
                  [:writer-store])
    :nrepl (component/using
            (e.c.nrepl/new-nrepl config)
            [:interceptor :writer-store])
    :handler (component/using
              (e.c.handler/new-handler config)
              [:nrepl :interceptor :writer-store])
    :server (component/using
             (e.c.server/new-server config)
             [:handler]))))
