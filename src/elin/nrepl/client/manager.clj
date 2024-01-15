(ns elin.nrepl.client.manager
  (:require
   [elin.nrepl.client :as e.n.client]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.util.schema :as e.u.schema]
   [malli.core :as m]))

(m/=> client-key [:function
                  [:=> [:cat string? int?] string?]
                  [:=> [:cat e.n.client/?Client] string?]])
(defn- client-key
  ([host port]
   (format "%s:%s" host port))
  ([c]
   (format "%s:%s" (get-in c [:connection :host]) (get-in c [:connection :port]))))

(defrecord Manager
  [clients ; atom of [:map-of string? e.n.client/?Client]
   current-client-key] ; atom of [:maybe string?]]

  e.p.nrepl/IClientManager
  (add-client!
    [_ client]
    (swap! clients assoc (client-key client) client)
    client)
  (add-client!
    [this host port]
    (e.p.nrepl/add-client! this (e.n.client/connect host port)))

  (remove-client!
    [_ client]
    (swap! clients dissoc (client-key client))
    (e.n.client/disconnect client))

  (get-client
    [this host port]
    (e.p.nrepl/get-client this (client-key host port)))
  (get-client
    [_ client-key]
    (get @clients client-key))

  (switch-client!
    [_ client]
    (let [c-key (client-key client)]
      (if (contains? @clients c-key)
        (do
          (reset! current-client-key c-key)
          true)
        false)))

  (current-client [this]
    (e.p.nrepl/get-client this @current-client-key))

  e.p.nrepl/IClient
  (supported-op?
    [this op]
    (when-let [client (e.p.nrepl/current-client this)]
      (e.p.nrepl/supported-op? client op)))

  e.p.nrepl/IConnection
  (disconnect
    [this]
    (if-let [client (e.p.nrepl/current-client this)]
      (e.p.nrepl/disconnect client)
      (throw (ex-info "Not connected" {}))))

  (disconnected?
    [this]
    (if-let [client (e.p.nrepl/current-client this)]
      (e.p.nrepl/disconnected? client)
      (throw (ex-info "Not connected" {}))))

  (notify [this msg]
    (if-let [client (e.p.nrepl/current-client this)]
      (e.p.nrepl/notify client msg)
      (throw (ex-info "Not connected" {}))))

  (request [this msg]
    (when-let [client (e.p.nrepl/current-client this)]
      (e.p.nrepl/request client msg)))

  e.p.nrepl/INreplOp
  (close-op [this]
    (when-let [client (e.p.nrepl/current-client this)]
      (e.p.nrepl/close-op client)))

  (eval-op [this code options]
    (when-let [client (e.p.nrepl/current-client this)]
      (e.p.nrepl/eval-op client code options)))

  (interrupt-op [this options]
    (when-let [client (e.p.nrepl/current-client this)]
      (e.p.nrepl/interrupt-op client options)))

  (load-file-op [this file options]
    (when-let [client (e.p.nrepl/current-client this)]
      (e.p.nrepl/load-file-op client file options))))

(m/=> new-manager [:=> :cat (e.u.schema/?instance Manager)])
(defn new-manager
  []
  (map->Manager {:clients (atom {})
                 :current-client-key (atom nil)}))
