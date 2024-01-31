(ns elin.function.vim
  (:require
   [clojure.core.async :as async]
   [elin.error :as e.error]
   [elin.schema :as e.schema]
   [elin.schema.server :as e.s.server]
   [elin.schema.vim :as e.s.vim]
   [elin.util.function :as e.u.function]
   [malli.core :as m]))

(m/=> call!! [:=> [:cat e.s.server/?Writer string? [:sequential any?]] any?])
(defn call!! [writer function-name params]
  (async/<!! (e.u.function/call-function writer function-name params)))

(m/=> get-current-working-directory!! [:=> [:cat e.s.server/?Writer [:* any?]] (e.schema/error-or string?)])
(defn get-current-working-directory!!
  [writer & extra-params]
  (let [params (or extra-params [])]
    (async/<!! (e.u.function/call-function writer "getcwd" params))))

(m/=> get-cursor-position!! [:=> [:cat e.s.server/?Writer [:* any?]] (e.schema/error-or e.s.vim/?Position)])
(defn get-cursor-position!!
  [writer & extra-params]
  (e.error/let [params (or extra-params [])
                [bufnum lnum col off curswant] (async/<!! (e.u.function/call-function writer "getcurpos" params))]
    {:bufname bufnum
     :lnum lnum
     :col col
     :off off
     :curswant curswant}))

(m/=> get-full-path!! [:=> [:cat e.s.server/?Writer] (e.schema/error-or string?)])
(defn get-full-path!!
  [writer]
  (async/<!! (e.u.function/call-function writer "expand" ["%:p"])))

(m/=> jump!! [:=> [:cat e.s.server/?Writer string? int? int? [:* any?]] [:maybe e.schema/?Error]])
(defn jump!!
  [writer path lnum col & [jump-command]]
  (let [jump-command (or jump-command "edit")
        res (async/<!! (e.u.function/call-function writer "elin#internal#jump" [path lnum col jump-command]))]
    (when (e.error/error? res)
      res)))

(m/=> eval!! [:=> [:cat e.s.server/?Writer string?] any?])
(defn eval!!
  [writer s]
  (async/<!! (e.u.function/call-function writer "elin#internal#eval" [s])))

(m/=> execute!! [:=> [:cat e.s.server/?Writer string?] any?])
(defn execute!!
  [writer cmd]
  (async/<!! (e.u.function/call-function writer "elin#internal#execute" [cmd])))

(m/=> get-variable!! [:=> [:cat e.s.server/?Writer string?] any?])
(defn get-variable!!
  [writer var-name]
  (eval!! writer (format "exists('%s') ? %s : v:null" var-name var-name)))

(m/=> set-variable!! [:=> [:cat e.s.server/?Writer string? any?] :nil])
(defn set-variable!!
  [writer var-name value]
  (let [value' (cond
                 (string? value) (str "'" value "'")
                 (true? value) "v:true"
                 (false? value) "v:false"
                 :else value)]
    (execute!! writer (format "let %s = %s" var-name value'))
    nil))
