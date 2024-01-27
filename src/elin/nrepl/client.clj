(ns elin.nrepl.client
  (:require
   [clojure.core.async :as async]
   [elin.nrepl.connection :as e.n.connection]
   [elin.nrepl.message :as e.n.message]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema.nrepl :as e.s.nrepl]
   [malli.core :as m]))

(defrecord Client
  [connection
   session
   supported-ops
   initial-namespace
   version]

  e.p.nrepl/IConnection
  (disconnect [_]
    (e.p.nrepl/disconnect connection))
  (disconnected? [_]
    (e.p.nrepl/disconnected? connection))
  (notify [this msg]
    (when-not (e.p.nrepl/supported-op? this (:op msg))
      (throw (ex-info "Not supported operation" {:message msg})))
    (let [msg (if (contains? msg :session)
                msg
                (assoc msg :session session))]
      (e.p.nrepl/notify connection msg)))
  (request [this msg]
    (when-not (e.p.nrepl/supported-op? this (:op msg))
      (throw (ex-info "Not supported operation" {:message msg})))
    (let [msg (if (contains? msg :session)
                msg
                (assoc msg :session session))]
      (e.p.nrepl/request connection msg)))

  e.p.nrepl/IClient
  (supported-op? [_ op]
    (contains? supported-ops (keyword op))))

(m/=> connect [:=> [:cat string? int?] e.s.nrepl/?Client])
(defn connect
  [host port]
  (let [conn (e.n.connection/connect host port)
        clone-resp (e.n.message/merge-messages
                    (async/<!! (e.p.nrepl/request conn {:op "clone"})))
        describe-resp (e.n.message/merge-messages
                       (async/<!! (e.p.nrepl/request conn {:op "describe"})))]
    (map->Client
     {:connection conn
      :session (:new-session clone-resp)
      :supported-ops (set (keys (:ops describe-resp)))
      :initial-namespace (get-in describe-resp [:aux :current-ns])
      :version (:versions describe-resp)})))
