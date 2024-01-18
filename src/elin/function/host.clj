(ns elin.function.host
  (:require
   [clojure.core.async :as async]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.schema.server :as e.s.server]
   [malli.core :as m]))

(m/=> call-function [:=>
                     [:cat e.s.server/?Message string? [:sequential any?]]
                     any?])
(defn call-function
  [msg fn-name params]
  (let [{:keys [result error]} (async/<!! (e.p.rpc/call-function msg fn-name params))]
    (if error
      (throw (ex-info "Failed to call function" {:function fn-name
                                                 :params params
                                                 :error error}))
      result)))

(defn getcurpos
  [msg & extra-params]
  (let [params (or extra-params [])
        [bufnum lnum col off curswant] (call-function msg "getcurpos" params)]
    {:bufname bufnum
     :lnum lnum
     :col col
     :off off
     :curswant curswant}))
