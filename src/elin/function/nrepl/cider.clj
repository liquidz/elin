(ns elin.function.nrepl.cider
  (:require
   [clojure.core.async :as async]
   [elin.error :as e]
   [elin.function.nrepl :as e.f.nrepl]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema :as e.schema]
   [elin.schema.component :as e.s.component]
   [elin.schema.nrepl.op :as e.s.n.op]
   [elin.util.nrepl :as e.u.nrepl]
   [malli.core :as m]))

(def info-op "info")
(def test-var-query-op "test-var-query")

;; TODO If complete op is not supported, fallback to completions op.
(defn complete!!
  [nrepl ns-str prefix]
  (e/-> (e.p.nrepl/request nrepl {:op "complete"
                                  :prefix prefix
                                  :ns ns-str
                                  :extra-metadata ["arglists" "doc"]})
        (async/<!!)
        (e.u.nrepl/merge-messages)
        (:completions)))

(m/=> info!! [:=> [:cat e.s.component/?Nrepl string? string?] (e.schema/error-or e.s.n.op/?Lookup)])
(defn info!!
  "If info op is not supported, fallback to lookup op."
  [nrepl ns-str sym-str]
  (if-not (e.p.nrepl/supported-op? nrepl info-op)
    ;; Fallback to lookup op
    (e.f.nrepl/lookup!! nrepl ns-str sym-str)
    (e/let [res (e/-> (e.p.nrepl/request nrepl {:op info-op
                                                :ns ns-str
                                                :sym sym-str})
                      (async/<!!)
                      (e.u.nrepl/merge-messages))]
      (if (e.u.nrepl/has-status? res "no-info")
        (e/not-found {:message (format "Not found: %s/%s" ns-str sym-str)})
        res))))

(defn test-var-query!!
  [nrepl var-query]
  (e/-> (e.p.nrepl/request nrepl {:op test-var-query-op
                                  :var-query var-query})
        (async/<!!)
        (e.u.nrepl/merge-messages)))
