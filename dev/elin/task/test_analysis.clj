(ns elin.task.test-analysis
  (:require
   [babashka.pods :as b.pods]))

(b.pods/load-pod "clj-kondo")
(require '[pod.borkdude.clj-kondo :as clj-kondo])

(defn -main
  [& _]
  (-> (clj-kondo/run! {:lint ["src"]
                       :config {:analysis true}})
      (select-keys [:analysis])
      (pr-str)
      (->> (spit "dev/analysis.edn"))))
