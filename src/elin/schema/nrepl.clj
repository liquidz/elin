(ns elin.schema.nrepl
  (:require
   [elin.schema :as e.schema])
  (:import
   clojure.lang.Atom
   (java.io
    OutputStream
    PushbackInputStream)
   java.net.Socket))

(def ?Message
  [:map-of keyword? any?])

(def ?Output
  [:map
   [:type [:enum "out" "pprint-out" "err"]]
   [:text string?]])

(def ?Connection
  [:map
   [:host string?]
   [:port int?]
   [:socket (e.schema/?instance Socket)]
   [:read-stream (e.schema/?instance PushbackInputStream)]
   [:write-stream (e.schema/?instance OutputStream)]
   [:output-channel e.schema/?ManyToManyChannel]
   [:response-manager (e.schema/?instance Atom)]])

(def ?Client
  [:map
   [:connection ?Connection]
   [:session string?]
   [:supported-ops [:set keyword?]]
   [:initial-namespace [:maybe string?]]
   [:version [:map-of keyword? any?]]])

(def ?Manager
  [:map-of int? [:map
                 [:responses [:sequential ?Message]]
                 [:channel e.schema/?ManyToManyChannel]]])
