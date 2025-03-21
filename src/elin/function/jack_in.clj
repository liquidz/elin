(ns elin.function.jack-in
  (:require
   [clojure.core.async :as async]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [elin.constant.jack-in :as e.c.jack-in]
   [elin.constant.nrepl :as e.c.nrepl]
   [elin.error :as e]
   [elin.protocol.host :as e.p.host]
   [elin.util.nrepl :as e.u.nrepl]
   [elin.util.process :as e.u.process]))

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

(defmulti generate-command
  (fn [project-type _port _optional-args]
    project-type))

(defmethod generate-command :default
  [_ _ _]
  (e/unsupported))

(defmethod generate-command e.c.jack-in/clojure-cli
  [_ port optional-args]
  {:language e.c.nrepl/lang-clojure
   :command (concat [e.c.jack-in/clojure-command]
              optional-args
              ["-Sdeps" (pr-str {:deps (:deps command-config)})
               "-M" "-m" "nrepl.cmdline"
               "--port" (str port)
               "--middleware" (pr-str (:middlewares command-config))
               "--interactive"])})

(defmethod generate-command e.c.jack-in/leiningen
  [_ port _]
  {:language e.c.nrepl/lang-clojure
   :command (concat [e.c.jack-in/leiningen-command
                     "update-in" ":dependencies" "conj"]
                    (->> (:deps command-config)
                         (map (fn [[lib {:mvn/keys [version]}]]
                                (format "[%s \"%s\"]"
                                        lib
                                        version))))
                    ["--"
                     "update-in" "[:repl-options, :nrepl-middleware]" "conj"]
                    (:middlewares command-config)
                    ["--"
                     "repl" ":start" ":port"
                     port])})

(defmethod generate-command e.c.jack-in/babashka
  [_ port _]
  {:language e.c.nrepl/lang-clojure
   :command [e.c.jack-in/babashka-command
             "nrepl-server"
             (str "localhost:" port)]})

(defmethod generate-command e.c.jack-in/squint
  [_ port _]
  (e/let [squint-cmd (cond
                       (e.u.process/executable? e.c.jack-in/squint-command)
                       [e.c.jack-in/squint-command]

                       (e.u.process/executable? e.c.jack-in/deno-command)
                       [e.c.jack-in/deno-command "run" "-A" "npm:squint-cljs@latest"]

                       :else
                       (e/not-found {:message "squint of deno command is required"}))]
    {:language e.c.nrepl/lang-clojurescript
     :command (concat squint-cmd ["nrepl-server" ":port" (str port)])}))

(defmethod generate-command e.c.jack-in/nbb
  [_ port _]
  (e/let [nbb-cmd (cond
                    (e.u.process/executable? e.c.jack-in/nbb-command)
                    [e.c.jack-in/nbb-command]

                    (e.u.process/executable? e.c.jack-in/deno-command)
                    [e.c.jack-in/deno-command "run" "-A" "npm:nbb@latest"]

                    :else
                    (e/not-found {:message "nbb or deno command is required"}))]
    {:language e.c.nrepl/lang-clojure
     :command (concat nbb-cmd ["nrepl-server" ":port" (str port)])}))

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
           {:keys [language command]} (generate-command project-type port [])
           args (cons {:dir project-root-dir} command)]
     (e.u.process/start (port->process-id port) args)
     {:language language :port port})))
