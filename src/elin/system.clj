(ns elin.system
  (:require
   [com.stuartsierra.component :as component]
   [elin.component.clj-kondo :as e.c.clj-kondo]
   [elin.component.handler :as e.c.handler]
   [elin.component.interceptor :as e.c.interceptor]
   [elin.component.lazy-host :as e.c.lazy-host]
   [elin.component.nrepl :as e.c.nrepl]
   [elin.component.plugin :as e.c.plugin]
   [elin.component.server :as e.c.server]
   [elin.component.server.http :as e.c.s.http]
   [elin.component.session-storage :as e.c.session-storage]
   [msgpack.clojure-extensions]))

(defn new-system
  ([]
   (new-system {:server {:port 0}}))
  ([config]
   (component/system-map
    :lazy-host (e.c.lazy-host/new-lazy-host config)

    :plugin (component/using
             (e.c.plugin/new-plugin config)
             [:lazy-host])

    :session-storage (e.c.session-storage/new-session-storage config)

    :interceptor (component/using
                  (e.c.interceptor/new-interceptor config)
                  [:lazy-host
                   :plugin])

    :nrepl (component/using
            (e.c.nrepl/new-nrepl config)
            [:clj-kondo
             :interceptor
             :lazy-host
             :session-storage])

    ;; NOTE clj-kondo component should not depend on nrepl component
    :clj-kondo (component/using
                (e.c.clj-kondo/new-clj-kondo config)
                [:lazy-host])

    :handler (component/using
              (e.c.handler/new-handler config)
              [:clj-kondo
               :interceptor
               :lazy-host
               :nrepl
               :plugin
               :session-storage])

    :http-server (component/using
                  (e.c.s.http/new-http-server config)
                  [:handler])

    :server (component/using
             (e.c.server/new-server config)
             [:handler
              :lazy-host]))))
