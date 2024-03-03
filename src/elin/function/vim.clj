(ns elin.function.vim
  (:require
   [clojure.core.async :as async]
   [elin.error :as e]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.schema :as e.schema]
   [elin.schema.server :as e.s.server]
   [elin.schema.vim :as e.s.vim]
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

(m/=> get-current-working-directory!! [:=> [:cat e.s.server/?Host [:* any?]] (e.schema/error-or string?)])
(defn get-current-working-directory!!
  [host & extra-params]
  (let [params (or extra-params [])]
    (async/<!! (call host "getcwd" params))))

(m/=> get-current-file-path!! [:=> [:cat e.s.server/?Host] (e.schema/error-or string?)])
(defn get-current-file-path!!
  [host]
  (async/<!! (call host "expand" ["%:p"])))

(m/=> get-cursor-position!! [:=> [:cat e.s.server/?Host [:* any?]] (e.schema/error-or e.s.vim/?Position)])
(defn get-cursor-position!!
  [host & extra-params]
  (e/let [params (or extra-params [])
          [bufnum lnum col off curswant] (async/<!! (call host "getcurpos" params))]
    {:bufname bufnum
     :lnum lnum
     :col col
     :off off
     :curswant curswant}))

(m/=> jump!! [:=> [:cat e.s.server/?Host string? int? int? [:* any?]] [:maybe e.schema/?Error]])
(defn jump!!
  [host path lnum col & [jump-command]]
  (let [jump-command (or jump-command "edit")
        res (async/<!! (call host "elin#internal#jump" [path lnum col jump-command]))]
    (when (e/error? res)
      res)))

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

(m/=> get-variable!! [:=> [:cat e.s.server/?Host string?] any?])
(defn get-variable!!
  [host var-name]
  (eval!! host (format "exists('%s') ? %s : v:null" var-name var-name)))

(m/=> set-variable! [:=> [:cat e.s.server/?Host string? any?] e.schema/?ManyToManyChannel])
(defn set-variable!
  [host var-name value]
  (let [value' (cond
                 (string? value) (str "'" value "'")
                 (true? value) "v:true"
                 (false? value) "v:false"
                 :else value)]
    (execute! host (format "let %s = %s" var-name value'))))

(m/=> set-variable!! [:=> [:cat e.s.server/?Host string? any?] :nil])
(defn set-variable!!
  [host var-name value]
  (async/<!! (set-variable! host var-name value))
  nil)

(m/=> input!! [:=> [:cat e.s.server/?Host string? string?] string?])
(defn input!!
  [host prompt default]
  (call!! host "input" [prompt default]))
