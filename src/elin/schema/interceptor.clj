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
   e.c.interceptor/output
   e.c.interceptor/autocmd])

(def ?Interceptor
  [:map
   [:name qualified-keyword?]
   [:kind ?Kind]
   [:enter {:optional true} fn?]
   [:leave {:optional true} fn?]])

(def ?HandlerContext
  e.s.handler/?Elin)

(def ?OutputContext
  [:map
   [:host e.s.server/?Host]
   [:output e.s.nrepl/?Output]])

(def ?ConnectContext
  [:map
   [:elin e.s.handler/?Elin]
   [:hostname [:maybe string?]]
   [:port [:maybe int?]]])

(def ?NreplContext
  [:map
   [:request e.s.nrepl/?Message]
   [:host e.s.server/?Host]])

(def ?AutocmdContext
  [:map
   [:elin e.s.handler/?Elin]
   [:autocmd-type [:enum
                   "BufRead"
                   "BufNewFile"
                   "BufEnter"
                   "BufWritePost"
                   "VimLeave"]]])
