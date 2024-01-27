(ns elin.schema.interceptor
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.schema.handler :as e.s.handler]
   [elin.schema.nrepl :as e.s.nrepl]
   [elin.schema.server :as e.s.server]))

(def ?Kind
  [:enum
   e.c.interceptor/all
   e.c.interceptor/handler
   e.c.interceptor/connect
   e.c.interceptor/nrepl
   e.c.interceptor/output])

(def ?Interceptor
  [:map
   [:name keyword?]
   [:kind ?Kind]
   [:enter {:optional true} fn?]
   [:leave {:optional true} fn?]])

(def ?HandlerContext
  e.s.handler/?ArgMap)

(def ?OutputContext
  [:map
   [:writer e.s.server/?Writer]
   [:output e.s.nrepl/?Output]])

(def ?ConnectContext
  [:map
   [:elin e.s.handler/?Elin]
   [:host [:maybe string?]]
   [:port [:maybe int?]]])

(def ?NreplContext
  [:map
   [:request e.s.nrepl/?Message]
   [:writer e.s.server/?Writer]])
