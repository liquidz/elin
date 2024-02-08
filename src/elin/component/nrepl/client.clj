(ns elin.component.nrepl.client
  (:require
   [clojure.core.async :as async]
   [elin.component.nrepl.connection :as e.c.n.connection]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema.nrepl :as e.s.nrepl]
   [elin.util.nrepl :as e.u.nrepl]
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
    (contains? supported-ops (keyword op)))
  (current-session [_]
    session))

(defn new-client
  [conn]
  (let [clone-resp (e.u.nrepl/merge-messages
                    (async/<!! (e.p.nrepl/request conn {:op "clone"})))
        describe-resp (e.u.nrepl/merge-messages
                       (async/<!! (e.p.nrepl/request conn {:op "describe"})))
        ns-eval-resp (e.u.nrepl/merge-messages
                      (async/<!! (e.p.nrepl/request conn {:op "eval" :code (str '(ns-name *ns*))})))]
    (map->Client
     {:connection conn
      :session (:new-session clone-resp)
      :supported-ops (set (keys (:ops describe-resp)))
      :initial-namespace (:value ns-eval-resp)
      :version (:versions describe-resp)})))

(m/=> connect [:=> [:cat string? int?] e.s.nrepl/?Client])
(defn connect
  [host port]
  (new-client (e.c.n.connection/connect host port)))
