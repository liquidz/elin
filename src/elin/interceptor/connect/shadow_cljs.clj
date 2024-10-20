(ns elin.interceptor.connect.shadow-cljs
  (:require
   [clojure.core.async :as async]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.function.evaluate :as e.f.evaluate]
   [elin.function.select :as e.f.select]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema.nrepl :as e.s.nrepl]
   [elin.util.file :as e.u.file]
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
      {:language "clojurescript"
       :port-file (.getAbsolutePath file)
       :port (some->> file slurp Long/parseLong)})))

(def detect-shadow-cljs-port
  {:kind e.c.interceptor/connect
   :enter (-> (fn [{:as ctx :component/keys [host] :keys [port-file]}]
                (let [cwd (async/<!! (e.p.host/get-current-working-directory! host))
                      shadow-cljs-port-file (find-shadow-cljs-port-file cwd)
                      selected-port-file (->> [port-file
                                               (:port-file shadow-cljs-port-file)]
                                              (remove nil?)
                                              (e.f.select/select-from-candidates ctx))]
                  (if (= (:port-file shadow-cljs-port-file) selected-port-file)
                    (assoc ctx
                           :port (:port shadow-cljs-port-file)
                           :language (:language shadow-cljs-port-file)
                           :port-file (:port-file shadow-cljs-port-file))
                    ctx)))
              ;; Do nothing if the hostname and port number are specified, not automatically detected
              (ix/when #(not (and (:hostname %) (:port %) (not (:port-file %))))))

   :leave (-> (fn [{:as ctx :component/keys [nrepl]}]
                (let [{:keys [language port-file]} (e.p.nrepl/current-client nrepl)]
                  (when (and (= "clojurescript" language)
                             (str/includes? port-file "shadow-cljs"))

                    (let [build-id (-> (e.f.evaluate/evaluate-code ctx shadow-cljs-build-ids-code)
                                       (get-in [:response :value])
                                       (edn/read-string)
                                       (->> (e.f.select/select-from-candidates ctx)))]
                      (->> (str `(do (shadow.cljs.devtools.api/watch ~build-id)
                                     (shadow.cljs.devtools.api/nrepl-select ~build-id)))
                           (e.f.evaluate/evaluate-code ctx))))))
              (ix/discard))})
