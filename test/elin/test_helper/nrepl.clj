(ns elin.test-helper.nrepl
  (:require
   [clojure.core.async :as async]
   [elin.component.interceptor :as e.c.interceptor]
   [elin.component.nrepl :as e.c.nrepl]
   [elin.component.nrepl.client :as e.c.n.client]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.test-helper.host :as h.host]))

(defn- nrepl-connectin-default-handler
  [msg]
  (condp = (:op msg)
    "clone"
    [{:new-session "dummy-session"}]

    "describe"
    [{:ops {:clone 1 :describe 1 :eval 1 :lookup 1}
      :versions {:elin "test"}}]

    "eval"
    (when (= (str '(ns-name *ns*)) (:code msg))
      [{:value "user"}])

    nil))

(defrecord TestNreplConnection
  [host port socket read-stream write-stream output-channel response-manager
   connected-atom option]
  e.p.nrepl/IConnection
  (disconnect [_]
    (reset! connected-atom false))

  (disconnected? [_]
    (not @connected-atom))

  (notify [this msg]
    (when-not (e.p.nrepl/disconnected? this)
      (let [option-handler (or (:handler option) (constantly nil))
            res (option-handler msg)]
        (if res
          res
          (nrepl-connectin-default-handler msg)))))

  (request [this msg]
    (async/go
      (e.p.nrepl/notify this msg))))

(defn test-nrepl-connection
  [option]
  (map->TestNreplConnection
   {:host "localhost"
    :port 1234
    :socket (java.net.Socket.)
    :read-stream (java.io.PushbackInputStream.
                  (java.io.ByteArrayInputStream. (.getBytes "")))
    :write-stream (java.io.ByteArrayOutputStream.)
    :output-channel (async/chan)
    :response-manager (atom {})
    :connected-atom (atom true)
    :option option}))

(defn test-nrepl-client
  [option]
  (-> (test-nrepl-connection option)
      (e.c.n.client/new-client)))

(defn test-nrepl
  [option]
  (let [host (h.host/test-host (merge {:handler identity}
                                      (or (:lazy-host option) {})))
        interceptor (e.c.interceptor/new-interceptor
                     {:interceptor (merge {:lazy-host host
                                           :interceptor-map {}}
                                          (or (:interceptor option) {}))})
        client (test-nrepl-client (or (:client option) {}))
        nrepl (e.c.nrepl/new-nrepl
               {:nrepl {:interceptor interceptor
                        :lazy-host host}})]
    (e.p.nrepl/add-client! nrepl client)
    (e.p.nrepl/switch-client! nrepl client)
    nrepl))

(comment
  (let [
        option {}
        host (h.host/test-host (merge {:handler identity}
                                      (or (:lazy-host option) {})))
        interceptor (e.c.interceptor/new-interceptor
                      {:interceptor (merge {:lazy-host host
                                            :interceptor-map {}}
                                           (or (:interceptor option) {}))})]


    interceptor))
