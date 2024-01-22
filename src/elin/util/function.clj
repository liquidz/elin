(ns elin.util.function
  (:require
   [clojure.core.async :as async]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.schema.server :as e.s.server]
   [malli.core :as m]))

(m/=> call-function [:=>
                     [:cat e.s.server/?Writer string? [:sequential any?]]
                     any?])
(defn call-function
  [writer fn-name params]
  (let [{:keys [result error]} (async/<!! (e.p.rpc/call-function writer fn-name params))]
    (if error
      (throw (ex-info "Failed to call function" {:function fn-name
                                                 :params params
                                                 :error error}))
      result)))

(m/=> luaeval [:=> [:cat e.s.server/?Writer string? [:sequential any?]] any?])
(defn luaeval [writer code args]
  (call-function writer "luaeval" [code args]))
