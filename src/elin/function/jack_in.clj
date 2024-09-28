(ns elin.function.jack-in
  (:require
   [clojure.core.async :as async]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [elin.error :as e]
   [elin.protocol.host :as e.p.host]
   [elin.util.process :as e.u.process])
  (:import
   java.net.ServerSocket))

(def ^:private clojure-command
  (or (System/getenv "ELIN_REPL_CLOJURE_CLI_CMD")
      "clj"))

(def ^:private babashka-command
  (or (System/getenv "ELIN_REPL_BABASHKA_CMD")
      "bb"))

(def ^:private elin-root-dir
  (-> (io/file *file*)
      (.getParentFile)
      (.getParentFile)
      (.getParentFile)
      (.getParentFile)
      (.getAbsolutePath)))

(defn- get-free-port
  []
  (with-open [sock (ServerSocket. 0)]
    (.getLocalPort sock)))


(defn- existing-file
  [dir filename]
  (let [file (io/file dir filename)]
    (when (.exists file)
      file)))

(defn- find-project-files
  [cwd]
  (loop [dir (io/file cwd)]
    (when dir
      (let [deps-edn-file (existing-file dir "deps.edn")
            project-clj-file (existing-file dir "project.clj")
            bb-edn-file (existing-file dir "bb.edn")]
        (if (or deps-edn-file
                project-clj-file
                bb-edn-file)
          {:clojure-cli deps-edn-file
           :leiningen project-clj-file
           :babashka bb-edn-file}
          (recur (.getParentFile dir)))))))

(defn- select-project
  [{:keys [force-leiningen force-shadow-cljs]}
   cwd]
  (let [res (find-project-files cwd)]
    (cond
      force-leiningen
      (first (select-keys res [:leiningen]))

      force-shadow-cljs
      (first (select-keys res [:shadow-cljs]))

      :else
      (first (select-keys res [:clojure-cli])))))

(def ^:private command-config
  (-> (io/file elin-root-dir "bb.edn")
      (slurp)
      (edn/read-string)
      (get-in [:__elin_internal__ :command])))

(defn- generate-command
  [project-type port]
  (case project-type
    :clojure-cli [clojure-command
                  "-Sdeps" (pr-str {:deps (:deps command-config)})
                  "-M" "-m" "nrepl.cmdline"
                  "--port" (str port)
                  "--middleware" (pr-str (:middlewares command-config))
                  "--interactive"]

    :babashka [babashka-command
               "nrepl-server"
               (str "localhost:" port)]

    (e/unsupported)))

(defn port->process-id
  [port]
  (str "jack-in-" port))

(defn launch-process
  ([elin]
   (launch-process elin {}))
  ([{:component/keys [host]}
    options]
   (e/let [path (async/<!! (e.p.host/get-current-file-path! host))
           [project-type project-file] (select-project options path)
           project-root-dir (-> project-file
                                (.getParentFile)
                                (.getAbsolutePath))
           port (get-free-port)
           args (->> (generate-command project-type port)
                     (cons {:dir project-root-dir}))]
     (e.u.process/start (port->process-id port) args)
     port)))
