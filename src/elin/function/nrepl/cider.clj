(ns elin.function.nrepl.cider
  (:require
   [clojure.core.async :as async]
   [elin.constant.nrepl :as e.c.nrepl]
   [elin.error :as e]
   [elin.function.nrepl :as e.f.nrepl]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema :as e.schema]
   [elin.schema.component :as e.s.component]
   [elin.schema.nrepl :as e.s.nrepl]
   [elin.util.nrepl :as e.u.nrepl]
   [malli.core :as m]))

;; TODO If complete op is not supported, fallback to completions op.
(defn complete!!
  [nrepl ns-str prefix]
  (e/-> (e.p.nrepl/request nrepl {:op e.c.nrepl/complete-op
                                  :prefix prefix
                                  :ns ns-str
                                  :extra-metadata ["arglists" "doc"]})
        (async/<!!)
        (e.u.nrepl/merge-messages)
        (:completions)))

(m/=> info!! [:=> [:cat e.s.component/?Nrepl string? string?] (e.schema/error-or e.s.nrepl/?Lookup)])
(defn info!!
  "If info op is not supported, fallback to lookup op."
  [nrepl ns-str sym-str]
  (if-not (e.p.nrepl/supported-op? nrepl e.c.nrepl/info-op)
    ;; Fallback to lookup op
    (e.f.nrepl/lookup!! nrepl ns-str sym-str)
    (e/let [res (e/-> (e.p.nrepl/request nrepl {:op e.c.nrepl/info-op
                                                :ns ns-str
                                                :sym sym-str})
                      (async/<!!)
                      (e.u.nrepl/merge-messages))]
      (if (or (e.u.nrepl/has-status? res "no-info")
              (= [] (:ns res) (:name res)))
        (e/not-found {:message (format "Not found: %s/%s" ns-str sym-str)})
        (merge {:column 1 :line 1}
               res)))))

(m/=> ns-path!! [:=> [:cat e.s.component/?Nrepl string?] (e.schema/error-or [:maybe string?])])
(defn ns-path!!
  [nrepl ns-str]
  (e/let [resp (e/-> (e.p.nrepl/request nrepl {:op e.c.nrepl/ns-path-op
                                               :ns ns-str})
                     (async/<!!)
                     (e.u.nrepl/merge-messages))]
    (or (:url resp)
        (:path resp))))

(defn test-var-query!!
  [nrepl var-query]
  (e/-> (e.p.nrepl/request nrepl {:op e.c.nrepl/test-var-query-op
                                  :var-query var-query})
        (async/<!!)
        (e.u.nrepl/merge-messages)))

(m/=> reload!! [:=> [:cat e.s.component/?Nrepl] (e.schema/error-or e.s.nrepl/?Message)])
(defn reload!!
  [nrepl]
  (e/-> (e.p.nrepl/request nrepl {:op e.c.nrepl/reload-op})
        (async/<!!)
        (e.u.nrepl/merge-messages)))

(m/=> reload-all!! [:=> [:cat e.s.component/?Nrepl] (e.schema/error-or e.s.nrepl/?Message)])
(defn reload-all!!
  [nrepl]
  (e/-> (e.p.nrepl/request nrepl {:op e.c.nrepl/reload-all-op})
        (async/<!!)
        (e.u.nrepl/merge-messages)))

(defn undef!!
  [nrepl ns-str sym-str]
  (e/-> (e.p.nrepl/request nrepl {:op e.c.nrepl/undef-op
                                  :ns ns-str
                                  :sym sym-str})
        (async/<!!)
        (e.u.nrepl/merge-messages)))

(defn undef-all!!
  [nrepl ns-str]
  (e/-> (e.p.nrepl/request nrepl {:op e.c.nrepl/undef-all-op
                                  :ns ns-str})
        (async/<!!)
        (e.u.nrepl/merge-messages)))

(defn init-debugger
  "This operation does not return response immediately."
  [nrepl]
  (e.p.nrepl/notify nrepl {:op e.c.nrepl/init-debugger-op
                           ;; FIXME configuarable
                           :print-length 10
                           :print-level 10}))

(defn debug-input
  "This operation does not return response immediately."
  [nrepl key-str input-str]
  (e.p.nrepl/notify nrepl {:op e.c.nrepl/debug-input-op
                           :key key-str
                           :input input-str}))

(defn log-frameworks
  [nrepl]
  (e.p.nrepl/notify nrepl {:op e.c.nrepl/log-frameworks}))

(defn log-add-appender
  [nrepl {:keys [framework appender filters size threshold]}]
  (e.p.nrepl/notify nrepl {:op e.c.nrepl/log-add-appender
                           :framework framework
                           :appender appender
                           :filters filters
                           :size size
                           :threshold threshold}))

(defn log-clear-appender
  [nrepl {:keys [framework appender]}]
  (e.p.nrepl/notify nrepl {:op e.c.nrepl/log-clear-appender
                           :framework framework
                           :appender appender}))

(defn log-remove-appender!
  [nrepl {:keys [framework appender]}]
  (e.p.nrepl/request nrepl {:op e.c.nrepl/log-remove-appender
                            :framework framework
                            :appender appender}))

(defn log-search!
  [nrepl {:keys [framework appender]}]
  (e.p.nrepl/request nrepl {:op e.c.nrepl/log-search
                            :framework framework
                            :appender appender}))
