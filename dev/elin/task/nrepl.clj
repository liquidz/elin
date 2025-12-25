(ns elin.task.nrepl
  (:require
   [babashka.fs :as fs]
   [babashka.nrepl.server :as srv]
   [bencode.core :as b]
   [elin.dev :as e.dev]))

(def DEFAULT_NREPL_SERVER_PORT 34567)
(def DEFAULT_DEV_SERVER_HOST "nvim")
(def DEFAULT_DEV_SERVER_PORT 45678)

(defn- start-dev-server!
  [{:keys [port options]}]
  (with-open [s (java.net.Socket. "localhost" port)
              output-stream (.getOutputStream s)]
    (let [host (or (:host options) DEFAULT_DEV_SERVER_HOST)
          port (or (:port options) DEFAULT_DEV_SERVER_PORT)]
      (b/write-bencode output-stream
                       {"op" "eval"
                        "code" (str `(do (elin.dev/initialize {:host ~host :port ~port})
                                         (elin.dev/start)))}))))

(defn -main
  [& [options]]
  (let [{:keys [socket]} (srv/start-server! {:host "localhost" :port DEFAULT_NREPL_SERVER_PORT})
        port (.getLocalPort socket)]
    (spit ".nrepl-port" (str port))
    (-> (Runtime/getRuntime)
        (.addShutdownHook
          (Thread. (fn [] (fs/delete ".nrepl-port")))))
    (start-dev-server! {:port port :options options})
    (deref (promise))))
