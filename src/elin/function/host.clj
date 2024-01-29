(ns elin.function.host
  (:refer-clojure :exclude [eval])
  (:require
   [elin.schema.host :as e.s.host]
   [elin.schema.server :as e.s.server]
   [elin.util.function :as e.u.function]
   [malli.core :as m]))

(m/=> get-current-working-directory [:=> [:cat e.s.server/?Writer [:* any?]] string?])
(defn get-current-working-directory
  [writer & extra-params]
  (let [params (or extra-params [])]
    (e.u.function/call-function writer "getcwd" params)))

(m/=> get-cursor-position [:=> [:cat e.s.server/?Writer [:* any?]] e.s.host/?Position])
(defn get-cursor-position
  [writer & extra-params]
  (let [params (or extra-params [])
        [bufnum lnum col off curswant] (e.u.function/call-function writer "getcurpos" params)]
    {:bufname bufnum
     :lnum lnum
     :col col
     :off off
     :curswant curswant}))

(m/=> get-full-path [:=> [:cat e.s.server/?Writer] string?])
(defn get-full-path
  [writer]
  (e.u.function/call-function writer "expand" ["%:p"]))

(m/=> jump [:=> [:cat e.s.server/?Writer string? int? int? [:* any?]] :nil])
(defn jump
  [writer path lnum col & [jump-command]]
  (let [jump-command (or jump-command "edit")]
    (e.u.function/call-function writer "elin#internal#jump" [path lnum col jump-command])
    nil))

(m/=> eval [:=> [:cat e.s.server/?Writer string?] any?])
(defn eval
  [writer s]
  (e.u.function/call-function writer "elin#internal#eval" [s]))

(m/=> execute [:=> [:cat e.s.server/?Writer string?] any?])
(defn execute
  [writer cmd]
  (e.u.function/call-function writer "elin#internal#execute" [cmd]))

(m/=> get-variable [:=> [:cat e.s.server/?Writer string?] any?])
(defn get-variable
  [writer var-name]
  (eval writer (format "exists('%s') ? %s : v:null" var-name var-name)))

(m/=> set-variable [:=> [:cat e.s.server/?Writer string? any?] any?])
(defn set-variable
  [writer var-name value]
  (let [value' (cond
                 (string? value) (str "'" value "'")
                 (true? value) "v:true"
                 (false? value) "v:false"
                 :else value)]
    (execute writer (format "let %s = %s" var-name value'))))
