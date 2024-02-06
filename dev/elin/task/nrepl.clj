(ns elin.task.nrepl
  (:require
   [babashka.fs :as fs]
   [babashka.nrepl.server :as srv]))

(defn -main
  [& _]
  (let [{:keys [socket]} (srv/start-server! {:host "localhost" :port 0})
        port (.getLocalPort socket)]
    (spit ".nrepl-port" (str port))
    (-> (Runtime/getRuntime)
        (.addShutdownHook
         (Thread. (fn [] (fs/delete ".nrepl-port")))))
    (deref (promise))))
