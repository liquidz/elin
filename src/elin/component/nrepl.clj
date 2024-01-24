(ns elin.component.nrepl
  (:require
   [clojure.core.async :as async]
   [com.stuartsierra.component :as component]
   [elin.constant.kind :as e.c.kind]
   [elin.log :as e.log]
   [elin.nrepl.client :as e.n.client]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.protocol.nrepl :as e.p.nrepl]
   [malli.core :as m]
   [msgpack.clojure-extensions]))

(m/=> client-key [:function
                  [:=> [:cat string? int?] string?]
                  [:=> [:cat e.n.client/?Client] string?]])
(defn- client-key
  ([host port]
   (format "%s:%s" host port))
  ([c]
   (format "%s:%s" (get-in c [:connection :host]) (get-in c [:connection :port]))))

(defrecord Nrepl
  [interceptor
   clients-store ; atom of [:map-of string? e.n.client/?Client]
   current-client-key-store ; atom of [:maybe string?]]
   writer-store] ; atom of OutputStream

  component/Lifecycle
  (start [this]
    (e.log/debug "Nrepl component: Started")
    this)
  (stop [this]
    (e.log/info "Nrepl component: Stopping")
    (e.p.nrepl/remove-all! this)
    (e.log/info "Nrepl component: Stopped")
    (dissoc this :client-manager))

  e.p.nrepl/INreplComponent
  (set-writer! [_ writer]
    (reset! writer-store writer))

  e.p.nrepl/IClientManager
  (add-client!
    [_ client]
    (swap! clients-store assoc (client-key client) client)
    client)
  (add-client!
    [this host port]
    (e.p.nrepl/add-client! this (e.n.client/connect host port)))

  (remove-client!
    [_ client]
    (swap! clients-store dissoc (client-key client))
    (e.p.nrepl/disconnect client))

  (remove-all!
    [this]
    (doseq [c (vals @clients-store)]
      (e.p.nrepl/remove-client! this c)))

  (get-client
    [this host port]
    (e.p.nrepl/get-client this (client-key host port)))
  (get-client
    [_ client-key]
    (get @clients-store client-key))

  (switch-client!
    [_ client]
    (let [c-key (client-key client)]
      (if (contains? @clients-store c-key)
        (do
          (reset! current-client-key-store c-key)
          true)
        false)))

  (current-client [this]
    (e.p.nrepl/get-client this @current-client-key-store))

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
      (async/go
        (let [intercept #(apply e.p.interceptor/execute interceptor e.c.kind/nrepl %&)]
          (-> {:request msg :writer @writer-store}
              (intercept
               (fn [{:as ctx :keys [request]}]
                 (e.log/info "FIXME kiteruyo" request)
                 (assoc ctx :response (async/<! (e.p.nrepl/request client request)))))
              (:response)))))))

(defn new-nrepl
  [config]
  (map->Nrepl (merge
               (:nrepl config)
               {:clients-store (atom {})
                :current-client-key-store (atom nil)
                :writer-store (atom nil)})))
