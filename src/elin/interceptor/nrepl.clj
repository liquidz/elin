(ns elin.interceptor.nrepl
  (:require
   [clojure.string :as str]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.constant.nrepl :as e.c.nrepl]
   [elin.function.vim.virtual-text :as e.f.v.virtual-text]
   [elin.protocol.rpc :as e.p.rpc]
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

(def output-eval-result-to-cmdline-interceptor
  {:name ::output-eval-result-to-cmdline-interceptor
   :kind e.c.interceptor/nrepl
   :leave (fn [{:as ctx :component/keys [host] :keys [request response]}]
            (when (= e.c.nrepl/eval-op (:op request))
              (let [msg (e.u.nrepl/merge-messages response)]
                (when-let [v (:value msg)]
                  (e.p.rpc/echo-text host (str/trim (str v))))

                (when-let [v (:err msg)]
                  (e.p.rpc/echo-message host (str/trim (str v)) "ErrorMsg"))))
            ctx)})

(def set-eval-result-to-virtual-text-interceptor
  {:name ::set-eval-result-to-virtual-text-interceptor
   :kind e.c.interceptor/nrepl
   :leave (fn [{:as ctx :component/keys [host] :keys [request response]}]
            (when (= e.c.nrepl/eval-op (:op request))
              (when-let [v (:value (e.u.nrepl/merge-messages response))]
                (e.f.v.virtual-text/set host
                                        (str v)
                                        {:highlight "DiffText"})))
            ctx)})
