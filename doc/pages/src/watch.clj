(ns watch
  (:require
   [common]
   [nextjournal.beholder :as beholder]))

(defn -main [& _]
  (println "Watching ...")
  (let [watcher (beholder/watch (fn [& _]
                                  (common/convert-file))
                                ".")]
    (deref (promise))
    (beholder/stop watcher)))
