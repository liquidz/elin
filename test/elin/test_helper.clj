(ns elin.test-helper
  (:require
   [babashka.nrepl.server :as b.n.server]
   [clojure.core.async :as async]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.schema.server :as e.s.server]
   [elin.util.id :as e.u.id]
   [malli.core :as m]
   [malli.dev.pretty :as m.d.pretty]
   [malli.instrument :as m.inst]))

(def ^:dynamic *nrepl-server-port* nil)

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

(def ?TestMessageOption
  [:map
   [:handler [:=> [:cat [:sequential any?]] any?]]])

(defprotocol ITestWriter
  (get-outputs [this]))

(defrecord TestMessage
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
      {})))

(defrecord TestWriter
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
  (get-outputs [_] @outputs))

(m/=> test-message [:=> :cat e.s.server/?Message])
(defn test-message
  []
  (map->TestMessage {:host "test"
                     :message []}))

(m/=> test-writer [:=> [:cat ?TestMessageOption] e.s.server/?Writer])
(defn test-writer
  [option]
  (map->TestWriter {:output-stream (java.io.ByteArrayOutputStream.)
                    :outputs (atom [])
                    :option option}))

(defn call-function? [msg fn-name]
  (and
   (= "test_call_function" (nth msg 2))
   (= fn-name (first (nth msg 3)))))
