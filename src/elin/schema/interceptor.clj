(ns elin.schema.interceptor
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.schema.handler :as e.s.handler]
   [elin.schema.nrepl :as e.s.nrepl]
   [malli.util :as m.util]))

(def ?Kind
  [:enum
   e.c.interceptor/all
   e.c.interceptor/autocmd
   e.c.interceptor/connect
   e.c.interceptor/disconnect
   e.c.interceptor/evaluate
   e.c.interceptor/handler
   e.c.interceptor/nrepl
   e.c.interceptor/raw-nrepl
   e.c.interceptor/output
   e.c.interceptor/test
   e.c.interceptor/test-result
   e.c.interceptor/quickfix
   e.c.interceptor/modify-code])

(def ?Interceptor
  [:map
   [:name qualified-symbol?]
   [:kind ?Kind]
   [:optional {:optional true} boolean?]
   [:params {:optional true} sequential?]
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

(def ?DisconnectContext
  (-> [:map
       ;; ENTER
       [:hostname [:maybe string?]]
       [:port [:maybe int?]]]
      (m.util/merge e.s.handler/?Components)))

(def ?NreplContext
  (-> [:map
       ;; ENTER
       [:request e.s.nrepl/?Message]
       ;; LEAVE
       [:response {:optional true} any?]]
      (m.util/merge e.s.handler/?Components)))

(def ?RawNreplContext
  (-> [:map
       [:message e.s.nrepl/?Message]]
      (m.util/merge e.s.handler/?Components)))

(def ?AutocmdContext
  (-> [:map
       [:autocmd-type [:enum
                       "BufEnter"
                       "BufNewFile"
                       "BufRead"
                       "BufWritePost"
                       "BufWritePre"
                       "CursorMovedI"
                       "VimLeave"]]]
      (m.util/merge e.s.handler/?Components)))

(def ?TestContext
  (-> [:map
       ;; ENTER
       [:ns string?]
       [:vars [:sequential string?]]
       ;; LEAVE
       [:response {:optional true} map?]]
      (m.util/merge e.s.handler/?Components)))

(def ?TestResultContext
  (-> [:map
       ;; ENTER
       [:passed [:maybe [:sequential e.s.nrepl/?TestResult]]]
       [:failed [:maybe [:sequential e.s.nrepl/?TestResult]]]]
      (m.util/merge e.s.nrepl/?TestSummary)
      (m.util/merge ?TestContext)))

(def ?EvaluateContext
  (-> [:map
       ;; ENTER
       [:code string?]
       [:options map?]
       ;; LEAVE
       [:response {:optional true} map?]]
      (m.util/merge e.s.handler/?Components)))

(def ?QuickfixContext
  (-> [:map
       ;; ENTER
       [:type keyword?]
       [:list sequential?]]
      (m.util/merge e.s.handler/?Components)))

(def ?ModifyCodeContext
  (-> [:map
       ;; ENTER
       [:code string?]
       ;; LEAVE
       [:result {:optional true} boolean?]]
      (m.util/merge e.s.handler/?Components)))
