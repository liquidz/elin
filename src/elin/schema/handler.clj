(ns elin.schema.handler
  (:require
   [elin.schema.server :as e.s.server]
   [malli.util :as m.util]))

(def ?Components
  [:map
   [:component/nrepl any?]
   [:component/interceptor any?]
   [:component/host e.s.server/?Host]
   [:component/session-storage any?]
   [:component/clj-kondo any?]])

(def ?Elin
  (m.util/merge
   [:map
    [:message e.s.server/?Message]]
   ?Components))

(def ?HandlerMap
  [:map-of qualified-keyword? fn?])
