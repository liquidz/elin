(ns elin.component.interceptor
  (:require
   [clojure.set :as set]
   [com.stuartsierra.component :as component]
   [elin.config :as e.config]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.interceptor.autocmd]
   [elin.interceptor.code-change]
   [elin.interceptor.connect]
   [elin.interceptor.nrepl]
   [elin.interceptor.output]
   [elin.interceptor.quickfix]
   [elin.message :as e.message]
   [elin.protocol.config :as e.p.config]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.schema.interceptor :as e.s.interceptor]
   [exoscale.interceptor :as interceptor]
   [malli.core :as m]
   [msgpack.clojure-extensions]
   [taoensso.timbre :as timbre]))

(def ^:private config-key :interceptor)
(def ^:private invalid-group ::invalid)
(def ^:private optional-group ::optional)
(def ^:private valid-group ::valid)

(defn- resolve-interceptor [lazy-host sym]
  (try
    (-> (requiring-resolve sym)
        (deref)
        (assoc :name sym))
    (catch Exception ex
      (e.message/warning lazy-host "Failed to resolve interceptor" {:symbol sym :ex ex})
      nil)))

(defn- wrap-interceptor-for-logging
  [{:as interceptor :keys [enter leave]}]
  (let [wrap (fn [timing f]
               (fn [context]
                 (try
                   (f context)
                   (catch Exception ex
                     (throw (ex-info (format "error occured on %s %s because of '%s'"
                                             timing
                                             (:name interceptor)
                                             (ex-message ex))
                                     {}
                                     ex))))))]
    (cond-> interceptor
      (fn? enter)
      (assoc :enter (wrap "entering" enter) #_(wrap-interceptor-for-logging* name "entering" enter))

      (fn? leave)
      (assoc :leave (wrap "leaving" leave) #_(wrap-interceptor-for-logging* name "leaving" leave)))))

(defn- interceptor-group
  [x]
  (cond
    (not (m/validate e.s.interceptor/?Interceptor x))
    invalid-group

    (:optional x)
    optional-group

    :else
    valid-group))

(defrecord Interceptor
  [;; COMPONENTS
   lazy-host
   plugin
   ;; CONFIGS
   includes
   excludes
   config-map
   ;; PARAMS
   name-to-symbol-dict
   interceptor-map]
  component/Lifecycle
  (start [this]
    (let [resolved-interceptors (->> (or includes [])
                                     (distinct)
                                     (keep #(when-let [i (resolve-interceptor lazy-host %)]
                                              [% i])))
          name-to-symbol-dict (->> resolved-interceptors
                                   (map (fn [[sym i]] [(:name i) sym]))
                                   (into {}))
          grouped-interceptors (->> resolved-interceptors
                                    (map (comp wrap-interceptor-for-logging second))
                                    (group-by interceptor-group))
          interceptor-map (group-by :kind (get grouped-interceptors valid-group))]
      (when-let [invalid-interceptors (seq (get grouped-interceptors invalid-group))]
        (timbre/warn "Invalid interceptors:" invalid-interceptors)
        (e.message/warning "Invalid interceptors:" invalid-interceptors))
      (timbre/debug "Interceptor component: Started")
      (assoc this
             :name-to-symbol-dict name-to-symbol-dict
             :interceptor-map interceptor-map)))
  (stop [this]
    (timbre/debug "Interceptor component: Stopped")
    (dissoc this
            :name-to-symbol-dict
            :interceptor-map))

  e.p.interceptor/IInterceptor
  (execute [this kind context]
    (e.p.interceptor/execute this kind context identity))
  (execute [this kind context terminator]
    (let [interceptors (concat
                        (or (get interceptor-map e.c.interceptor/all) [])
                        (or (get interceptor-map kind) []))
          terminator' {:name ::terminator
                       :enter terminator}
          context' (assoc context
                          :component/interceptor this
                          :interceptor/kind kind)]
      (try
        #_(timbre/debug (format "Start to intercept %s with [%s]" kind (->> (map :name interceptors)
                                                                            (str/join ", "))))
        (interceptor/execute context' (concat interceptors [terminator']))
        (catch Exception ex
          (e.message/error lazy-host (format "Failed to intercept for %s: %s"
                                             kind
                                             (ex-message ex)))))))

  e.p.config/IConfigure
  (configure [this config]
    (let [{:as target :keys [includes excludes]} (-> (e.config/expand-config config)
                                                     (get config-key))
          exclude-set (set/union (set (or excludes []))
                                 (set (or includes [])))
          config-map' (if-let [target-config-map (:config-map target)]
                        (e.config/merge-configs config-map target-config-map)
                        config-map)
          grouped (->> (or includes [])
                       (keep #(resolve-interceptor lazy-host %))
                       (map wrap-interceptor-for-logging)
                       (group-by interceptor-group))
          include-map (group-by :kind (concat (get grouped valid-group)
                                              (get grouped optional-group)))
          interceptor-map' (update-vals interceptor-map
                                        (fn [interceptors]
                                          (remove #(contains? exclude-set (get name-to-symbol-dict (:name %)))
                                                  interceptors)))
          ;; NOTE includes should be prioritized over excludes
          interceptor-map' (reduce-kv
                            (fn [accm kind interceptors]
                              (assoc accm kind (concat (or (get accm kind) [])
                                                       interceptors)))
                            interceptor-map' include-map)]
      (assoc this
             :config-map config-map'
             :interceptor-map interceptor-map'))))

(defn new-interceptor
  [config]
  (map->Interceptor (or (get config config-key) {})))
