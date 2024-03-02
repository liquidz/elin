(ns elin.interceptor.optional.nrepl
  (:require
   [clojure.edn :as edn]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.constant.nrepl :as e.c.nrepl]
   [elin.function.nrepl :as e.f.nrepl]
   [elin.util.nrepl :as e.u.nrepl]
   [exoscale.interceptor :as ix]
   [taoensso.timbre :as timbre]))

(defn- fetch-schema-code [ns-sym var-sym]
  `(with-out-str
     (-> (malli.core/function-schemas)
         (get-in ['~ns-sym '~var-sym :schema])
         (clojure.pprint/pprint))))

(def malli-lookup-interceptor
  {:name ::malli-lookup-interceptor
   :kind e.c.interceptor/nrepl
   :optional true
   :leave (-> (fn [{:as ctx :component/keys [nrepl] :keys [request response]}]
                (try
                  (let [{ns-str :ns var-str :sym} request
                        code (str (fetch-schema-code (symbol ns-str) (symbol var-str)))
                        doc (some-> (e.f.nrepl/eval!! nrepl code)
                                    (:value)
                                    (edn/read-string))
                        doc' (str "*malli?*\n  " doc)
                        response' (if (some :doc response)
                                    (->> response
                                         (e.u.nrepl/update-messages :doc #(str % "\n\n" doc')))
                                    (cons {:doc (str "\n" doc')} response))]
                    (assoc ctx :response response'))
                  (catch Exception ex
                    (timbre/error "Failed to execute malli-lookup-interceptor" ex)
                    ctx)))
              (ix/when #(= e.c.nrepl/info-op
                           (get-in % [:request :op]))))})
