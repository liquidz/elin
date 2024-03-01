(ns elin.interceptor.lookup
  (:require
   [clojure.edn :as edn]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.function.nrepl :as e.f.nrepl]))

(defn- fetch-schema-code [ns-sym var-sym]
  `(with-out-str
     (-> (malli.core/function-schemas)
         (get-in ['~ns-sym '~var-sym :schema])
         (clojure.pprint/pprint))))

(def malli-function-schema-interceptor
  {:name ::malli-function-schema-interceptor
   :kind e.c.interceptor/lookup
   :optional true
   :enter (fn [{:as ctx :component/keys [nrepl] :keys [lookup]}]
            (let [ns-sym (symbol (:ns lookup))
                  var-sym (symbol (:name lookup))
                  code (str (fetch-schema-code ns-sym var-sym))
                  doc (some-> (e.f.nrepl/eval!! nrepl code)
                              (:value)
                              (edn/read-string))]
              (update-in ctx [:lookup :doc] #(str % "\n\n*malli*\n  " doc))))})

;; (comment
;;   (def sample
;;     (-> (malli.core/function-schemas)
;;         (get-in '[elin.handler.internal initialize :schema])
;;         (pr-str)))
;;
;;   (let [zloc (r.zip/of-string sample)]
;;     (r.zip/find-value))
;;   (let [idx (str/ilndex-of sample ":cat")]
;;     (subs sample 0 (+ idx 4))))
