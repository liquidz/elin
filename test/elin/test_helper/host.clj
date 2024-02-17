(ns elin.test-helper.host
  (:require
   [clojure.core.async :as async]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.schema.server :as e.s.server]
   [elin.test-helper.message :as h.message]
   [elin.util.id :as e.u.id]
   [malli.core :as m]))

(defrecord TestHost ; {{{
  [host-store outputs option]
  e.p.rpc/IHost
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
    (swap! outputs conj text)))

(defn get-outputs [test-host]
  @(:outputs test-host))

(m/=> test-host [:=> [:cat h.message/?TestMessageOption] e.s.server/?Host])
(defn test-host
  [option]
  (map->TestHost {:host-store (atom nil)
                  :outputs (atom [])
                  :option option}))