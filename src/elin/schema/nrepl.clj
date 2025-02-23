(ns elin.schema.nrepl
  (:require
   [elin.constant.nrepl :as e.c.nrepl]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema :as e.schema]
   [malli.util :as m.util]))

(def ?Message
  [:map-of keyword? any?])

(def ?Output
  [:map
   [:type [:enum "out" "pprint-out" "err"]]
   [:text string?]])

(def ?Connection
  (e.schema/?protocol e.p.nrepl/IConnection))

(def ?Client
  (e.schema/?protocol e.p.nrepl/IConnection
                      e.p.nrepl/IClient))

(def ?Manager
  [:map-of int? [:map
                 [:responses [:sequential ?Message]]
                 [:channel e.schema/?ManyToManyChannel]]])

(def ?PortFile
  [:map
   [:port int?]
   [:port-file [:maybe string?]]
   [:language [:maybe [:enum
                       e.c.nrepl/lang-clojure
                       e.c.nrepl/lang-clojurescript]]]])

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

(def ?TestSummary
  [:map
   [:summary string?]
   [:succeeded? boolean?]])

(def ?TestActualValue
  [:map
   [:actual string?]
   [:diffs {:optional true} string?]])

(def ?TestResult
  [:or
   [:map
    [:result [:enum :passed]]
    [:ns string?]
    [:var string?]]
   (m.util/merge
     [:map
      [:result [:enum :failed]]
      [:ns string?]
      [:var string?]
      [:filename string?]
      [:text string?]
      [:expected string?]
      [:lnum {:optional true} int?]]
     ?TestActualValue)])
