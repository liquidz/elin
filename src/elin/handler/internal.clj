(ns elin.handler.internal
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.function.vim :as e.f.vim]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.schema.handler :as e.s.handler]
   [malli.core :as m]))

(m/=> initialize [:=> [:cat e.s.handler/?Elin] any?])
(defn initialize
  [{:component/keys [writer]}]
  (e.f.vim/notify writer "elin#internal#buffer#info#ready" [])
  true)

(m/=> intercept [:=> [:cat e.s.handler/?Elin] any?])
(defn intercept
  [{:as elin :component/keys [interceptor] :keys [message]}]
  (let [autocmd-type (first (:params message))]
    (->> {:elin elin :autocmd-type autocmd-type}
         (e.p.interceptor/execute interceptor e.c.interceptor/autocmd))
    true))
