(ns elin.component.nrepl.client
  (:require
   [clojure.core.async :as async]
   [elin.component.nrepl.connection :as e.c.n.connection]
   [elin.error :as e]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema :as e.schema]
   [elin.schema.nrepl :as e.s.nrepl]
   [elin.util.nrepl :as e.u.nrepl]
   [malli.core :as m]
   [malli.util :as m.util]))

(def default-hostname "localhost")

(defrecord Client
  [connection
   session
   supported-ops
   initial-namespace
   version
   language
   port-file]

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
    session)
  (version [_]
    version))

(def ^:private ?ConnectArgumentMap
  (m.util/merge
    [:map
     [:host [:maybe string?]]
     [:port [:maybe int?]]]
    (-> e.s.nrepl/?Client
        (m.util/select-keys [:port-file :language])
        (m.util/optional-keys [:port-file :language]))))

(m/=> new-client [:function
                  [:=> [:cat e.s.nrepl/?Connection] e.s.nrepl/?Client]
                  [:=> [:cat e.s.nrepl/?Connection ?ConnectArgumentMap] e.s.nrepl/?Client]])
(defn new-client
  ([conn]
   (new-client conn {:host nil
                     :port nil}))
  ([conn {:keys [language port-file]}]
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
        :version (:versions describe-resp)
        :language language
        :port-file port-file}))))

(m/=> connect [:=> [:cat ?ConnectArgumentMap] (e.schema/error-or e.s.nrepl/?Client)])
(defn connect
  [{:as arg :keys [host port port-file]}]
  (let [host' (or host default-hostname)
        port' (or port
                  (some-> port-file
                          (slurp)
                          (Long/parseLong)))]
    (e/-> (e.c.n.connection/connect host' port')
          (new-client arg))))
