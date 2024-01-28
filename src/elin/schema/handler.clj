(ns elin.schema.handler
  (:require
   [elin.schema.server :as e.s.server]
   [malli.util :as m.util]))

(def ?Components
  [:map
   [:component/nrepl any?]
   [:component/interceptor any?]
   [:component/writer e.s.server/?Writer]])

(def ?Elin
  (m.util/merge
   [:map
    [:message e.s.server/?Message]]
   ?Components))
