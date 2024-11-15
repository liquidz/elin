(ns elin.component.clj-kondo
  "NOTE:
  When using `babashka.pods`, repeatedly running linting on a large codebase caused the memory usage to gradually increase, affecting performance.
  Therefore, instead of using pods, we switched to starting the `clj-kondo` process each time it’s needed."
  (:require
   [babashka.process :as b.process]
   [clojure.core.async :as async]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [com.stuartsierra.component :as component]
   [elin.error :as e]
   [elin.protocol.clj-kondo :as e.p.clj-kondo]
   [elin.protocol.host :as e.p.host]
   [elin.util.file :as e.u.file]
   [malli.core :as m]
   [taoensso.timbre :as timbre]))

(defn- get-project-root-directory
  [host]
  (e/let [cwd (async/<!! (e.p.host/get-current-working-directory! host))
          root (or (e.u.file/get-project-root-directory cwd)
                   (e/not-found))]
    (.getAbsolutePath root)))

(defn- get-cache-file-path
  [user-dir]
  (.getAbsolutePath
    (io/file (e.u.file/get-cache-directory)
             (str (str/replace user-dir "/" "_")
                  ".edn"))))

(m/=> clj-kondo-available? [:=> [:cat string?] boolean?])
(defn- clj-kondo-available?
  [command]
  (try
    (zero? (:exit (b.process/shell {:out :string} command "--version")))
    (catch Exception _ false)))

(defn- clj-kondo-run!
  [{:keys [command lint config shell-config]}]
  (try
    (let [lint-args (->> (or lint [])
                         (map #(vector "--lint" %)))
          config' (assoc-in config [:output :format] :edn)]
      (->> [command lint-args "--config" (pr-str config')]
           (flatten)
           (apply b.process/shell (merge {:out :string :continue true}
                                         shell-config))
           (:out)
           (edn/read-string)))
    (catch Exception ex
      (timbre/info "Failed to run clj-kondo" ex)
      {})))

(defrecord CljKondo
  [;; COMPONENTS
   lazy-host
   ;; CONFIGS
   command
   config
   ;; PARAMS
   available?
   analyzing?-atom
   analyzed-atom]
  component/Lifecycle
  (start [this]
    (timbre/info "CljKondo component: Started")
    (assoc this
           :available? (if command
                         (clj-kondo-available? command)
                         false)
           :analyzing?-atom (atom false)
           :analyzed-atom (atom nil)))

  (stop [this]
    (timbre/info "CljKondo component: Stopped")
    (dissoc this :analyzing?-atom :analyzed-atom))

  e.p.clj-kondo/ICljKondo
  (analyze [this]
    (cond
      (not available?)
      (async/go (e/unavailable {:message "clj-kondo is unavailable"}))

      (e.p.clj-kondo/analyzing? this)
      (async/go (e/busy {:message "clj-kondo is already analyzing"}))

      :else
      (do (reset! analyzing?-atom true)
          (async/thread
            (try
              (e/let [project-root-dir (get-project-root-directory lazy-host)
                      res (clj-kondo-run! {:command command
                                           :lint [project-root-dir]
                                           :config config})
                      cache-path (get-cache-file-path project-root-dir)]
                (spit cache-path (pr-str res))
                (reset! analyzed-atom res))
              (finally
                (reset! analyzing?-atom false)))))))

  (restore [this]
    (cond
      (not available?)
      (async/go (e/unavailable {:message "clj-kondo is unavailable"}))

      (e.p.clj-kondo/analyzing? this)
      (async/go (e/busy {:message "clj-kondo is already analyzing"}))

      :else
      (do (reset! analyzing?-atom true)
          (async/thread
            (try
              (e/let [project-root-dir (get-project-root-directory lazy-host)
                      cache-file (get-cache-file-path project-root-dir)
                      analyzed (with-open [r (io/reader cache-file)]
                                 (edn/read (java.io.PushbackReader. r)))]
                (reset! analyzed-atom analyzed))
              (catch java.io.FileNotFoundException ex
                (e/not-found {:message (ex-message ex)}))
              (catch Exception ex
                (e/fault {:message (ex-message ex)}))
              (finally
                (reset! analyzing?-atom false)))))))

  (analyzing? [_]
    @analyzing?-atom)

  (analyzed? [_]
    (some? @analyzed-atom))

  (analysis [this]
    (when (and available?
               (e.p.clj-kondo/analyzed? this))
      (:analysis @analyzed-atom)))

  (analyze-code!! [_ code]
    (when available?
      (clj-kondo-run! {:command command
                       :lint ["-"]
                       :config {:output {:analysis {:protocol-impls true
                                                    :arglists true
                                                    :locals true
                                                    :keywords true}}}
                       :shell-config {:in code}}))))

(defn new-clj-kondo
  [config]
  (map->CljKondo (or (:clj-kondo config) {})))
