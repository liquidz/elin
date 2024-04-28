(ns elin.test-helper.host
  (:require
   [clojure.core.async :as async]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.host.rpc :as e.p.h.rpc]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.schema.server :as e.s.server]
   [elin.util.id :as e.u.id]
   [malli.core :as m]))

(defrecord TestHost ; {{{
  [host-store outputs option]
  e.p.h.rpc/IRpc
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

  (response! [_this _error _result] nil)
  (flush! [_] nil)

  e.p.host/IIo
  (echo-text [_ text]
    (swap! outputs conj text))
  (echo-message [_ text]
    (swap! outputs conj text))
  (echo-message [_ text _highlight]
    (swap! outputs conj text))
  (input! [_ _prompt _default]
    nil)

  e.p.host/ISexpr
  (get-top-list-sexpr! [_ _lnum _col]
    (async/go (:get-top-list-sexpr! option)))
  (get-list-sexpr! [_ _lnum _col]
    (async/go (:get-list-sexpr! option)))
  (get-single-sexpr! [_ _lnum _col]
    (async/go (:get-single-sexpr! option)))
  (get-namespace-sexpr! [_]
    (async/go (:get-namespace-sexpr! option)))
  (replace-list-sexpr! [_ _lnum _col _new-sexpr] (async/go nil))

  e.p.rpc/IFunction
  (call-function [this method params]
    (e.p.h.rpc/request! this ["test_call_function" [method params]])))

(defn get-outputs [test-host]
  @(:outputs test-host))

(m/=> test-host
      [:function
       [:=> :cat  e.s.server/?Host]
       [:=> [:cat map?] e.s.server/?Host]])
(defn test-host
  ([]
   (test-host {:handler identity}))
  ([option]
   (map->TestHost {:host-store (atom nil)
                   :outputs (atom [])
                   :option (merge {:handler identity}
                                  option)})))
