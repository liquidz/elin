(ns elin.interceptor.nrepl
  (:require
   [clojure.string :as str]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.constant.nrepl :as e.c.nrepl]
   [elin.message :as e.message]
   [elin.util.file :as e.u.file]
   [elin.util.nrepl :as e.u.nrepl]
   [exoscale.interceptor :as ix]))

(def eval-ns-interceptor
  {:name ::eval-ns-interceptor
   :kind e.c.interceptor/nrepl
   :enter (-> (fn [{:as ctx :keys [request]}]
                (let [{:keys [code]} request]
                  (if (str/starts-with? code "(ns")
                    (update ctx :request dissoc :ns)
                    ctx)))
              (ix/when #(= e.c.nrepl/eval-op (get-in % [:request :op]))))})

(def normalize-path-interceptor
  {:name ::normalize-path-interceptor
   :kind e.c.interceptor/nrepl
   :leave (fn [{:as ctx :keys [request response]}]
            (cond
              (contains? #{e.c.nrepl/lookup-op e.c.nrepl/info-op} (:op request))
              (->> response
                   (e.u.nrepl/update-messages :file e.u.file/normalize-path)
                   (assoc ctx :response))

              (contains? #{e.c.nrepl/ns-path-op} (:op request))
              (->> response
                   (e.u.nrepl/update-messages :url e.u.file/normalize-path)
                   (e.u.nrepl/update-messages :path e.u.file/normalize-path)
                   (assoc ctx :response))

              :else
              ctx))})

(def output-load-file-result-to-cmdline-interceptor
  {:name ::output-load-file-result-to-cmdline-interceptor
   :kind e.c.interceptor/nrepl
   :leave (-> (fn [{:component/keys [host] :keys [response]}]
                (let [msg (e.u.nrepl/merge-messages response)]
                  (if (e.u.nrepl/has-status? msg "eval-error")
                    (when-let [v (:err msg)]
                      (e.message/error host (str/trim (str v))))
                    (e.message/info host "Required."))))
              (ix/when #(= e.c.nrepl/load-file-op (get-in % [:request :op])))
              (ix/discard))})
