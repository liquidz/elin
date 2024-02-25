(ns elin.function.core
  (:require
   [elin.error :as e]
   [elin.function.clj-kondo :as e.f.clj-kondo]
   [elin.function.nrepl.cider :as e.f.n.cider]
   [elin.schema.handler :as e.s.handler]
   [elin.schema.nrepl :as e.s.nrepl]
   [malli.core :as m]
   [malli.util :as m.util]))

(def ?NreplAndCljKondo
  (m.util/select-keys e.s.handler/?Components [:component/nrepl :component/clj-kondo]))

(m/=> lookup!! [:=> [:cat ?NreplAndCljKondo string? string?] e.s.nrepl/?Lookup])
(defn lookup!!
  [{:component/keys [nrepl clj-kondo]} ns-str sym-str]
  (let [res (e.f.n.cider/info!! nrepl ns-str sym-str)]
    (if (e/error? res)
      (try
        (e.f.clj-kondo/lookup clj-kondo ns-str sym-str)
        (catch Exception e
          (e/fault {:message (pr-str e)})))
      (do
        (println "FIXME kotti???")
        res))))
