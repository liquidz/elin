(ns elin.handler.internal
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.function.vim :as e.f.vim]
   [elin.log :as e.log]
   [elin.protocol.clj-kondo :as e.p.clj-kondo]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.schema.handler :as e.s.handler]
   [elin.util.map :as e.u.map]
   [malli.core :as m]))

(defn healthcheck [_] "OK")

(m/=> initialize [:=> [:cat e.s.handler/?Elin] any?])
(defn initialize
  [{:component/keys [handler host clj-kondo]}]
  (e.f.vim/notify host "elin#internal#buffer#info#ready" [])
  (e.p.clj-kondo/restore clj-kondo)
  (doseq [[export-name export-value] (or (get-in handler [:initialize :export]) {})]
    (e.log/debug (format "Exporting %s as %s" export-value export-name))
    (e.f.vim/set-variable! host export-name export-value))
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
