(ns elin.schema.nrepl
  (:require
   [elin.util.schema :as e.u.schema])
  (:import
   clojure.core.async.impl.channels.ManyToManyChannel
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
   [:socket (e.u.schema/?instance Socket)]
   [:read-stream (e.u.schema/?instance PushbackInputStream)]
   [:write-stream (e.u.schema/?instance OutputStream)]
   [:output-channel (e.u.schema/?instance ManyToManyChannel)]
   [:response-manager (e.u.schema/?instance Atom)]])

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
                 [:channel (e.u.schema/?instance ManyToManyChannel)]]])
