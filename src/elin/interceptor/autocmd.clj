(ns elin.interceptor.autocmd
  (:require
   [clojure.core.async :as async]
   [clojure.string :as str]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.error :as e]
   [elin.function.connect :as e.f.connect]
   [elin.function.evaluate :as e.f.evaluate]
   [elin.function.nrepl :as e.f.nrepl]
   [elin.function.nrepl.namespace :as e.f.n.namespace]
   [elin.function.sexpr :as e.f.sexpr]
   [elin.handler.evaluate :as e.h.evaluate]
   [elin.message :as e.message]
   [elin.protocol.clj-kondo :as e.p.clj-kondo]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.util.file :as e.u.file]
   [elin.util.interceptor :as e.u.interceptor]
   [exoscale.interceptor :as ix]
   [pogonos.core :as pogonos]
   [taoensso.timbre :as timbre]))

(def ^:priavte ns-created-var-name
  "b:elin_ns_created")

(def deinitialize
  {:kind e.c.interceptor/autocmd
   :enter (-> (fn [{:as ctx :component/keys [nrepl]}]
                ;; NOTE
                ;; Use `e.f.connect/disconnect` to execute `disconnect` interceptors
                ;; instead of `e.p.nrepl/remove-all!`
                (doseq [client (e.p.nrepl/all-clients nrepl)]
                  (e.f.connect/disconnect ctx client)))

              (ix/when #(= "VimLeave" (:autocmd-type %)))
              (ix/discard))})

(defn- ns-not-created?
  [{:component/keys [host nrepl]}]
  (and (not (e.p.nrepl/disconnected? nrepl))
       (nil? (async/<!! (e.p.host/get-variable! host ns-created-var-name)))))

(defn- bufread-or-bufenter?
  [{:keys [autocmd-type]}]
  (contains? #{"BufRead" "BufEnter"} autocmd-type))

(def ns-create
  {:kind e.c.interceptor/autocmd
   :enter (-> (fn [{:as ctx :component/keys [host nrepl]}]
                (e/let [ns-str (e.f.sexpr/get-namespace ctx)
                        ns-sym (or (symbol ns-str)
                                   (e/incorrect))]
                  (->> `(when-not (clojure.core/find-ns '~ns-sym)
                          (clojure.core/create-ns '~ns-sym)
                          (clojure.core/in-ns '~ns-sym)
                          (clojure.core/refer-clojure))
                       (str)
                       (e.f.nrepl/eval!! nrepl))
                  (async/<!! (e.p.host/set-variable! host ns-created-var-name true))))
              (ix/when #(and (bufread-or-bufenter? %)
                             (ns-not-created? %)))
              (ix/discard))})

(def ns-load
  {:kind e.c.interceptor/autocmd
   :enter (-> (fn [{:as ctx :component/keys [host]}]
                (e.h.evaluate/evaluate-current-buffer ctx)
                (e.p.host/set-variable! host ns-created-var-name true))
              (ix/when #(and (bufread-or-bufenter? %)
                             (ns-not-created? %)))
              (ix/discard))})

(defn- empty-buffer?
  [{:component/keys [host] :keys [autocmd-type]}]
  (or (= "BufNewFile" autocmd-type)
      (and (= "BufRead" autocmd-type)
           (try
             (->> (async/<!! (e.p.host/get-lines host))
                  (str/join "")
                  (str/trim)
                  (empty?))
             (catch Exception ex
               (timbre/debug "Failed to fetch buffer lines" ex)
               false)))))

(def skeleton
  {:kind e.c.interceptor/autocmd
   :enter (-> (fn [{:as ctx :component/keys [host]}]
                (e/let [config (e.u.interceptor/config ctx #'skeleton)
                        path (async/<!! (e.p.host/get-current-file-path! host))
                        ns-str (or (e.f.n.namespace/guess-namespace-from-path path)
                                   ;; TODO fallback to another process
                                   (e/fault))
                        ;; TODO cljs, cljc support
                        lang "clojure"
                        test? (str/ends-with? ns-str "-test")
                        template (or (get-in config [(keyword lang) (if test? :test :src)])
                                     (e/not-found))
                        params (cond-> {:path path
                                        :ns ns-str
                                        :lang lang
                                        :test? test?}
                                 test?
                                 (assoc :source-ns (str/replace ns-str #"-test$" "")))
                        ns-form (pogonos/render-string template params)]
                  (e.p.host/set-to-current-buffer host (str/split-lines ns-form))
                  (e.f.evaluate/evaluate-code ctx ns-form {:file path})))
              (ix/when empty-buffer?)
              (ix/discard))})

(def clj-kondo-analyzing
  {:kind e.c.interceptor/autocmd
   :leave (-> (fn [{:component/keys [clj-kondo]}]
                (e.p.clj-kondo/analyze clj-kondo))
              (ix/when #(= "BufWritePost" (:autocmd-type %)))
              (ix/discard))})

(def switch-connection
  {:kind e.c.interceptor/autocmd
   :enter (-> (fn [{:component/keys [host nrepl]}]
                (let [clients (e.p.nrepl/all-clients nrepl)
                      ext (some-> (e.p.host/get-current-file-path! host)
                                  (async/<!!)
                                  (e.u.file/get-file-extension))
                      lang (:language (e.p.nrepl/current-client nrepl))]
                  (cond
                    (and (= "clojure" lang)
                         (contains? #{".clj" ".cljc" ".cljd"} ext))
                    nil

                    (and (= "clojurescript" lang)
                         (contains? #{".cljs" ".cljc"} ext))
                    nil

                    ;; Switch to clojure connection
                    (= ".clj" ext)
                    (when-let [client (some->> clients
                                               (filter #(= "clojure" (:language %)))
                                               (first))]
                      (e.p.nrepl/switch-client! nrepl client)
                      (e.message/info host (format "Switched to %s connection."
                                                   (:language client))))

                    ;; Switch to clojurescript connection
                    (= ".cljs" ext)
                    (when-let [client (some->> clients
                                               (filter #(= "clojurescript" (:language %)))
                                               (first))]
                      (e.p.nrepl/switch-client! nrepl client)
                      (e.message/info host (format "Switched to %s connection."
                                                   (:language client))))

                    :else
                    nil)))
              (ix/when #(and (bufread-or-bufenter? %)
                             (> (count (e.p.nrepl/all-clients (:component/nrepl %))) 1)))
              (ix/discard))})
