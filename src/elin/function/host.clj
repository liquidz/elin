(ns elin.function.host
  (:require
   [elin.schema.host :as e.s.host]
   [elin.schema.server :as e.s.server]
   [elin.util.function :as e.u.function]
   [malli.core :as m]))

(m/=> get-current-working-directory [:=> [:cat e.s.server/?Message [:* any?]] string?])
(defn get-current-working-directory
  [writer & extra-params]
  (let [params (or extra-params [])]
    (e.u.function/call-function writer "getcwd" params)))

(m/=> get-cursor-position [:=> [:cat e.s.server/?Message [:* any?]] e.s.host/?Position])
(defn get-cursor-position
  [writer & extra-params]
  (let [params (or extra-params [])
        [bufnum lnum col off curswant] (e.u.function/call-function writer "getcurpos" params)]
    {:bufname bufnum
     :lnum lnum
     :col col
     :off off
     :curswant curswant}))

(defn get-full-path
  [writer]
  (e.u.function/call-function writer "expand" ["%:p"]))
