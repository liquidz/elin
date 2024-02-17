(ns elin.schema.interceptor
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.schema.handler :as e.s.handler]
   [elin.schema.nrepl :as e.s.nrepl]
   [malli.util :as m.util]))

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
  (-> [:map
       [:output e.s.nrepl/?Output]]
      (m.util/merge e.s.handler/?Components)))

(def ?ConnectContext
  (-> [:map
       ;; ENTER
       [:hostname [:maybe string?]]
       [:port [:maybe int?]]
       ;; LEAVE
       [:client {:optional true} any?]]
      (m.util/merge e.s.handler/?Components)))

(def ?NreplContext
  (-> [:map
       ;; ENTER
       [:request e.s.nrepl/?Message]
       ;; LEAVE
       [:response {:optional true} any?]]
      (m.util/merge
       (m.util/dissoc e.s.handler/?Components
                      :component/nrepl))))

(def ?AutocmdContext
  (-> [:map
       [:autocmd-type [:enum
                       "BufRead"
                       "BufNewFile"
                       "BufEnter"
                       "BufWritePost"
                       "VimLeave"]]]
      (m.util/merge e.s.handler/?Components)))
