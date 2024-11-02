(ns elin.interceptor.nrepl.malli
  (:require
   [clojure.edn :as edn]
   [clojure.pprint :as pp]
   [clojure.string :as str]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.constant.lookup :as e.c.lookup]
   [elin.constant.nrepl :as e.c.nrepl]
   [elin.error :as e]
   [elin.function.nrepl :as e.f.nrepl]
   [elin.util.nrepl :as e.u.nrepl]
   [exoscale.interceptor :as ix]
   [malli.core :as m]
   [taoensso.timbre :as timbre])
  (:import
   clojure.lang.ExceptionInfo))

(def ^:private ?=>
  (m/-simple-schema
   {:type ::=>
    :pred #(= :=> %)}))

(def ^:private ?->
  (m/-simple-schema
   {:type ::->
    :pred #(= :-> %)}))

(def ^:private ?function
  (m/-simple-schema
   {:type ::function
    :pred #(= :function %)}))

(def ^:private ?FunctionSchema
  [:schema {:registry {::fn [:or
                             [:catn
                              [:type ?=>]
                              [:input [:vector any?]]
                              [:output any?]]
                             [:catn
                              [:type ?->]
                              [:input [:+ any?]]
                              [:output any?]]]}}
   [:or
    [:ref ::fn]
    [:catn
     [:type ?function]
     [:fns [:+ [:schema [:ref ::fn]]]]]]])

(defn- convert-schema-form-to-sexpr [schema-form]
  (condp = (when (sequential? schema-form)
             (first schema-form))
    :map
    (->> (rest schema-form)
         (map (fn [[map-key map-value]]
                [map-key (convert-schema-form-to-sexpr map-value)]))
         (into {}))

    :sequential
    (->> (rest schema-form)
         (map convert-schema-form-to-sexpr)
         (vec))

    :or
    (->> (rest schema-form)
         (map convert-schema-form-to-sexpr)
         (cons 'or))

    schema-form))

(defn- convert-parsed-function-schema-to-sexpr [parsed]
  (condp = (:type parsed)
    :function
    (->> (:fns parsed)
         (mapcat convert-parsed-function-schema-to-sexpr))

    :=>
    [{:input (->> (:input parsed)
                  (rest)
                  (mapv convert-schema-form-to-sexpr))
      :output (convert-schema-form-to-sexpr (:output parsed))}]

    :->
    [{:input (->> (:input parsed)
                  (mapv convert-schema-form-to-sexpr))
      :output (convert-schema-form-to-sexpr (:output parsed))}]

    nil))

(defn- fetch-schema-code
  [ns-sym var-sym]
  `(some-> (malli.core/function-schemas)
           (get-in ['~ns-sym '~var-sym :schema])
           (malli.core/-form)))

(defn- pp-str
  [v]
  (with-out-str
    (pp/pprint v)))

(defn- add-indent
  [indent s]
  (let [indent-s (apply str (repeat (or indent 0) " "))]
    (->> (str/split-lines s)
         (map #(str indent-s %))
         (str/join "\n"))))

(defn- document-str
  [converted]
  (str e.c.lookup/subsection-separator
       "\n*Malli*\n"
       "Inputs: "
       (->> (map (comp pp-str :input) converted)
            (str/join "\n")
            (add-indent 8)
            (str/trim))

       "\nReturns: "
       (->> (map :output converted)
            (distinct)
            (map pp-str)
            (str/join "\n")
            (add-indent 9)
            (str/trim))))

(def lookup-schema
  {:kind e.c.interceptor/nrepl
   :leave (-> (fn [{:as ctx :component/keys [nrepl] :keys [response]}]
                (try
                  (let [{ns-str :ns var-str :name} (first response)
                        _ (when (or (not ns-str)
                                    (not var-str))
                            (throw (e/not-found {:response response})))
                        code (str (fetch-schema-code (symbol ns-str) (symbol var-str)))
                        doc (some-> (e.f.nrepl/eval!! nrepl code)
                                    (:value)
                                    (edn/read-string))
                        doc' (when doc
                               (-> (m/parse ?FunctionSchema doc)
                                   (convert-parsed-function-schema-to-sexpr)
                                   (document-str)))
                        response' (cond->> response
                                    doc'
                                    (e.u.nrepl/update-messages :doc #(str % (when % "\n") "\n" doc')))]
                    (assoc ctx :response response'))
                  (catch ExceptionInfo _
                    ctx)
                  (catch Exception ex
                    (timbre/error "Failed to execute malli-lookup" ex)
                    ctx)))
              (ix/when #(= e.c.nrepl/info-op
                           (get-in % [:request :op]))))})
