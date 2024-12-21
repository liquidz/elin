(ns elin.interceptor.tap
  (:require
   [clojure.string :as str]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.function.evaluate :as e.f.evaluate]
   [exoscale.interceptor :as ix])
  (:import
   (java.time LocalDateTime)))

(def ^:private ns-sym
  'elin.interceptor.tap)

(def ^:private tap-fn-sym
  'catch-tapped!)

(def ^:private tapped-atom-sym
  (gensym "elin-tapped"))

(defn- initialize-code
  []
  (str
    `(do (in-ns '~ns-sym)
         (refer-clojure)
         (def ~tapped-atom-sym (atom []))

         (try
           (remove-tap @(resolve '~tap-fn-sym))
           (catch Exception _# nil))

         (defn ~tap-fn-sym
           [x#]
           (swap! ~tapped-atom-sym conj {:id (str (random-uuid))
                                         :time (str (LocalDateTime/now))
                                         :value (clojure.core.protocols/datafy x#)}))
         (try
           (add-tap ~tap-fn-sym)
           (catch Exception _# nil)))))

(def initialize
  "Defines a tap function and adds it."
  {:kind e.c.interceptor/connect
   :leave (-> (fn [ctx]
                (e.f.evaluate/evaluate-code ctx (initialize-code)))
              (ix/discard))})

(def access-tapped-values
  "Enables access to tapped values from a specific symbol."
  {:kind e.c.interceptor/evaluate
   :enter (-> (fn [ctx]
                (let [var-sym (symbol (name ns-sym)
                                      (name tapped-atom-sym))
                      var-code (str `(deref ~var-sym))]
                  (update ctx :code #(str/replace % #"\*tapped\*" var-code))))
              (ix/when #(str/includes? (:code %) "*tapped*")))})
