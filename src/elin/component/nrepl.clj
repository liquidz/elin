(ns elin.component.nrepl
  (:require
   [clojure.core.async :as async]
   [com.stuartsierra.component :as component]
   [elin.component.nrepl.client :as e.c.n.client]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.error :as e]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema.nrepl :as e.s.nrepl]
   [malli.core :as m]
   [msgpack.clojure-extensions]
   [taoensso.timbre :as timbre]))

(m/=> client-key [:function
                  [:=> [:cat string? int?] string?]
                  [:=> [:cat e.s.nrepl/?Client] string?]])
(defn- client-key
  ([host port]
   (format "%s:%s" host port))
  ([c]
   (format "%s:%s" (get-in c [:connection :host]) (get-in c [:connection :port]))))

(defrecord Nrepl
  [;; COMPONENTS
   clj-kondo
   interceptor
   lazy-host
   session-storage
   ;; PARAMS
   clients-store ; atom of [:map-of string? e.c.n.client/?Client]
   current-client-key-store] ; atom of [:maybe string?]]

  component/Lifecycle
  (start [this]
    (timbre/info "Nrepl component: Started")
    this)
  (stop [this]
    (timbre/info "Nrepl component: Stopping")
    (e.p.nrepl/remove-all! this)
    (timbre/info "Nrepl component: Stopped")
    (dissoc this :client-manager))

  e.p.nrepl/IClientManager
  (add-client!
    [this client]
    (cond
      (satisfies? e.p.nrepl/IClient client)
      (do (swap! clients-store assoc (client-key client) client)
          client)

      (map? client)
      (e.p.nrepl/add-client! this (e.c.n.client/connect client))

      :else
      nil))

  (remove-client!
    [_ client]
    (swap! clients-store dissoc (client-key client))
    (e.p.nrepl/disconnect client))

  (remove-all!
    [this]
    (doseq [client (e.p.nrepl/all-clients this)]
      (e.p.nrepl/remove-client! this client)))

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

  (all-clients [_]
    (vals @clients-store))

  e.p.nrepl/IClient
  (supported-op?
    [this op]
    (when-let [client (e.p.nrepl/current-client this)]
      (e.p.nrepl/supported-op? client op)))

  (current-session [this]
    (when-let [client (e.p.nrepl/current-client this)]
      (e.p.nrepl/current-session client)))

  (version [this]
    (when-let [client (e.p.nrepl/current-client this)]
      (e.p.nrepl/version client)))

  e.p.nrepl/IConnection
  (disconnect
    [this]
    (if-let [client (e.p.nrepl/current-client this)]
      (e.p.nrepl/disconnect client)
      (e/unavailable {:message "Not connected"})))

  (disconnected?
    [this]
    (if-let [client (e.p.nrepl/current-client this)]
      (e.p.nrepl/disconnected? client)
      true))

  (notify [this msg]
    (if-let [client (e.p.nrepl/current-client this)]
      (async/go
        (let [intercept #(apply e.p.interceptor/execute interceptor e.c.interceptor/nrepl %&)]
          (-> {:component/host lazy-host
               :component/interceptor interceptor
               :component/session-storage session-storage
               :component/clj-kondo clj-kondo
               :component/nrepl this
               :request msg}
              (intercept
               (fn [{:as ctx :keys [request]}]
                 (assoc ctx :response (e.p.nrepl/notify client request))))
              (:response))))
      (e/unavailable {:message "Not connected"})))

  (request [this msg]
    (if-let [client (e.p.nrepl/current-client this)]
      (async/go
        (let [intercept #(apply e.p.interceptor/execute interceptor e.c.interceptor/nrepl %&)]
          (-> {:component/host lazy-host
               :component/interceptor interceptor
               :component/session-storage session-storage
               :component/clj-kondo clj-kondo
               :component/nrepl this
               :request msg}
              (intercept
               (fn [{:as ctx :keys [request]}]
                 (assoc ctx :response (async/<! (e.p.nrepl/request client request)))))
              (:response))))
      (async/go
        (e/unavailable {:message "Not connected"})))))

(defn new-nrepl
  [config]
  (map->Nrepl (merge
               (:nrepl config)
               {:clients-store (atom {})
                :current-client-key-store (atom nil)})))
