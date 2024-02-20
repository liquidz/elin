(ns elin.task.nrepl
  (:require
   [babashka.fs :as fs]
   [babashka.nrepl.server :as srv]
   [elin.dev :as e.dev]))

(defn- start-dev-server!
  [options]
  (let [host (or (:host options) "nvim")
        port (or (:port options) 45678)]
    (e.dev/initialize {:host host :port port})
    (e.dev/start)))

(defn -main
  [& [options]]
  (start-dev-server! options)

  (let [{:keys [socket]} (srv/start-server! {:host "localhost" :port 0})
        port (.getLocalPort socket)]
    (spit ".nrepl-port" (str port))
    (-> (Runtime/getRuntime)
        (.addShutdownHook
         (Thread. (fn [] (fs/delete ".nrepl-port")))))
    (deref (promise))))
