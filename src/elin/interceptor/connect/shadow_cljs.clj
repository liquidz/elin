(ns elin.interceptor.connect.shadow-cljs
  (:require
   [clojure.core.async :as async]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.constant.nrepl :as e.c.nrepl]
   [elin.function.evaluate :as e.f.evaluate]
   [elin.function.select :as e.f.select]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema.nrepl :as e.s.nrepl]
   [elin.util.file :as e.u.file]
   [elin.util.interceptor :as e.u.interceptor]
   [exoscale.interceptor :as ix]
   [malli.core :as m]))

(def ^:private shadow-cljs-build-ids-code
  (str `(do (require 'shadow.cljs.devtools.api)
            (->> (shadow.cljs.devtools.api/get-build-ids)
                 (filter shadow.cljs.devtools.api/worker-running?)))))

(m/=> find-shadow-cljs-port-file [:-> string? [:maybe e.s.nrepl/?PortFile]])
(defn- find-shadow-cljs-port-file
  [cwd]
  (when-let [file (some-> (e.u.file/find-file-in-parent-directories cwd ".shadow-cljs")
                          (io/file "nrepl.port"))]
    (when (.exists file)
      {:language e.c.nrepl/lang-clojurescript
       :port-file (.getAbsolutePath file)
       :port (some->> file slurp Long/parseLong)})))

(def detect-shadow-cljs-port
  "Detect available `shadow-cljs` ports.
  If there are multiple port candidates, prompt the user to select one.

  When a `shadow-cljs` port is selected and there are multiple build IDs in `shadow-cljs`, prompt the user to select a build ID as well.
  The selected build ID will be watched on `shadow-cljs`."
  {:kind e.c.interceptor/connect
   :enter (-> (fn [{:as ctx :component/keys [host] :keys [hostname port-file]}]
                (let [{:keys [default-hostname]} (e.u.interceptor/config ctx #'detect-shadow-cljs-port)
                      cwd (async/<!! (e.p.host/get-current-working-directory! host))
                      file-sep (e.u.file/guess-file-separator cwd)
                      project-root (try
                                     (str (.getAbsolutePath (e.u.file/get-project-root-directory cwd))
                                          file-sep)
                                     (catch Exception _
                                       (str cwd
                                            file-sep)))
                      shadow-cljs-port-file (find-shadow-cljs-port-file cwd)
                      selected-port-file (when shadow-cljs-port-file
                                           (->> [port-file
                                                 (:port-file shadow-cljs-port-file)]
                                                (remove nil?)
                                                (map #(str/replace-first % project-root ""))
                                                (e.f.select/select-from-candidates ctx)))]
                  (cond
                    ;; no shadow-cljs port found
                    (not shadow-cljs-port-file)
                    ctx

                    ;; not selected
                    (not selected-port-file)
                    (assoc ctx
                           :hostname nil
                           :port 0)

                    ;; shadow-cljs is selected
                    (str/ends-with? (:port-file shadow-cljs-port-file)
                                    selected-port-file)
                    (assoc ctx
                           :hostname (or hostname default-hostname)
                           :port (:port shadow-cljs-port-file)
                           :language (:language shadow-cljs-port-file)
                           :port-file (:port-file shadow-cljs-port-file))

                    ;; default nrepl is selected
                    :else
                    ctx)))
              ;; Do nothing if the hostname and port number are specified, not automatically detected
              (ix/when #(not (and (:hostname %) (:port %) (not (:port-file %))))))

   :leave (-> (fn [{:as ctx :component/keys [nrepl]}]
                (let [{:keys [language port-file]} (e.p.nrepl/current-client nrepl)]
                  ;; When shadow-cljs port is selected
                  (when (and (= e.c.nrepl/lang-clojurescript language)
                             (string? port-file)
                             (str/includes? port-file "shadow-cljs"))
                    ;; Select the build ID
                    (let [build-id (-> (e.f.evaluate/evaluate-code ctx shadow-cljs-build-ids-code)
                                       (get-in [:response :value])
                                       (edn/read-string)
                                       (->> (e.f.select/select-from-candidates ctx)))]
                      (->> (str `(do (shadow.cljs.devtools.api/watch ~build-id)
                                     (shadow.cljs.devtools.api/nrepl-select ~build-id)))
                           (e.f.evaluate/evaluate-code ctx))))))
              (ix/discard))})
