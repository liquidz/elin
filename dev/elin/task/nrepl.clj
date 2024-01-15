(ns elin.task.nrepl
  (:require
   [babashka.fs :as fs]
   [babashka.nrepl.server :as srv]
   [bencode.core :as b]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def ^:private namespaces-to-load
  '[["src" elin.component.server]
    ["src" elin.component.handler]
    ["src" elin.nrepl.message]
    ["src" elin.nrepl.response]
    ["src" elin.nrepl.connection]
    ["src" elin.nrepl.client]
    ["src" elin.system]
    ["src" elin.log]
    ["src" elin.util.file]
    ["dev" user]])

(defn- ns-sym->file
  [{:keys [base-dir ns-sym]}]
  (-> (if base-dir
        (str base-dir "/" ns-sym)
        (str ns-sym))
      (str/replace "." "/")
      (str/replace "-" "_")
      (str ".clj")
      (str/split #"/")
      (->> (apply io/file))))

(defn- load-namespace
  [{:keys [output-stream base-dir ns-sym]}]
  (let [file (ns-sym->file {:base-dir base-dir :ns-sym ns-sym})]
    (println "Loading" (.getAbsolutePath file))
    (b/write-bencode output-stream
                     {"op" "load-file"
                      "file" (slurp file)
                      "file-name" (.getName file)
                      "file-path" (.getAbsolutePath file)})))

(defn- load-namespaces
  [{:keys [port namespaces]}]
  (with-open [s (java.net.Socket. "localhost" port)
              out (.getOutputStream s)]
    (doseq [[base-dir sym] namespaces]
      (load-namespace {:output-stream out
                       :base-dir base-dir
                       :ns-sym sym}))))

(defn -main
  [& _]
  (let [{:keys [socket]} (srv/start-server! {:host "localhost" :port 0})
        port (.getLocalPort socket)]
    (spit ".nrepl-port" (str port))
    (-> (Runtime/getRuntime)
        (.addShutdownHook
         (Thread. (fn [] (fs/delete ".nrepl-port")))))
    ;; (load-namespaces {:port port
    ;;                   :namespaces namespaces-to-load})
    (deref (promise))))
