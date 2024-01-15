(ns elin.dev
  (:require
   [com.stuartsierra.component :as component]
   [elin.system :as elin.system]
   [malli.dev :as m.dev]))

(def system-map
  (elin.system/new-system))

(defonce sys (atom nil))

(defn start-system
  []
  (when-not @sys
    (reset! sys (component/start-system system-map))
    ::started))

(defn stop-system
  []
  (when @sys
    (component/stop-system @sys)
    (reset! sys nil)
    ::stopped))

(defn start
  []
  #_(start-system)
  (m.dev/start!))

(defn stop
  []
  #_(stop-system)
  (m.dev/stop!))

(defn go
  []
  (stop)
  (start))
