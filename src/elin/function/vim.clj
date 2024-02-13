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
            [:cat e.s.server/?Writer string? [:sequential any?]]
            e.schema/?ManyToManyChannel])
(defn call
  [writer fn-name params]
  (async/go
    (let [{:keys [result error]} (->> (e.u.server/format params)
                                      (e.p.rpc/call-function writer fn-name)
                                      (async/<!))]
      (if error
        (e/fault {:message (str "Failed to call function: " error)
                  :function fn-name
                  :params params})
        result))))

(m/=> notify [:=> [:cat e.s.server/?Writer string? [:sequential any?]] :nil])
(defn notify
  [writer fn-name params]
  (->> (map e.u.server/format params)
       (e.p.rpc/notify-function writer fn-name))
  nil)

(m/=> luaeval [:=> [:cat e.s.server/?Writer string? [:sequential any?]]
               e.schema/?ManyToManyChannel])
(defn luaeval [writer code args]
  (call writer "luaeval" [code args]))

(m/=> call!! [:=> [:cat e.s.server/?Writer string? [:sequential any?]] any?])
(defn call!! [writer function-name params]
  (async/<!! (call writer function-name params)))

(m/=> get-current-working-directory!! [:=> [:cat e.s.server/?Writer [:* any?]] (e.schema/error-or string?)])
(defn get-current-working-directory!!
  [writer & extra-params]
  (let [params (or extra-params [])]
    (async/<!! (call writer "getcwd" params))))

(m/=> get-current-file-path!! [:=> [:cat e.s.server/?Writer] (e.schema/error-or string?)])
(defn get-current-file-path!!
  [writer]
  (async/<!! (call writer "expand" ["%:p"])))

(m/=> get-cursor-position!! [:=> [:cat e.s.server/?Writer [:* any?]] (e.schema/error-or e.s.vim/?Position)])
(defn get-cursor-position!!
  [writer & extra-params]
  (e/let [params (or extra-params [])
          [bufnum lnum col off curswant] (async/<!! (call writer "getcurpos" params))]
    {:bufname bufnum
     :lnum lnum
     :col col
     :off off
     :curswant curswant}))

(m/=> get-full-path!! [:=> [:cat e.s.server/?Writer] (e.schema/error-or string?)])
(defn get-full-path!!
  [writer]
  (async/<!! (call writer "expand" ["%:p"])))

(m/=> jump!! [:=> [:cat e.s.server/?Writer string? int? int? [:* any?]] [:maybe e.schema/?Error]])
(defn jump!!
  [writer path lnum col & [jump-command]]
  (let [jump-command (or jump-command "edit")
        res (async/<!! (call writer "elin#internal#jump" [path lnum col jump-command]))]
    (when (e/error? res)
      res)))

(m/=> eval!! [:=> [:cat e.s.server/?Writer string?] any?])
(defn eval!!
  [writer s]
  (async/<!! (call writer "elin#internal#eval" [s])))

(m/=> execute! [:=> [:cat e.s.server/?Writer string?] e.schema/?ManyToManyChannel])
(defn execute!
  [writer cmd]
  (call writer "elin#internal#execute" [cmd]))

(m/=> execute!! [:=> [:cat e.s.server/?Writer string?] any?])
(defn execute!!
  [writer cmd]
  (async/<!! (execute! writer cmd)))

(m/=> get-variable!! [:=> [:cat e.s.server/?Writer string?] any?])
(defn get-variable!!
  [writer var-name]
  (eval!! writer (format "exists('%s') ? %s : v:null" var-name var-name)))

(m/=> set-variable! [:=> [:cat e.s.server/?Writer string? any?] e.schema/?ManyToManyChannel])
(defn set-variable!
  [writer var-name value]
  (let [value' (cond
                 (string? value) (str "'" value "'")
                 (true? value) "v:true"
                 (false? value) "v:false"
                 :else value)]
    (execute! writer (format "let %s = %s" var-name value'))))

(m/=> set-variable!! [:=> [:cat e.s.server/?Writer string? any?] :nil])
(defn set-variable!!
  [writer var-name value]
  (async/<!! (set-variable! writer var-name value)))
