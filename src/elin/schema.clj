(ns elin.schema
  (:require
   [malli.core :as m])
  (:import
   clojure.core.async.impl.channels.ManyToManyChannel
   (clojure.lang
    Atom
    ExceptionInfo)))

(defn ?instance
  [klass]
  (m/-simple-schema
   {:type ::instance
    :pred #(instance? klass %)}))

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

(defn error-or
  [schema]
  [:or ?Error schema])
