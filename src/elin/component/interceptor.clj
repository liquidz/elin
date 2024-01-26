(ns elin.component.interceptor
  (:require
   [com.stuartsierra.component :as component]
   [elin.constant.kind :as e.c.kind]
   [elin.interceptor.connect :as e.i.connect]
   [elin.interceptor.nrepl :as e.i.nrepl]
   [elin.log :as e.log]
   [elin.protocol.interceptor :as e.p.interceptor]
   [exoscale.interceptor :as interceptor]
   [msgpack.clojure-extensions]))

(def ^:private default-manager
  {e.c.kind/connect [e.i.connect/port-auto-detecting-interceptor
                     e.i.connect/output-channel-interceptor]})
   ;; e.c.kind/evaluate [e.i.nrepl/eval-ns-interceptor]})

(def ^:private dev-manager
  {e.c.kind/nrepl [e.i.nrepl/debug-interceptor]})

(defrecord Interceptor
  [manager]
  component/Lifecycle
  (start [this]
    (e.log/debug "Interceptor component: Started")
    this)
  (stop [this]
    (e.log/info "Interceptor component: Stopped")
    (dissoc this :manager))

  e.p.interceptor/IInterceptor
  (add-interceptor! [_ kind interceptor]
    (swap! manager update kind #(conj (or % []) interceptor)))
  (remove-interceptor! [_ interceptor]
    (swap! manager update-vals (fn [vs] (vec (remove #(= % interceptor) vs)))))
  (remove-interceptor! [_ kind interceptor]
    (swap! manager update kind (fn [vs] (vec (remove #(= % interceptor) vs)))))
  (execute [_ kind context]
    (->> (or (get @manager kind) [])
         (interceptor/execute context)))
  (execute [_ kind context terminator]
    (let [interceptors (or (get @manager kind) [])
          terminator' {:name ::terminator
                       :enter terminator}]
      (interceptor/execute context (concat interceptors [terminator'])))))

(defn new-interceptor
  [{:as config :keys [develop?]}]
  (let [default-manager' (merge default-manager
                                (when develop? dev-manager))
        initial-manager (->> (get-in config [:interceptor :manager])
                             (reduce-kv assoc default-manager'))]
    (map->Interceptor {:manager (atom initial-manager)})))
