(ns watch
  (:require
   [clojure.java.io :as io]
   [common]
   [nextjournal.beholder :as beholder]))

(defn -main [& _]
  (let [dir "."
        _ (println (str "Watching "
                        (.getAbsolutePath (io/file dir))
                        " ..."))
        watcher (beholder/watch (fn [& _]
                                  (common/convert-file))
                                dir)]
    (deref (promise))
    (beholder/stop watcher)))
