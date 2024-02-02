(ns elin.schema.config)

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
   [:log {:default {}} ?Log]
   [:server ?Server]])
