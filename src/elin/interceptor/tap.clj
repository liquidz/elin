(ns elin.interceptor.tap
  (:require
   [clojure.string :as str]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.function.evaluate :as e.f.evaluate]
   [elin.util.interceptor :as e.u.interceptor]
   [exoscale.interceptor :as ix]))

(def ^:private ns-sym
  (gensym "elin.interceptor.tap."))

(def ^:private tap-fn-sym
  'catch-tapped!)

(def ^:private tapped-atom-sym
  (gensym "elin-tapped"))

(defn- convert-to-edn-compliant-data-fn-code
  "Return a code for converting data to EDN-compliant data
  cf. https://github.com/edn-format/edn?tab=readme-ov-file#built-in-elements"
  [max-depth]
  `(fn datafy#
     ([x#]
      (datafy# 0 x#))
     ([depth# x#]
      (if (> depth# ~max-depth)
        '...
        (clojure.walk/prewalk
          (fn [v#]
            (cond
              ;; java.time.Instant
              (instance? java.time.Instant v#)
              (clojure.instant/read-instant-date (str v#))

              ;; no conversion
              (or
                (nil? v#)
                (boolean? v#)
                (char? v#)
                (symbol? v#)
                (keyword? v#)
                (number? v#)
                (inst? v#)
                (uuid? v#))
              v#
              ;; vectors
              (vector? v#)
              (mapv (partial datafy# (inc depth#)) v#)
              ;; maps
              (map? v#)
              (update-vals v# (partial datafy# (inc depth#)))
              ;; sets
              (set? v#)
              (set (map (partial datafy# (inc depth#)) v#))
              ;; lists
              (sequential? v#)
              (map (partial datafy# (inc depth#)) v#)

              :else
              (try
                (let [datafied# (clojure.core.protocols/datafy v#)]
                  (if (not= datafied# v#)
                    datafied#
                    (str v#)))
                (catch Exception _# (str v#)))))
          x#)))))

(defn- initialize-code
  [{:keys [http-server-port max-store-size max-datafy-depth]}]
  (str
    `(do (in-ns '~ns-sym)
         (refer-clojure)

         (def convert-to-edn-compliant-data#
           ~(convert-to-edn-compliant-data-fn-code max-datafy-depth))

         (defn tap-handler-request#
           [value#]
           (try
             (let [uri# (java.net.URI/create
                          (str  "http://localhost:" ~http-server-port "/api/v1"))
                   body# (format "{\"method\": \"elin.handler.tap/tapped\", \"params\": [%s]}"
                                 (pr-str (pr-str value#)))
                   client# (java.net.http.HttpClient/newHttpClient)
                   req# (-> (java.net.http.HttpRequest/newBuilder)
                            (.uri uri#)
                            (.header "Content-Type" "application/json")
                            (.POST (java.net.http.HttpRequest$BodyPublishers/ofString body#))
                            (.build))]

               (.send client# req# (java.net.http.HttpResponse$BodyHandlers/ofString)))
             (catch Exception _# nil)))

         (def ~tapped-atom-sym (atom []))

         (try
           (remove-tap @(resolve '~tap-fn-sym))
           (catch Exception _# nil))

         (defn ~tap-fn-sym
           [original-value#]
           (let [display-value# (convert-to-edn-compliant-data# original-value#)]
             (tap-handler-request# display-value#)
             (when (<= ~max-store-size (count (deref ~tapped-atom-sym)))
               (swap! ~tapped-atom-sym (fn [v#] (drop-last 1 v#))))
             (swap! ~tapped-atom-sym (fn [v#]
                                       (cons {:id (str (random-uuid))
                                              :time (str (java.time.LocalDateTime/now))
                                              :value original-value#}
                                             v#)))))
         (try
           (add-tap ~tap-fn-sym)
           (catch Exception _# nil)))))

(def initialize
  "Defines a tap function and adds it.

  .Configuration
  [%autowidth.stretch]
  |===
  | key | type | description

  | max-store-size | integer | The maximum number of values to store when tapped. Defautl value is `10`.
  | max-datafy-depth | integer | The maximum depth to datafy tapped value. Datafied value is used for intercepting tapped value. Defautl value is `5`.
  |==="
  {:kind e.c.interceptor/connect
   :leave (-> (fn [{:as ctx :component/keys [handler]}]
                (let [config (e.u.interceptor/config ctx #'initialize)
                      http-server-port (get-in handler [:initialize :export "g:elin_http_server_port"])
                      code (initialize-code {:max-store-size (:max-store-size config)
                                             :max-datafy-depth (:max-datafy-depth config)
                                             :http-server-port http-server-port})]
                  (e.f.evaluate/evaluate-code ctx code)))
              (ix/discard))})

(def access-tapped-values
  "Enables access to tapped values from a specific symbol.

  .Configuration
  [%autowidth.stretch]
  |===
  | key | type | description

  | var-name | string | The var name to access tapped values. Default value is `\\*tapped*`.
  |==="
  (let [get-target-name #(-> (e.u.interceptor/config % #'access-tapped-values)
                             (:var-name))]
    {:kind e.c.interceptor/evaluate
     :enter (-> (fn [ctx]
                  (let [target-name (get-target-name ctx)
                        var-code (str `(deref ~(symbol (name ns-sym)
                                                       (name tapped-atom-sym))))]
                    (update ctx :code #(str/replace % target-name var-code))))
                (ix/when #(str/includes? (:code %) (get-target-name %))))}))
