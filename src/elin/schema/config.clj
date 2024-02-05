(ns elin.schema.config)

(def ^:private ?Env
  [:map
   [:cwd string?]])

(def ^:private ?Handler
  [:map
   [:includes {:default []} [:sequential qualified-symbol?]]
   [:excludes {:default []} [:sequential qualified-symbol?]]])

(def ^:private ?Interceptor
  [:map
   [:includes {:default []} [:sequential qualified-symbol?]]
   [:excludes {:default []} [:sequential qualified-symbol?]]])

(def ^:private ?LogLevel
  [:enum :debug :info :warning :error])

(def ^:private ?Log
  [:map
   [:level {:default :info} ?LogLevel]])

(def ^:private ?Plugin
  [:map
   [:edn-files {:default []} [:sequential string?]]])

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
   [:plugin {:default {}} ?Plugin]
   [:server ?Server]])
