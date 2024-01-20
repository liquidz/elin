(ns elin.component.nrepl
  (:require
   [com.stuartsierra.component :as component]
   [elin.log :as e.log]
   [elin.nrepl.client.manager :as e.n.c.manager]
   [elin.protocol.nrepl :as e.p.nrepl]
   [msgpack.clojure-extensions]))

(defrecord Nrepl
  [client-manager]
  component/Lifecycle
  (start [this]
    (e.log/debug "Nrepl component: Started")
    (assoc this :client-manager (e.n.c.manager/new-manager)))
  (stop [this]
    (e.log/info "Nrepl component: Stopping")
    (e.p.nrepl/remove-all! client-manager)
    (e.log/info "Nrepl component: Stopped")
    (dissoc this :client-manager)))
