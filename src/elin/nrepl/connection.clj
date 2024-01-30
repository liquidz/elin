(ns elin.nrepl.connection
  (:require
   [bencode.core :as b]
   [clojure.core.async :as async]
   [elin.constant.nrepl :as e.c.nrepl]
   [elin.nrepl.response :as e.n.response]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema.nrepl :as e.s.nrepl]
   [elin.util.id :as e.u.id]
   [elin.util.schema :as e.u.schema]
   [malli.core :as m])
  (:import
   java.io.PushbackInputStream
   (java.net
    Socket
    SocketException)))

(m/=> bytes->str [:=> [:cat any?] e.u.schema/?NotBytes])
(defn- bytes->str
  [x]
  (if (bytes? x)
    (String. (bytes x))
    x))

(m/=> format-message [:=> [:cat [:map-of string? any?]] e.s.nrepl/?Message])
(defn- format-message
  [msg]
  (reduce-kv
   (fn [accm k v]
     (assoc accm
            (keyword k)
            (cond
              (contains? e.c.nrepl/array-key-set k)
              (mapv bytes->str v)

              (map? v)
              (format-message v)

              :else
              (bytes->str v))))
   {}
   msg))

(defrecord Connection
  [host
   port
   socket
   read-stream
   write-stream
   output-channel
   response-manager]

  e.p.nrepl/IConnection
  (disconnect [_]
    (if (.isClosed socket)
      false
      (do (.close socket)
          (async/close! output-channel)
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
      (async/go nil)
      (let [id (or (:id msg) (e.u.id/next-id))
            msg (assoc msg :id id)]
        (swap! response-manager e.n.response/register-message msg)
        (->> (update-keys msg (comp str symbol))
             (b/write-bencode write-stream))
        (get-in @response-manager [id :channel])))))

(m/=> connect [:=> [:cat string? int?] e.s.nrepl/?Connection])
(defn connect
  [host port]
  (let [sock (Socket. host port)
        output-ch (async/chan)
        read-stream (PushbackInputStream. (.getInputStream sock))
        write-stream (.getOutputStream sock)
        response-manager (atom {})]

    (async/go-loop []
      (try
        (let [v (b/read-bencode read-stream)
              msg (format-message v)]
          (swap! response-manager e.n.response/process-message msg)

          (when (string? (:out msg))
            (async/>! output-ch {:type "out" :text (:out msg)}))
          (when (string? (:pprint-out msg))
            (async/>! output-ch {:type "pprint-out" :text (:pprint-out msg)}))
          (when (string? (:err msg))
            (async/>! output-ch {:type "err" :text (:err msg)})))
        (catch SocketException _ nil))
      (when-not (.isClosed sock)
        (recur)))

    (map->Connection
     {:host host
      :port port
      :socket sock
      :read-stream read-stream
      :write-stream write-stream
      :output-channel output-ch
      :response-manager response-manager})))
