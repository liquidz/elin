(ns elin.schema
  (:require
   [malli.core :as m])
  (:import
   (clojure.core.async.impl.channels ManyToManyChannel)
   (clojure.lang Atom ExceptionInfo)
   (java.util.regex Pattern)))

(defn ?instance
  [klass]
  (m/-simple-schema
    {:type klass
     :pred #(instance? klass %)}))

(defn ?protocol
  [& protocols]
  (m/-simple-schema
    {:type ::protocol
     :pred (fn [v]
             (every? #(satisfies? % v) protocols))}))

(def ?File
  (?instance java.io.File))

(def ?NotBytes
  (m/-simple-schema
    {:type ::not-bytes
     :pred #(not (bytes? %))}))

(def ?Error
  (?instance ExceptionInfo))

(def ?ManyToManyChannel
  (?instance ManyToManyChannel))

(def ?Atom
  (?instance Atom))

(def ?Pattern
  (?instance Pattern))

(defn error-or
  [schema]
  [:or ?Error schema])
