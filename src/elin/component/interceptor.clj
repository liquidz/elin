(ns elin.component.interceptor
  (:require
   [com.stuartsierra.component :as component]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.interceptor.autocmd]
   [elin.interceptor.connect]
   ;; [elin.interceptor.debug :as e.i.debug]
   [elin.interceptor.nrepl]
   [elin.interceptor.output]
   [elin.log :as e.log]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.schema.interceptor :as e.s.interceptor]
   [exoscale.interceptor :as interceptor]
   [malli.core :as m]
   [msgpack.clojure-extensions]))

(def ^:private default-interceptors
  '[elin.interceptor.connect/port-auto-detecting-interceptor
    elin.interceptor.connect/output-channel-interceptor
    elin.interceptor.connect/connected-interceptor
    elin.interceptor.output/print-output-interceptor
    elin.interceptor.nrepl/eval-ns-interceptor
    elin.interceptor.nrepl/normalize-path-interceptor
    elin.interceptor.autocmd/ns-create-interceptor])

;; (def ^:private dev-interceptors
;;   [e.i.debug/interceptor-context-checking-interceptor
;;    e.i.debug/nrepl-debug-interceptor])

(defn- resolve-interceptor [lazy-writer sym]
  (try
    (deref (requiring-resolve sym))
    (catch Exception ex
      (e.log/warning lazy-writer "Failed to resolve interceptor" {:symbol sym :ex ex})
      nil)))

(defn- valid-interceptor?
  [x]
  (m/validate e.s.interceptor/?Interceptor x))

(defrecord Interceptor
  [lazy-writer     ; LazyWriter component
   plugin          ; Plugin component
   excludes
   includes
   manager]
  component/Lifecycle
  (start [this]
    (let [exclude-set (set excludes)
          grouped-interceptors (->> (concat default-interceptors
                                            (or includes [])
                                            (or (get-in plugin [:loaded-plugin :interceptors]) []))
                                    (remove #(contains? exclude-set %))
                                    (keep #(resolve-interceptor lazy-writer %))
                                    (group-by valid-interceptor?))
          interceptor-map (group-by :kind (get grouped-interceptors true))]
      (when-let [invalid-interceptors (seq (get grouped-interceptors false))]
        (e.log/warning lazy-writer "Invalid interceptors:" invalid-interceptors))
      (e.log/debug "Interceptor component: Started")
      (assoc this :manager (atom interceptor-map))))
  (stop [this]
    (e.log/info "Interceptor component: Stopped")
    (dissoc this :manager))

  e.p.interceptor/IInterceptor
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

(defn new-interceptor
  [config]
  (map->Interceptor (or (:interceptor config) {})))
