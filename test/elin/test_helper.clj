(ns elin.test-helper
  (:require
   [babashka.nrepl.server :as b.n.server]
   [clojure.core.async :as async]
   [elin.component.interceptor :as e.c.interceptor]
   [elin.component.nrepl :as e.c.nrepl]
   [elin.component.nrepl.client :as e.c.n.client]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.schema.server :as e.s.server]
   [elin.util.id :as e.u.id]
   [malli.core :as m]
   [malli.dev.pretty :as m.d.pretty]
   [malli.instrument :as m.inst]))

(def ^:dynamic *nrepl-server-port* nil)

;; FIXTURES {{{
(defn malli-instrument-fixture
  [f]
  (m.inst/instrument!
   {:report (m.d.pretty/reporter)})
  (f))

(defn test-nrepl-server-port-fixture
  [f]
  (let [{:as server :keys [socket]} (b.n.server/start-server! {:host "localhost" :port 0})
        port (.getLocalPort socket)]
    (try
      (binding [*nrepl-server-port* port]
        (f))
      (finally
        (b.n.server/stop-server! server)))))
;; }}}

;; RECORDS {{{

(def ?TestMessageOption
  [:map
   [:handler [:=> [:cat [:sequential any?]] any?]]])

(defrecord TestMessage ; {{{
  [host message]
  e.p.rpc/IMessage
  (request? [_]
    (= 0 (first message)))

  (response? [_]
    (= 1 (first message)))

  (parse-message [_]
    (condp = (first message)
      ;; request
      0 (let [[_ id method [params]] message]
          {:id id
           :method (keyword method)
           :params params})
      ;; response
      1 (let [[_ id error result] message]
          {:id id
           :error error
           :result result})
      ;; notify
      2 (let [[_ method [params callback]] message]
          {:method (keyword method)
           :params params
           :callback callback})
      {}))) ; }}}

(defprotocol ITestWriter
  (get-outputs [this]))

(defrecord TestWriter ; {{{
  [output-stream outputs option]
  e.p.rpc/IWriter
  (request! [_ content]
    (let [id (e.u.id/next-id)
          {:keys [handler]} option
          [result error] (try
                           [(handler (concat [0 id] content))]
                           (catch Exception ex
                             [nil (ex-message ex)]))
          ch (async/chan)]
      (async/go
        (async/>! ch {:result result :error error}))
      ch))

  (notify! [_ content]
    (let [{:keys [handler]} option]
      (handler (concat [2] content))
      nil))

  (response! [_this _error _result]
    nil)

  e.p.rpc/IFunction
  (call-function [this method params]
    (e.p.rpc/request! this ["test_call_function" [method params]]))

  (echo-text [_ text]
    (swap! outputs conj text))
  (echo-message [_ text]
    (swap! outputs conj text))
  (echo-message [_ text _highlight]
    (swap! outputs conj text))

  ITestWriter
  (get-outputs [_] @outputs)) ; }}}

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

(defrecord TestNreplConnection ; {{{
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
      (e.p.nrepl/notify this msg)))) ; }}}

;; }}}

;; FUNCTIONS {{{
(m/=> test-message [:function
                    [:=> :cat e.s.server/?Message]
                    [:=> [:cat [:sequential any?]] e.s.server/?Message]])
(defn test-message
  ([]
   (test-message []))
  ([messages]
   (map->TestMessage {:host "test"
                      :message messages})))

(m/=> test-writer [:=> [:cat ?TestMessageOption] e.s.server/?Writer])
(defn test-writer
  [option]
  (map->TestWriter {:output-stream (java.io.ByteArrayOutputStream.)
                    :outputs (atom [])
                    :option option}))

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
  (let [writer (test-writer (or (:lazy-writer option) {}))
        interceptor (e.c.interceptor/new-interceptor (or (:interceptor option) {}))
        client (test-nrepl-client (or (:client option) {}))
        nrepl (e.c.nrepl/new-nrepl
               {:nrepl {:interceptor interceptor
                        :lazy-writer writer}})]
    (e.p.nrepl/add-client! nrepl client)
    (e.p.nrepl/switch-client! nrepl client)
    nrepl))

(defn call-function? [msg fn-name]
  (and
   (= "test_call_function" (nth msg 2))
   (= fn-name (first (nth msg 3)))))
;; }}}
