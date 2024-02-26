(ns elin.component.clj-kondo
  (:require
   [babashka.pods :as b.pods]
   [clojure.core.async :as async]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [com.stuartsierra.component :as component]
   [elin.error :as e]
   [elin.function.vim :as e.f.vim]
   [elin.protocol.clj-kondo :as e.p.clj-kondo]
   [elin.util.file :as e.u.file]))

(defn- get-project-root-directory
  [host]
  (e/let [cwd (e.f.vim/get-current-working-directory!! host)
          root (or (e.u.file/get-project-root-directory cwd)
                   (e/not-found))]
    (.getAbsolutePath root)))

(defn- get-cache-file-path
  [user-dir]
  (.getAbsolutePath
   (io/file (e.u.file/get-cache-directory)
            (str (str/replace user-dir "/" "_")
                 ".edn"))))

(def clj-kondo-available?
  (try
    (b.pods/load-pod "clj-kondo")
    true
    (catch Exception _ false)))

(when clj-kondo-available?
  (require '[pod.borkdude.clj-kondo :as clj-kondo]))

(defrecord CljKondo
  [lazy-host analyzing?-atom analyzed-atom config]
  component/Lifecycle
  (start [this]
    (assoc this
           :analyzing?-atom (atom false)
           :analyzed-atom (atom nil)))

  (stop [this]
    (dissoc this :analyzing?-atom :analyzed-atom))

  e.p.clj-kondo/ICljKondo
  (analyze [this]
    (if (e.p.clj-kondo/analyzing? this)
      (async/go (e/busy {:message "clj-kondo is already analyzing"}))
      (do (reset! analyzing?-atom true)
          (async/thread
            (try
              #_{:clj-kondo/ignore [:unresolved-namespace]}
              (e/let [project-root-dir (get-project-root-directory lazy-host)
                      res (clj-kondo/run! {:lint [project-root-dir]
                                           :config config})
                      cache-path (get-cache-file-path project-root-dir)]
                (spit cache-path (pr-str res))
                (reset! analyzed-atom res))
              (finally
                (reset! analyzing?-atom false)))))))

  (restore [this]
    (if (e.p.clj-kondo/analyzing? this)
      (async/go (e/busy {:message "clj-kondo is already analyzing"}))
      (do (reset! analyzing?-atom true)
          (async/thread
            (try
              (e/let [project-root-dir (get-project-root-directory lazy-host)
                      cache-file (get-cache-file-path project-root-dir)
                      analyzed (with-open [r (io/reader cache-file)]
                                 (edn/read (java.io.PushbackReader. r)))]
                (reset! analyzed-atom analyzed))
              (finally
                (reset! analyzing?-atom false)))))))

  (analyzing? [_]
    @analyzing?-atom)

  (analyzed? [_]
    (some? @analyzed-atom))

  (analysis [this]
    (when (e.p.clj-kondo/analyzed? this)
      (:analysis @analyzed-atom))))

(defn new-clj-kondo
  [config]
  (map->CljKondo (or (:clj-kondo config) {})))
