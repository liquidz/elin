(ns elin.nrepl.client
  (:require
   [clojure.core.async :as async]
   [elin.nrepl.connection :as e.n.connection]
   [elin.nrepl.constant :as e.n.constant]
   [elin.nrepl.message :as e.n.message]
   [elin.nrepl.protocol :as e.n.protocol]
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

  e.n.protocol/IConnection
  (disconnect [_]
    (e.n.protocol/disconnect connection))
  (disconnected? [_]
    (e.n.protocol/disconnected? connection))
  (notify [this msg]
    (when-not (e.n.protocol/supported-op? this (:op msg))
      (throw (Exception. "FIXME not supported op")))
    (let [msg (if (contains? msg :session)
                msg
                (assoc msg :session session))]
      (e.n.protocol/notify connection msg)))
  (request [this msg]
    (when-not (e.n.protocol/supported-op? this (:op msg))
      (throw (Exception. "FIXME not supported op")))
    (let [msg (if (contains? msg :session)
                msg
                (assoc msg :session session))]
      (e.n.protocol/request connection msg)))

  e.n.protocol/IClient
  (supported-op? [_ op]
    (contains? supported-ops (keyword op)))

  e.n.protocol/INreplOp
  (close-op [this]
    (e.n.protocol/request this {:op "close" :session session}))

  (eval-op [this code options]
    (->> (merge (select-keys options e.n.constant/eval-option-keys)
                {:op "eval" :session session :code code})
         (e.n.protocol/request this)))

  (interrupt-op [this options]
    (->> (merge (select-keys options #{:interrupt-id})
                {:op "interrupt" :session session})
         (e.n.protocol/request this)))

  (load-file-op [this file options]
    (->> (merge (select-keys options e.n.constant/load-file-option-keys)
                {:op "load-file" :session session :file file})
         (e.n.protocol/request this))))

(m/=> connect [:=> [:cat string? int?] ?Client])
(defn connect
  [host port]
  (let [conn (e.n.connection/connect host port)
        clone-resp (e.n.message/merge-messages
                    (async/<!! (e.n.protocol/request conn {:op "clone"})))
        describe-resp (e.n.message/merge-messages
                       (async/<!! (e.n.protocol/request conn {:op "describe"})))]
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
      (println "resp" (async/<!! (e.n.protocol/request client {:op "eval" :code "(+ 1 2 3)"})))
      (finally
        (e.n.protocol/disconnect client)))))

