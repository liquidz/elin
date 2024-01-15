(ns elin.nrepl.connection
  (:require
   [bencode.core :as b]
   [clojure.core.async :as async]
   [elin.nrepl.message :as e.n.message]
   [elin.nrepl.response :as e.n.response]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.util.id :as e.u.id]
   [elin.util.schema :as e.u.schema]
   [malli.core :as m])
  (:import
   clojure.core.async.impl.channels.ManyToManyChannel
   clojure.lang.Atom
   (java.io
    OutputStream
    PushbackInputStream)
   (java.net
    Socket
    SocketException)))

(def ?Connection
  [:map
   [:host string?]
   [:port int?]
   [:socket (e.u.schema/?instance Socket)]
   [:read-stream (e.u.schema/?instance PushbackInputStream)]
   [:write-stream (e.u.schema/?instance OutputStream)]
   [:output-channel (e.u.schema/?instance ManyToManyChannel)]
   [:response-manager (e.u.schema/?instance Atom)]])

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
    (when-not (.isClosed socket)
      (.close socket)
      (async/close! output-channel)
      (reset! response-manager {})))

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
        (async/<!! (async/timeout 100))
        (->> (update-keys msg (comp str symbol))
             (b/write-bencode write-stream))
        (get-in @response-manager [id :channel])))))

(m/=> connect [:=> [:cat string? int?] ?Connection])
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
              msg (e.n.message/format-message v)]
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

(comment
  (let [conn (connect "localhost" 61081)]
    (try
      (println "connected")
      (let [{:keys [new-session]} (e.n.message/merge-messages
                                    (async/<!! (e.p.nrepl/request conn {:op "clone"})))]
        (println "resp"
                 (e.n.message/merge-messages
                   (async/<!! (e.p.nrepl/request conn {:op "eval" :code "(+ 1 2 3)" :session new-session})))))

      (finally
        (e.p.nrepl/disconnect conn)))))
