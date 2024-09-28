(ns elin.function.quickfix
  (:require
   [clojure.core.async :as async]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.util.map :as e.u.map]))

(defn get-quickfix-list
  [{:component/keys [host]}]
  (async/<!! (e.p.host/get-quickfix-list host)))

(defn set-quickfix-list
  [{:as elin :component/keys [interceptor]} qf-list]
  (let [context (-> elin
                    (e.u.map/select-keys-by-namespace :component)
                    (assoc :type :error
                           :list qf-list))]
    (e.p.interceptor/execute interceptor e.c.interceptor/quickfix context
                             (fn [{:as ctx :component/keys [host]}]
                               (e.p.host/set-quickfix-list host (:list ctx))
                               ctx))))

(defn set-location-list
  ([elin qf-list]
   (set-location-list elin 0 qf-list))
  ([{:as elin :component/keys [interceptor]} window-id qf-list]
   (let [context (-> elin
                     (e.u.map/select-keys-by-namespace :component)
                     (assoc :type :location
                            :window-id window-id
                            :list qf-list))]
     (e.p.interceptor/execute interceptor e.c.interceptor/quickfix context
                              (fn [{:as ctx :component/keys [host]}]
                                (e.p.host/set-location-list host (:window-id ctx) (:list ctx))
                                ctx)))))
