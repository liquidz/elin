(ns elin.interceptor.nrepl
  (:require
   [clojure.string :as str]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.nrepl.message :as e.n.message]
   [elin.util.file :as e.u.file]))

(def eval-ns-interceptor
  {:name ::eval-ns-interceptor
   :kind e.c.interceptor/nrepl
   :enter (fn [{:as ctx :keys [request]}]
            (if (not= "eval" (:op request))
              ctx
              (let [{:keys [code]} request]
                (if (str/starts-with? code "(ns")
                  (update ctx :request dissoc :ns)
                  ctx))))})

(def normalize-path-interceptor
  {:name ::normalize-path-interceptor
   :kind e.c.interceptor/nrepl
   :leave (fn [{:as ctx :keys [request response]}]
            (if (not (contains? #{"lookup" "info"} (:op request)))
              ctx
              (->> response
                   (e.n.message/update-messages :file e.u.file/normalize-path)
                   (assoc ctx :response))))})
