(ns elin.function.core.namespace
  (:require
   [clojure.edn :as edn]
   [elin.error :as e]
   [elin.function.clj-kondo :as e.f.clj-kondo]
   [elin.function.nrepl :as e.f.nrepl]))

(defn get-namespaces
  [{:component/keys [clj-kondo nrepl]}]
  (let [ns-list (e/-> (e.f.nrepl/eval!! nrepl (str '(map (comp str ns-name) (all-ns))))
                      (:value)
                      (edn/read-string))
        ns-list (if (e/error? ns-list)
                  []
                  ns-list)]
    (->> (e.f.clj-kondo/namespace-symbols clj-kondo)
         (map str)
         (concat ns-list)
         (distinct)
         (sort))))

;; TODO use :fachvorite-ns-aliases
(defn most-used-namespace-alias
  [{:component/keys [clj-kondo]} ns-sym]
  (e.f.clj-kondo/most-used-namespace-alias clj-kondo ns-sym))
