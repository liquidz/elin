(ns elin.test-helper.writer
  (:require
   [clojure.core.async :as async]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.schema.server :as e.s.server]
   [elin.test-helper.message :as h.message]
   [elin.util.id :as e.u.id]
   [malli.core :as m]))

(defrecord TestWriter ; {{{
  [writer-store outputs option]
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

(defn get-outputs [test-writer]
  @(:outputs test-writer))

(m/=> test-writer [:=> [:cat h.message/?TestMessageOption] e.s.server/?Writer])
(defn test-writer
  [option]
  (map->TestWriter {:writer-store (atom nil)
                    :outputs (atom [])
                    :option option}))
