(ns elin.util.function
  (:require
   [clojure.core.async :as async]
   [elin.error :as e]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.schema :as e.schema]
   [elin.schema.server :as e.s.server]
   [malli.core :as m]
   [merr.core :as merr]))

(m/=> call-function [:=>
                     [:cat e.s.server/?Writer string? [:sequential any?]]
                     e.schema/?ManyToManyChannel])
(defn call-function
  [writer fn-name params]
  (async/go
    (let [{:keys [result error]} (async/<! (e.p.rpc/call-function writer fn-name params))]
      (if error
        (e/fault {:message (str "Failed to call function: " error)
                  :function fn-name
                  :params params})
        result))))

(m/=> luaeval [:=> [:cat e.s.server/?Writer string? [:sequential any?]]
               e.schema/?ManyToManyChannel])
(defn luaeval [writer code args]
  (call-function writer "luaeval" [code args]))

(comment
  (do
    (println "foo")
    (let [ch (async/go
               (println "aaa")
               (async/<! (async/timeout 100))
               (println "bbb")
               (merr/error))]
      (println "bar")
      (async/<!! (async/timeout 200))
      (println "finish" (async/<!! ch)))))
