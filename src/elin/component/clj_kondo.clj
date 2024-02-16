(ns elin.component.clj-kondo
  (:require
   [babashka.pods :as b.pods]
   [cheshire.core :as json]
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [com.stuartsierra.component :as component]
   [elin.error :as e]
   [elin.function.nrepl.system :as e.f.n.system]
   [elin.function.vim :as e.f.vim]
   [elin.protocol.clj-kondo :as e.p.clj-kondo]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.util.file :as e.u.file]
   [elin.util.function :as e.u.function]))

(defn- get-parent-absolute-path
  [path]
  (.getAbsolutePath (.getParentFile (io/file path))))

(defn- get-user-dir*
  [nrepl lazy-host]
  (e/let [user-dir (e.f.n.system/get-user-dir nrepl)]
    (if (empty? user-dir)
      (e/-> (e.f.vim/get-current-file-path!! lazy-host)
            (get-parent-absolute-path))
      user-dir)))
(def get-user-dir
  (e.u.function/memoize-by
   (comp e.p.nrepl/current-session first)
   get-user-dir*))

(defn- get-cache-file-path
  [user-dir]
  (.getAbsolutePath
   (io/file (e.u.file/get-cache-directory)
            (str (str/replace user-dir "/" "_")
                 ".json"))))

(def clj-kondo-available?
  (try
    (b.pods/load-pod "clj-kondo")
    true
    (catch Exception _ false)))

(when clj-kondo-available?
  (require '[pod.borkdude.clj-kondo :as clj-kondo]))

(defrecord CljKondo
  [lazy-host nrepl analyzing?-atom analyzed-atom]
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
              (e/let [user-dir (get-user-dir nrepl lazy-host)
                      res (clj-kondo/run! {:lint [user-dir]
                                           :config {:output {:analysis {:protocol-impls true}}}})
                      cache-path (get-cache-file-path user-dir)]
                (spit cache-path (json/generate-string res))
                (reset! analyzed-atom res))
              (finally
                (reset! analyzing?-atom false)))))))

  (restore [this]
    (if (e.p.clj-kondo/analyzing? this)
      (async/go (e/busy {:message "clj-kondo is already analyzing"}))
      (do (reset! analyzing?-atom true)
          (async/thread
            (try
              (e/let [user-dir (get-user-dir nrepl lazy-host)
                      cache-file (get-cache-file-path user-dir)
                      analyzed (json/parse-stream (io/reader cache-file) keyword)]
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
  [_]
  (map->CljKondo {}))

(comment
  (def result (clj-kondo/run! {:lint ["."]
                               :config {:output {:analysis {:protocol-impls true}}}}))

  (def clj-kondo (map->CljKondo {:analyzed-atom (atom result)})))

(defn namespace-usages
  [clj-kondo]
  (when-let [ana (e.p.clj-kondo/analysis clj-kondo)]
    (or (:namespace-usages ana)
        [])))

(defn var-usages
  [clj-kondo]
  (when-let [ana (e.p.clj-kondo/analysis clj-kondo)]
    (or (:var-usages ana)
        [])))

(defn namespace-definitions
  [clj-kondo]
  (when-let [ana (e.p.clj-kondo/analysis clj-kondo)]
    (or (:namespace-definitions ana)
        [])))

(defn local-usages
  [clj-kondo]
  (when-let [ana (e.p.clj-kondo/analysis clj-kondo)]
    (or (:local-usages ana)
        [])))

(defn local-definitions
  [clj-kondo]
  (when-let [ana (e.p.clj-kondo/analysis clj-kondo)]
    (or (:locals ana)
        [])))

(defn keywords [clj-kondo]
  (when-let [ana (e.p.clj-kondo/analysis clj-kondo)]
    (or (:keywords ana)
        [])))

(defn keyword-usages
  [clj-kondo keyword']
  (when-let [keywords' (keywords clj-kondo)]
    (let [[kw-ns kw-name] ((juxt namespace name) keyword')
          pred (if kw-ns
                 #(and (= kw-ns (:ns %))
                       (= kw-name (:name %)))
                 #(= kw-name (:name %)))]
      (filter pred keywords'))))

(defn keyword-definition
  [clj-kondo filename keyword']
  (when-let [keywords' (keywords clj-kondo)]
    (let [[kw-ns kw-name] ((juxt namespace name) keyword')]
      (if kw-ns
        (when-let [targets (->> keywords'
                                (filter #(and (= filename (:filename %))
                                              (= kw-ns (:alias %))
                                              (= kw-name (:name %))))
                                (seq))]
          (let [target-ns (-> targets (first) (:ns) (or ""))
                target-name (-> targets (first) (:name) (or ""))]
            (->> keywords'
                 (filter #(and (= target-ns (:ns %))
                               (= target-name (:name %))
                               (not= "" (:reg %))))
                 (first))))
        (->> keywords'
             (filter #(and (= filename (:filename %))
                           (= "" (:alias %))
                           (= kw-name (:name %))
                           (not= "" (:reg %))))
             (first))))))

(defn references
  [clj-kondo ns-str var-name]
  (let [var-name (str/replace-first var-name #"^'+" "")]
    (some->> (var-usages clj-kondo)
             (filter #(and (= ns-str (:to %))
                           (= var-name (:name %))))
             (sort-by :filename))))
