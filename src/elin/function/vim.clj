(ns elin.function.vim
  (:require
   [clojure.core.async :as async]
   [elin.error :as e]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.schema :as e.schema]
   [elin.schema.server :as e.s.server]
   [elin.util.server :as e.u.server]
   [malli.core :as m]))

(m/=> call [:=>
            [:cat e.s.server/?Host string? [:sequential any?]]
            e.schema/?ManyToManyChannel])
(defn call
  [host fn-name params]
  (async/go
    (let [{:keys [result error]} (->> (e.u.server/format params)
                                      (e.p.rpc/call-function host fn-name)
                                      (async/<!))]
      (if error
        (e/fault {:message (str "Failed to call function: " error)
                  :function fn-name
                  :params params})
        result))))

(m/=> notify [:=> [:cat e.s.server/?Host string? [:sequential any?]] :nil])
(defn notify
  [host fn-name params]
  (->> (map e.u.server/format params)
       (e.p.rpc/notify-function host fn-name))
  nil)

(m/=> luaeval [:=> [:cat e.s.server/?Host string? [:sequential any?]]
               e.schema/?ManyToManyChannel])
(defn luaeval [host code args]
  (call host "luaeval" [code args]))

(m/=> call!! [:=> [:cat e.s.server/?Host string? [:sequential any?]] any?])
(defn call!! [host function-name params]
  (async/<!! (call host function-name params)))

(m/=> eval!! [:=> [:cat e.s.server/?Host string?] any?])
(defn eval!!
  [host s]
  (async/<!! (call host "elin#internal#eval" [s])))

(m/=> execute! [:=> [:cat e.s.server/?Host string?] e.schema/?ManyToManyChannel])
(defn execute!
  [host cmd]
  (call host "elin#internal#execute" [cmd]))

(m/=> execute!! [:=> [:cat e.s.server/?Host string?] any?])
(defn execute!!
  [host cmd]
  (async/<!! (execute! host cmd)))
