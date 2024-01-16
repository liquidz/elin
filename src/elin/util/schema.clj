(ns elin.util.schema
  (:require
   [malli.core :as m]))

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

(def ?ClientMessage
  [:map
   [:host string?]
   [:message [:sequential any?]]
   [:output-stream (?instance java.io.OutputStream)]])
