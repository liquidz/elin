(ns elin.schema.interceptor
  (:require
   [elin.schema.handler :as e.s.handler]
   [elin.schema.nrepl :as e.s.nrepl]
   [elin.schema.server :as e.s.server]))

(def ?HandlerContext
  e.s.handler/?ArgMap)

(def ?ConnectContext
  [:map
   [:elin e.s.handler/?Elin]
   [:host [:maybe string?]]
   [:port [:maybe int?]]])

(def ?NreplContext
  [:map
   [:request e.s.nrepl/?Message]
   [:writer e.s.server/?Writer]])
