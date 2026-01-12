(ns elin.util.interceptor
  (:require
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema.interceptor :as e.s.interceptor]
   [exoscale.interceptor :as-alias interceptor]
   [malli.core :as m]))

(m/=> self [:=> [:cat map?] [:maybe e.s.interceptor/?Interceptor]])
(defn self [context]
  (some-> context
          (get ::interceptor/stack)
          (first)))

(defn config
  [context interceptor-var]
  (get-in context [:component/interceptor :config-map (symbol interceptor-var)]))

(defn connected?
  [{:component/keys [nrepl]}]
  (not (e.p.nrepl/disconnected? nrepl)))

(defn override-session
  [{:as context :component/keys [nrepl]} new-session]
  (let [overrided-nrepl (e.p.nrepl/set-override-session nrepl new-session)]
    (assoc context :component/nrepl overrided-nrepl)))
