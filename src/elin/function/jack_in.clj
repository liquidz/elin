(ns elin.function.jack-in
  (:require
   [clojure.core.async :as async]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [elin.constant.jack-in :as e.c.jack-in]
   [elin.error :as e]
   [elin.protocol.host :as e.p.host]
   [elin.util.nrepl :as e.u.nrepl]
   [elin.util.process :as e.u.process]))

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

(defn- parent-absolute-path
  [path]
  (-> (io/file path)
      (.getParentFile)
      (.getAbsolutePath)))

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
          {e.c.jack-in/clojure-cli deps-edn-file
           e.c.jack-in/leiningen project-clj-file
           e.c.jack-in/babashka bb-edn-file}
          (recur (.getParentFile dir)))))))

(defn select-project
  [{:keys [forced-project]}
   cwd]
  (if forced-project
    [forced-project cwd]
    (->> (find-project-files cwd)
         (filter val)
         (first))))

(def ^:private command-config
  (-> (io/file elin-root-dir "bb.edn")
      (slurp)
      (edn/read-string)
      (get-in [:__elin_internal__ :command])))

(defn generate-command
  ([project-type port]
   (generate-command project-type port []))
  ([project-type port optional-args]
   (condp = project-type
     e.c.jack-in/clojure-cli
     (concat [clojure-command]
             optional-args
             ["-Sdeps" (pr-str {:deps (:deps command-config)})
              "-M" "-m" "nrepl.cmdline"
              "--port" (str port)
              "--middleware" (pr-str (:middlewares command-config))
              "--interactive"])

     e.c.jack-in/babashka
     [babashka-command
      "nrepl-server"
      (str "localhost:" port)]

     (e/unsupported))))

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
           project-root-dir (parent-absolute-path project-file)
           port (e.u.nrepl/get-free-port)
           args (->> (generate-command project-type port)
                     (cons {:dir project-root-dir}))]
     (e.u.process/start (port->process-id port) args)
     port)))
