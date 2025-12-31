(ns elin.schema.config
  (:require
   [malli.util :as mu]))

(def ^:private ?Env
  [:map
   [:cwd string?]])

(def ^:private ?ConfigMap
  [:map-of qualified-symbol? map?])

(def ^:private ?HandlerAlias
  [:map
   [:handler qualified-symbol?]
   [:config map?]])

(def ^:private ?Handler
  [:map
   [:includes {:default []} [:sequential qualified-symbol?]]
   [:excludes {:default []} [:sequential qualified-symbol?]]
   [:config-map {:default {}} ?ConfigMap]
   [:aliases {:default {}} [:map-of symbol? ?HandlerAlias]]])

(def ?InterceptorItem
  [:or
   qualified-symbol?
   [:cat qualified-symbol? [:* any?]]])

(def ^:private ?Interceptor
  [:map
   [:includes {:default []} [:sequential ?InterceptorItem]]
   [:excludes {:default []} [:sequential ?InterceptorItem]]
   [:config-map {:default {}} ?ConfigMap]])

(def ^:private ?LogLevel
  [:enum :debug :info :warning :error])

(def ^:private ?Log
  [:map
   [:min-level {:default :info} ?LogLevel]
   [:appenders {:optional true} map?]])

(def ^:private ?Server
  [:map
   [:host string?]
   [:port int?]
   [:entrypoints map?]])

(def ?Config
  [:map
   [:env ?Env]
   [:handler {:default {}} ?Handler]
   [:interceptor {:default {}} ?Interceptor]
   [:log {:default {}} ?Log]
   [:server ?Server]])

(def ?ConfigLinter
  (let [top-schema-keys (->>
                          (mu/subschemas ?Config)
                          (filter #(= 1 (count (:path %))))
                          (map (comp first :path)))]
    (reduce
      (fn [accm k]
        (mu/update accm k mu/closed-schema))
      ?Config top-schema-keys)))
