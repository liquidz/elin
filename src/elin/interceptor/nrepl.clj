(ns elin.interceptor.nrepl
  (:require
   [clojure.string :as str]
   [elin.constant.interceptor :as e.c.interceptor]))

(def eval-ns-interceptor
  {:name ::eval-ns-interceptor
   :kind e.c.interceptor/evaluate
   :enter (fn [{:as ctx :keys [code]}]
            (if (str/starts-with? code "(ns")
              (update ctx :options dissoc :ns)
              ctx))})

;; (def normalize-path-interceptor
;;   {:name ::normalize-path-interceptor
;;    :enter (fn [{:as}])})
;;
