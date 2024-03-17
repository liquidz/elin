(ns elin.test-helper.clj-kondo
  (:require
   [clojure.core.async :as async]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [elin.protocol.clj-kondo :as e.p.clj-kondo]
   [elin.test-helper.host :as h.host]))

(defrecord TestCljKondo
  [lazy-host analyzing?-atom analyzed-atom]
  e.p.clj-kondo/ICljKondo
  (analyze [_]
    (async/go nil))
  (restore [_]
    (async/go nil))
  (analyzing? [_]
    @analyzing?-atom)
  (analyzed? [_]
    true)
  (analysis [_]
    (:analysis @analyzed-atom)))

(defn test-clj-kondo
  []
  ;; NOTE analysis.edn is generated by `bb generate-test-analysis`
  (let [analyzed (-> (io/resource "analysis.edn")
                     (slurp)
                     (edn/read-string))]
    (map->TestCljKondo {:lazy-host (h.host/test-host)
                        :analyzing?-atom (atom false)
                        :analyzed-atom (atom analyzed)})))