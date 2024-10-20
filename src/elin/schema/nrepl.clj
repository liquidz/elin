(ns elin.schema.nrepl
  (:require
   [elin.schema :as e.schema]
   [malli.util :as m.util])
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
   [:raw-message-channel e.schema/?ManyToManyChannel]
   [:response-manager (e.schema/?instance Atom)]])

(def ?Client
  [:map
   [:connection ?Connection]
   [:session string?]
   [:supported-ops [:set keyword?]]
   [:initial-namespace [:maybe string?]]
   [:version [:map-of keyword? any?]]
   [:port-file [:maybe string?]]
   [:language [:maybe [:enum "clojure" "clojurescript"]]]])

(def ?Manager
  [:map-of int? [:map
                 [:responses [:sequential ?Message]]
                 [:channel e.schema/?ManyToManyChannel]]])

(def ?PortFile
  (m.util/merge
   [:map [:port int?]]
   (-> ?Client
       (m.util/select-keys [:port-file :language]))))

(def ?Lookup
  [:map
   [:ns string?]
   [:name string?]
   [:file string?]
   [:arglists-str string?]
   [:column int?]
   [:line int?]
   [:doc {:optional true} string?]
   ;; cider-nrepl's info op
   [:arglists {:optional true} [:maybe string?]]])

(def ?LookupJavaRenderingData
  [:map {:closed? true}
   [:format-type keyword?]
   [:name string?]
   [:arglists [:sequential string?]]
   [:document [:maybe string?]]
   [:return [:maybe string?]]
   [:javadoc [:maybe string?]]])

(def ?LookupClojureRenderingData
  [:map {:closed? true}
   [:format-type keyword?]
   [:name string?]
   [:arglists [:sequential string?]]
   [:document [:maybe string?]]
   [:has-see-alsos boolean?]
   [:see-alsos [:sequential string?]]])

(def ?ClojuredocsRenderingData
  (let [?indexed-content [:map
                          [:index int?]
                          [:content string?]]]
    [:map {:closed? true}
     [:format-type keyword?]
     [:name string?]
     [:arglists [:sequential string?]]
     [:document [:maybe string?]]
     [:has-examples boolean?]
     [:example-count int?]
     [:examples [:sequential ?indexed-content]]
     [:has-see-alsos boolean?]
     [:see-also-count int?]
     [:see-alsos [:sequential ?indexed-content]]
     [:has-notes boolean?]
     [:note-count int?]
     [:notes [:sequential ?indexed-content]]]))

(def ?RenderingData
  [:map
   [:format-type keyword?]])
