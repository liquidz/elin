(ns elin.schema.config)

(def ^:private ?Env
  [:map
   [:cwd string?]])

(def ^:private ?Handler
  [:map
   [:includes {:default []} [:sequential qualified-symbol?]]
   [:excludes {:default []} [:sequential qualified-symbol?]]])

(def ?InterceptorItem
  [:or
   qualified-symbol?
   [:cat qualified-symbol? [:* any?]]])

(def ^:private ?Interceptor
  [:map
   [:includes {:default []} [:sequential ?InterceptorItem]]
   [:excludes {:default []} [:sequential ?InterceptorItem]]])

(def ^:private ?LogLevel
  [:enum :debug :info :warning :error])

(def ^:private ?Log
  [:map
   [:level {:default :info} ?LogLevel]])

(def ^:private ?Server
  [:map
   [:host string?]
   [:port int?]])

(def ?Config
  [:map
   [:env ?Env]
   [:handler {:default {}} ?Handler]
   [:interceptor {:default {}} ?Interceptor]
   [:log {:default {}} ?Log]
   [:server ?Server]])
