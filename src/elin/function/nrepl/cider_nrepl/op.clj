(ns elin.function.nrepl.cider-nrepl.op
  (:require
   [clojure.core.async :as async]
   [elin.error :as e]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.util.nrepl :as e.u.nrepl]))

(defn complete!!
  [nrepl ns-str prefix]
  (e/-> (e.p.nrepl/request nrepl {:op "complete"
                                  :prefix prefix
                                  :ns ns-str
                                  :extra-metadata ["arglists" "doc"]})
        (async/<!!)
        (e.u.nrepl/merge-messages)
        (:completions)))
