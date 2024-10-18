(ns elin.error
  (:refer-clojure :exclude [let
                            ->
                            ->>]))

(def ^:private unavailable-type ::unavailable)
(def ^:private interrupted-type ::interrupted)
(def ^:private incorrect-type ::incorrect)
(def ^:private forbidden-type ::forbidden)
(def ^:private unsupported-type ::unsupported)
(def ^:private not-found-type ::not-found)
(def ^:private conflict-type ::conflict)
(def ^:private fault-type ::fault)
(def ^:private busy-type ::busy)

(defn unavailable [& [m cause]]
  (ex-info (or (:message m) "Unavailable")
           (merge m {:type unavailable-type})
           cause))

(defn interrupted [& [m cause]]
  (ex-info (or (:message m) "Interrupted")
           (merge m {:type interrupted-type})
           cause))

(defn incorrect [& [m cause]]
  (ex-info (or (:message m) "Incorrect")
           (merge m {:type incorrect-type})
           cause))

(defn forbidden [& [m cause]]
  (ex-info (or (:message m) "Forbidden")
           (merge m {:type forbidden-type})
           cause))

(defn unsupported [& [m cause]]
  (ex-info (or (:message m) "Unsupported")
           (merge m {:type unsupported-type})
           cause))

(defn not-found [& [m cause]]
  (ex-info (or (:message m) "Not found")
           (merge m {:type not-found-type})
           cause))

(defn conflict [& [m cause]]
  (ex-info (or (:message m) "Conflict")
           (merge m {:type conflict-type})
           cause))

(defn fault [& [m cause]]
  (ex-info (or (:message m) "Fault")
           (merge m {:type fault-type})
           cause))

(defn busy [& [m cause]]
  (ex-info (or (:message m) "Busy")
           (merge m {:type busy-type})
           cause))

(defn error? [x]
  (instance? clojure.lang.ExceptionInfo x))

(defn unavailable? [x]
  (and (error? x)
       (= unavailable-type (:type (ex-data x)))))

(defn interrupted? [x]
  (and (error? x)
       (= interrupted-type (:type (ex-data x)))))

(defn incorrect? [x]
  (and (error? x)
       (= incorrect-type (:type (ex-data x)))))

(defn forbidden? [x]
  (and (error? x)
       (= forbidden-type (:type (ex-data x)))))

(defn unsupported? [x]
  (and (error? x)
       (= unsupported-type (:type (ex-data x)))))

(defn not-found? [x]
  (and (error? x)
       (= not-found-type (:type (ex-data x)))))

(defn conflict? [x]
  (and (error? x)
       (= conflict-type (:type (ex-data x)))))

(defn fault? [x]
  (and (error? x)
       (= fault-type (:type (ex-data x)))))

(defn busy? [x]
  (and (error? x)
       (= busy-type (:type (ex-data x)))))

(def ^:private ignore-checkers
  #{number? string? vector? keyword? boolean? map? set?})

(defn- compare-value
  [v]
  (if (some #(% v) ignore-checkers)
    [v nil]
    `(clojure.core/let [v# ~v]
       (if (instance? Exception v#)
         [nil v#]
         [v# nil]))))

(defmacro let
  [bindings & body]
  (assert (vector? bindings) "a vector for its binding")
  (assert (even? (count bindings)) "an even number of forms in binding vector")
  (clojure.core/let [err-sym (gensym)
                     bindings (partition 2 bindings)
                     [k v] (first bindings)
                     first-bind [[k err-sym] (compare-value v)]
                     rest-binds (mapcat (fn [[k v]]
                                          [[k err-sym] `(if (nil? ~err-sym)
                                                          ~(compare-value v)
                                                          [nil ~err-sym])])
                                        (rest bindings))]
    `(clojure.core/let [~@first-bind ~@rest-binds]
       (or ~err-sym
           (do ~@body)))))

(defmacro ->
  [x & forms]
  (clojure.core/let [sym (gensym)
                     bindings (map (fn [form]
                                     (if (seq? form)
                                       (with-meta `(~(first form) ~sym ~@(next form)) (meta form))
                                       (list form sym)))
                                   forms)
                     bindings (cons x bindings)]
    `(let [~@(interleave (repeat sym) bindings)]
       ~sym)))

(defmacro ->>
  [x & forms]
  (clojure.core/let [sym (gensym)
                     bindings (map (fn [form]
                                     (if (seq? form)
                                       (with-meta `(~@form ~sym) (meta form))
                                       (list form sym)))
                                   forms)
                     bindings (cons x bindings)]
    `(let [~@(interleave (repeat sym) bindings)]
       ~sym)))

(defn error-or [& vs]
  (some
   #(when (and %
               (not (error? %)))
      %)
   vs))
