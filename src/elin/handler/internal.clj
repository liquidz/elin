(ns elin.handler.internal
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.function.vim :as e.f.vim]
   [elin.log :as e.log]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.schema.handler :as e.s.handler]
   [elin.util.map :as e.u.map]
   [malli.core :as m]))

(m/=> initialize [:=> [:cat e.s.handler/?Elin] any?])
(defn initialize
  [{:component/keys [host]}]
  (e.f.vim/notify host "elin#internal#buffer#info#ready" [])
  true)

(m/=> intercept [:=> [:cat e.s.handler/?Elin] any?])
(defn intercept
  [{:as elin :component/keys [interceptor] :keys [message]}]
  (let [autocmd-type (first (:params message))
        context (-> elin
                    (e.u.map/select-keys-by-namespace :component)
                    (assoc :autocmd-type autocmd-type))]
    (e.p.interceptor/execute interceptor e.c.interceptor/autocmd context)
    true))

(defn error
  [{:component/keys [host] :keys [message]}]
  (e.log/error host (str "Unexpected error: " (pr-str (:params message))))
  true)
