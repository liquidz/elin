(ns elin.system
  (:require
   [com.stuartsierra.component :as component]
   [elin.component.clj-kondo :as e.c.clj-kondo]
   [elin.component.handler :as e.c.handler]
   [elin.component.interceptor :as e.c.interceptor]
   [elin.component.lazy-writer :as e.c.lazy-writer]
   [elin.component.nrepl :as e.c.nrepl]
   [elin.component.plugin :as e.c.plugin]
   [elin.component.server :as e.c.server]
   [elin.component.server.http :as e.c.s.http]
   [msgpack.clojure-extensions]))

(defn new-system
  ([]
   (new-system {:server {:port 0}}))
  ([config]
   (component/system-map
    :lazy-writer (e.c.lazy-writer/new-lazy-writer config)

    :plugin (component/using
             (e.c.plugin/new-plugin config)
             [:lazy-writer])

    :interceptor (component/using
                  (e.c.interceptor/new-interceptor config)
                  [:lazy-writer
                   :plugin])

    :nrepl (component/using
            (e.c.nrepl/new-nrepl config)
            [:interceptor
             :lazy-writer])

    :clj-kondo (component/using
                (e.c.clj-kondo/new-clj-kondo config)
                [:lazy-writer
                 :nrepl])

    :handler (component/using
              (e.c.handler/new-handler config)
              [:interceptor
               :lazy-writer
               :nrepl
               :plugin])

    ;; NOTE: The port will be stored to elin.constant.server/http-server-port-variable
    :http-server (component/using
                  (e.c.s.http/new-http-server config)
                  [:handler
                   :lazy-writer])

    :server (component/using
             (e.c.server/new-server config)
             [:handler
              :lazy-writer]))))
