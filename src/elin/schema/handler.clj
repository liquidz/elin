(ns elin.schema.handler
  (:require
   [elin.schema.server :as e.s.server]
   [malli.util :as m.util]))

(def ?ArgMap
  [:map
   [:message e.s.server/?Message]
   [:writer e.s.server/?Writer]])

(def ?Components
  [:map
   [:component/nrepl any?]
   [:component/interceptor any?]])

(def ?Elin
  (m.util/merge
   ?ArgMap
   ?Components))
