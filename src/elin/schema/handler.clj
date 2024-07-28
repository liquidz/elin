(ns elin.schema.handler
  (:require
   [elin.schema.component :as e.s.component]
   [elin.schema.server :as e.s.server]
   [malli.util :as m.util]))

(def ?Components
  [:map
   [:component/nrepl e.s.component/?Nrepl]
   [:component/interceptor e.s.component/?Interceptor]
   [:component/host e.s.component/?LazyHost]
   [:component/session-storage e.s.component/?Storage]
   [:component/clj-kondo e.s.component/?CljKondo]])

(def ?Elin
  (m.util/merge
   [:map
    [:message e.s.server/?Message]]
   ?Components))

(def ?HandlerMap
  [:map-of qualified-keyword? fn?])
