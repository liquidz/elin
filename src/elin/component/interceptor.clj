(ns elin.component.interceptor
  (:require
   [com.stuartsierra.component :as component]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.interceptor.autocmd :as e.i.autocmd]
   [elin.interceptor.connect :as e.i.connect]
   [elin.interceptor.debug :as e.i.debug]
   [elin.interceptor.nrepl :as e.i.nrepl]
   [elin.interceptor.output :as e.i.output]
   [elin.log :as e.log]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.schema.interceptor :as e.s.interceptor]
   [exoscale.interceptor :as interceptor]
   [malli.core :as m]
   [msgpack.clojure-extensions]))

(def ^:private default-interceptors
  [e.i.connect/port-auto-detecting-interceptor
   e.i.connect/output-channel-interceptor
   e.i.connect/connected-interceptor
   e.i.output/print-output-interceptor
   e.i.nrepl/eval-ns-interceptor
   e.i.nrepl/normalize-path-interceptor
   e.i.autocmd/ns-create-interceptor])

(def ^:private dev-interceptors
  [e.i.debug/interceptor-context-checking-interceptor
   e.i.debug/nrepl-debug-interceptor])

(defrecord Interceptor
  [lazy-writer manager]
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
  (execute [this kind context]
    (e.p.interceptor/execute this kind context identity))
  (execute [this kind context terminator]
    (let [interceptors (concat
                        (or (get @manager e.c.interceptor/all) [])
                        (or (get @manager kind) []))
          terminator' {:name ::terminator
                       :enter terminator}
          context' (assoc context
                          :elin/interceptor this
                          :elin/kind kind)]
      (try
        (interceptor/execute context' (concat interceptors [terminator']))
        (catch Exception ex
          (e.log/error "Interceptor error" ex))))))

(defn- valid-interceptor?
  [x]
  (m/validate e.s.interceptor/?Interceptor x))

(defn new-interceptor
  [{:as config :keys [develop?]}]
  (let [{interceptors true invalid false} (->> (concat default-interceptors
                                                       (when develop? dev-interceptors)
                                                       (get-in config [:interceptor :interceptors]))
                                               (group-by valid-interceptor?))
        initial-manager (group-by :kind interceptors)]
    ;; TODO writer
    (e.log/debug "Invalid interceptors:" invalid)
    (map->Interceptor {:manager (atom initial-manager)})))
