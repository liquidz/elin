(ns elin.component.nrepl.connection
  (:require
   [bencode.core :as b]
   [clojure.core.async :as async]
   [elin.error :as e]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema :as e.schema]
   [elin.schema.nrepl :as e.s.nrepl]
   [elin.util.id :as e.u.id]
   [elin.util.nrepl :as e.u.nrepl]
   [malli.core :as m]
   [taoensso.timbre :as timbre])
  (:import
   (java.io PushbackInputStream)
   (java.net Socket)))

(m/=> bytes->str [:=> [:cat any?] e.schema/?NotBytes])
(defn- bytes->str
  [x]
  (if (bytes? x)
    (String. (bytes x))
    x))

(defn- format-message
  [v]
  (cond
    (sequential? v)
    (mapv format-message v)

    (map? v)
    (reduce-kv
      (fn [accm k v]
        (assoc accm (keyword k) (format-message v)))
      {} v)

    :else
    (bytes->str v)))

(m/=> add-message [:=> [:cat e.s.nrepl/?Manager e.s.nrepl/?Message] e.s.nrepl/?Manager])
(defn- add-message
  [this
   {:as msg :keys [id]}]
  (if (and id
           (int? id)
           (contains? this id))
    (update-in this [id :responses] conj msg)
    this))

(m/=> put-done-responses [:=> [:cat e.s.nrepl/?Manager e.s.nrepl/?Message] e.s.nrepl/?Manager])
(defn- put-done-responses
  [this
   {:as msg :keys [id]}]
  (if (and id
           (int? id)
           (e.u.nrepl/has-status? msg "done"))
    (if-let [{:keys [responses channel]} (get this id)]
      (do
        ;; TODO error handling
        (async/put! channel responses)
        (dissoc this id))
      this)
    this))

(m/=> process-message [:=> [:cat e.s.nrepl/?Manager e.s.nrepl/?Message] e.s.nrepl/?Manager])
(defn- process-message
  [this
   msg]
  (-> this
      (add-message msg)
      (put-done-responses msg)))

(m/=> register-message [:=> [:cat e.s.nrepl/?Manager e.s.nrepl/?Message] e.s.nrepl/?Manager])
(defn- register-message
  [this
   msg]
  (let [id (:id msg)]
    (if (and id
             (int? id))
      (assoc this id {:channel (async/promise-chan)
                      :responses []})
      this)))

(defrecord Connection
  [host
   port
   socket
   read-stream
   write-stream
   raw-message-channel
   response-manager]

  e.p.nrepl/IConnection
  (disconnect [_]
    (if (.isClosed socket)
      false
      (do (.close socket)
          (async/close! raw-message-channel)
          (reset! response-manager {})
          true)))

  (disconnected? [_]
    (.isClosed socket))

  (notify [this msg]
    (when-not (e.p.nrepl/disconnected? this)
      (->> (update-keys msg (comp str symbol))
           (b/write-bencode write-stream))))

  (request [this msg]
    (if (e.p.nrepl/disconnected? this)
      (async/go (e/unavailable {:message "Not connected"}))
      (let [id (or (:id msg) (e.u.id/next-id))
            msg (assoc msg :id id)]
        (swap! response-manager register-message msg)
        (->> (update-keys msg (comp str symbol))
             (b/write-bencode write-stream))
        (get-in @response-manager [id :channel])))))

(m/=> connect [:=> [:cat string? int?] (e.schema/error-or e.s.nrepl/?Connection)])
(defn connect
  [host port]
  (try
    (let [sock (Socket. host port)
          raw-message-channel (async/chan)
          read-stream (PushbackInputStream. (.getInputStream sock))
          write-stream (.getOutputStream sock)
          response-manager (atom {})]

      (async/go-loop []
        (try
          (let [v (b/read-bencode read-stream)
                msg (format-message v)]
            (swap! response-manager process-message msg)
            (async/put! raw-message-channel msg))
          (catch Exception ex
            (timbre/debug "Error occured on bencode reading loop" (ex-message ex))
            (.close sock)))
        (when-not (.isClosed sock)
          (recur)))

      (map->Connection
        {:host host
         :port port
         :socket sock
         :read-stream read-stream
         :write-stream write-stream
         :raw-message-channel raw-message-channel
         :response-manager response-manager}))
    (catch Exception ex
      (e/fault {:message (ex-message ex)} ex))))
