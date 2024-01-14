(ns elin.nrepl.client.manager
  (:require
   [elin.nrepl.client :as e.n.client]
   [elin.nrepl.protocol :as e.n.protocol]
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

  e.n.protocol/IClientManager
  (add-client!
    [_ client]
    (swap! clients assoc (client-key client) client)
    client)
  (add-client!
    [this host port]
    (e.n.protocol/add-client! this (e.n.client/connect host port)))

  (remove-client!
    [_ client]
    (swap! clients dissoc (client-key client))
    (e.n.client/disconnect client))

  (get-client
    [this host port]
    (e.n.protocol/get-client this (client-key host port)))
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
    (e.n.protocol/get-client this @current-client-key))

  e.n.protocol/IClient
  (supported-op?
    [this op]
    (when-let [client (e.n.protocol/current-client this)]
      (e.n.protocol/supported-op? client op)))

  e.n.protocol/IConnection
  (disconnect
    [this]
    (if-let [client (e.n.protocol/current-client this)]
      (e.n.protocol/disconnect client)
      (throw (ex-info "Not connected" {}))))

  (disconnected?
    [this]
    (if-let [client (e.n.protocol/current-client this)]
      (e.n.protocol/disconnected? client)
      (throw (ex-info "Not connected" {}))))

  (notify [this msg]
    (if-let [client (e.n.protocol/current-client this)]
      (e.n.protocol/notify client msg)
      (throw (ex-info "Not connected" {}))))

  (request [this msg]
    (when-let [client (e.n.protocol/current-client this)]
      (e.n.protocol/request client msg)))

  e.n.protocol/INreplOp
  (close-op [this]
    (when-let [client (e.n.protocol/current-client this)]
      (e.n.protocol/close-op client)))

  (eval-op [this code options]
    (when-let [client (e.n.protocol/current-client this)]
      (e.n.protocol/eval-op client code options)))

  (interrupt-op [this options]
    (when-let [client (e.n.protocol/current-client this)]
      (e.n.protocol/interrupt-op client options)))

  (load-file-op [this file options]
    (when-let [client (e.n.protocol/current-client this)]
      (e.n.protocol/load-file-op client file options))))

(m/=> new-manager [:=> :cat (e.u.schema/?instance Manager)])
(defn new-manager
  []
  (map->Manager {:clients (atom {})
                 :current-client-key (atom nil)}))
