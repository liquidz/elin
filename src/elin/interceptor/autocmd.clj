(ns elin.interceptor.autocmd
  (:require
   [clojure.core.async :as async]
   [clojure.string :as str]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.error :as e]
   [elin.function.nrepl :as e.f.nrepl]
   [elin.function.nrepl.namespace :as e.f.n.namespace]
   [elin.function.vim :as e.f.vim]
   [elin.function.vim.sexp :as e.f.v.sexp]
   [elin.protocol.clj-kondo :as e.p.clj-kondo]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.nrepl :as e.p.nrepl]
   [exoscale.interceptor :as ix]))

(def ^:priavte ns-created-var-name
  "b:elin_ns_created")

(def deinitialize-interceptor
  {:name ::deinitialize-interceptor
   :kind e.c.interceptor/autocmd
   :enter (-> (fn [{:component/keys [nrepl]}]
                (e.p.nrepl/remove-all! nrepl))
              (ix/when #(= "VimLeave" (:autocmd-type %)))
              (ix/discard))})

(def ns-create-interceptor
  {:name ::ns-create-interceptor
   :kind e.c.interceptor/autocmd
   :enter (-> (fn [{:component/keys [host nrepl] :keys [autocmd-type]}]
                (when (and (contains? #{"BufRead" "BufEnter"} autocmd-type)
                           (not (e.p.nrepl/disconnected? nrepl))
                           (nil? (async/<!! (e.p.host/get-variable! host ns-created-var-name))))
                  (e/let [ns-str (e.f.v.sexp/get-namespace!! host)
                          ns-sym (or (symbol ns-str)
                                     (e/incorrect))]
                    (->> `(when-not (clojure.core/find-ns '~ns-sym)
                            (clojure.core/create-ns '~ns-sym)
                            (clojure.core/in-ns '~ns-sym)
                            (clojure.core/refer-clojure))
                         (str)
                         (e.f.nrepl/eval!! nrepl))
                    (async/<!! (e.p.host/set-variable! host ns-created-var-name true)))))
              (ix/discard))})

(defmulti generate-skeleton
  (fn [{:keys [lang test?]}]
    [lang test?]))

(defmethod generate-skeleton :default [_] nil)

(defmethod generate-skeleton ["clojure" false]
  [{:keys [ns-str]}]
  (format "(ns %s)" ns-str))

(defmethod generate-skeleton ["clojure" true]
  [{:keys [ns-str]}]
  (let [src-ns (str/replace ns-str #"-test$" "")]
    (format "(ns %s\n  (:require\n   [clojure.test :as t]\n   [%s :as sut]))"
            ns-str
            src-ns)))

(def skeleton-interceptor
  {:name ::skelton-interceptor
   :kind e.c.interceptor/autocmd
   :enter (-> (fn [{:component/keys [host]}]
                (e/let [path (async/<!! (e.p.host/get-current-file-path! host))
                        ns-str (or (e.f.n.namespace/guess-namespace-from-path path)
                                   ;; TODO fallback to another process
                                   (e/fault))
                        param {:path path
                               :ns-str ns-str
                               ;; TODO cljs, cljc support
                               :lang "clojure"
                               :test? (str/ends-with? ns-str "-test")}
                        ns-form-lines (->> (generate-skeleton param)
                                           (str/split-lines))]
                  (e.f.vim/notify host "elin#internal#buffer#set" ["%" ns-form-lines])))
              (ix/when #(= "BufNewFile" (:autocmd-type %)))
              (ix/discard))})

(def clj-kondo-analyzing-interceptor
  {:name ::clj-kondo-analyzing-interceptor
   :kind e.c.interceptor/autocmd
   :leave (-> (fn [{:component/keys [clj-kondo]}]
                (e.p.clj-kondo/analyze clj-kondo))
              (ix/when #(= "BufWritePost" (:autocmd-type %)))
              (ix/discard))})
