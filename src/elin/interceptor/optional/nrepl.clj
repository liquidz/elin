(ns elin.interceptor.optional.nrepl
  (:require
   [clojure.edn :as edn]
   [clojure.string :as str]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.constant.lookup :as e.c.lookup]
   [elin.constant.nrepl :as e.c.nrepl]
   [elin.function.nrepl :as e.f.nrepl]
   [elin.util.nrepl :as e.u.nrepl]
   [exoscale.interceptor :as ix]
   [malli.core :as m]
   [taoensso.timbre :as timbre]))

(def ^:private ?=>
  (m/-simple-schema
   {:type ::=>
    :pred #(= :=> %)}))

(def ^:private ?function
  (m/-simple-schema
   {:type ::function
    :pred #(= :function %)}))

(def ^:private ?FunctionSchema
  [:schema {:registry {::fn [:catn
                             [:type ?=>]
                             [:input [:vector any?]]
                             [:output any?]]}}
   [:or
    [:ref ::fn]
    [:catn
     [:type ?function]
     [:fns [:+ [:schema [:ref ::fn]]]]]]])

(defn- format-schema-form [level schema-form]
  (let [indent (apply str (repeat (* 2 level) " "))]
    (if-not  (sequential? schema-form)
      schema-form
      (str/join (str "\n" indent)
                (mapv (fn [v]
                        (if (and (sequential? v)
                                 (contains? #{:map :or} (first v)))
                          (str "["
                               (format-schema-form (inc level) v)
                               "]")
                          v))
                      schema-form)))))

(defn- format-parsed-function-schema [parsed]
  (when (= :=> (:type parsed))
    (str "  input~\n    "
         (->> (:input parsed)
              (rest)
              (format-schema-form 2))
         "\n\n  output~\n    "
         (format-schema-form 2 [(:output parsed)]))))

(defn- fetch-schema-code
  [ns-sym var-sym]
  `(-> (malli.core/function-schemas)
       (get-in ['~ns-sym '~var-sym :schema])
       (malli.core/-form)))

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
                        doc' (str e.c.lookup/subsection-separator
                                  "\n*Malli*\n"
                                  (-> (m/parse ?FunctionSchema doc)
                                      (format-parsed-function-schema)))
                        response' (->> response
                                       (e.u.nrepl/update-messages :doc #(str % (when % "\n") "\n" doc')))]
                    (assoc ctx :response response'))
                  (catch Exception ex
                    (timbre/error "Failed to execute malli-lookup-interceptor" ex)
                    ctx)))
              (ix/when #(= e.c.nrepl/info-op
                           (get-in % [:request :op]))))})
