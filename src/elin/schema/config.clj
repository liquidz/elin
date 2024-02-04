(ns elin.schema.config)

(def ^:private ?Env
  [:map
   [:cwd string?]])

(def ^:private ?LogLevel
  [:enum :debug :info :warning :error])

(def ^:private ?Log
  [:map
   [:level {:default :info} ?LogLevel]])

(def ^:private ?Plugin
  [:map
   [:edn-files [:sequential string?]]])

(def ^:private ?Server
  [:map
   [:host string?]
   [:port int?]])

(def ?Config
  [:map
   [:env ?Env]
   [:log {:optional true :default {}} ?Log]
   [:plugin ?Plugin]
   [:server ?Server]])
