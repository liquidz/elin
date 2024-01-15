(ns elin.nrepl.client
  (:require
   [clojure.core.async :as async]
   [elin.nrepl.connection :as e.n.connection]
   [elin.nrepl.constant :as e.n.constant]
   [elin.nrepl.message :as e.n.message]
   [elin.protocol.nrepl :as e.p.nrepl]
   [malli.core :as m]))

(defprotocol IClient
  (disconnect [this])
  (disconnected? [this])
  (notify [this msg])
  (request [this msg]))

(def ?Client
  [:map
   [:connection e.n.connection/?Connection]
   [:session string?]
   [:supported-ops [:set keyword?]]
   [:initial-namespace string?]
   [:versions [:map-of keyword? any?]]])

(defrecord Client
  [connection
   session
   supported-ops
   initial-namespace
   versions]

  e.p.nrepl/IConnection
  (disconnect [_]
    (e.p.nrepl/disconnect connection))
  (disconnected? [_]
    (e.p.nrepl/disconnected? connection))
  (notify [this msg]
    (when-not (e.p.nrepl/supported-op? this (:op msg))
      (throw (Exception. "FIXME not supported op")))
    (let [msg (if (contains? msg :session)
                msg
                (assoc msg :session session))]
      (e.p.nrepl/notify connection msg)))
  (request [this msg]
    (when-not (e.p.nrepl/supported-op? this (:op msg))
      (throw (Exception. "FIXME not supported op")))
    (let [msg (if (contains? msg :session)
                msg
                (assoc msg :session session))]
      (e.p.nrepl/request connection msg)))

  e.p.nrepl/IClient
  (supported-op? [_ op]
    (contains? supported-ops (keyword op)))

  e.p.nrepl/INreplOp
  (close-op [this]
    (e.p.nrepl/request this {:op "close" :session session}))

  (eval-op [this code options]
    (->> (merge (select-keys options e.n.constant/eval-option-keys)
                {:op "eval" :session session :code code})
         (e.p.nrepl/request this)))

  (interrupt-op [this options]
    (->> (merge (select-keys options #{:interrupt-id})
                {:op "interrupt" :session session})
         (e.p.nrepl/request this)))

  (load-file-op [this file options]
    (->> (merge (select-keys options e.n.constant/load-file-option-keys)
                {:op "load-file" :session session :file file})
         (e.p.nrepl/request this))))

(m/=> connect [:=> [:cat string? int?] ?Client])
(defn connect
  [host port]
  (let [conn (e.n.connection/connect host port)
        clone-resp (e.n.message/merge-messages
                    (async/<!! (e.p.nrepl/request conn {:op "clone"})))
        describe-resp (e.n.message/merge-messages
                       (async/<!! (e.p.nrepl/request conn {:op "describe"})))]
    (println (pr-str describe-resp))
    (map->Client
     {:connection conn
      :session (:new-session clone-resp)
      :supported-ops (set (keys (:ops describe-resp)))
      :initial-namespace (get-in describe-resp [:aux :current-ns])
      :version (:versions describe-resp)})))

(comment
  (let [client (connect "localhost" 61081)]
    (try
      (println "connected")
      (println "resp" (async/<!! (e.p.nrepl/request client {:op "eval" :code "(+ 1 2 3)"})))
      (finally
        (e.p.nrepl/disconnect client)))))

