(ns elin.function.core.namespace
  (:require
   [elin.function.clj-kondo :as e.f.clj-kondo]))

(defn get-namespaces
  [{:component/keys [clj-kondo]}]
  (->> (e.f.clj-kondo/namespace-symbols clj-kondo)
       (map str)))

;; TODO use :fachvorite-ns-aliases
(defn most-used-namespace-alias
  [{:component/keys [clj-kondo]} ns-sym]
  (e.f.clj-kondo/most-used-namespace-alias clj-kondo ns-sym))
