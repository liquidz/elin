(ns elin.function.nrepl.refactor
  (:require
   [clojure.core.async :as async]
   [clojure.edn :as edn]
   [elin.constant.nrepl :as e.c.nrepl]
   [elin.error :as e]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema.component :as e.s.component]
   [elin.util.nrepl :as e.u.nrepl]
   [malli.core :as m]))

(m/=> resolve-missing!! [:=> [:cat e.s.component/?Nrepl string?] [:sequential [:map [:name symbol?] [:type keyword?]]]])
(defn resolve-missing!!
  [nrepl sym-str]
  (e/-> (e.p.nrepl/request nrepl {:op e.c.nrepl/resolve-missing-op
                                  :symbol sym-str})
        (async/<!!)
        (e.u.nrepl/merge-messages)
        (:candidates)
        (edn/read-string)))
