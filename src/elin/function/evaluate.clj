(ns elin.function.evaluate
  (:require
   [clojure.core.async :as async]
   [clojure.string :as str]
   [elin.error :as e]
   [elin.function.nrepl :as e.f.nrepl]
   [elin.function.sexpr :as e.f.sexpr]
   [elin.protocol.host :as e.p.host]
   [elin.util.nrepl :as e.u.nrepl]))

(defn- eval!!
  [nrepl code options]
  (e/let [options (reduce-kv (fn [accm k v]
                               (if v
                                 (assoc accm k v)
                                 accm))
                             {:nrepl.middleware.print/stream? 1}
                             options)
          resp (e.f.nrepl/eval!! nrepl code options)]
    (if (e.u.nrepl/has-status? resp "eval-error")
      (e/fault {:message (-> (:err resp)
                             (str)
                             (str/trim)
                             (str/replace #"\r?\n" " "))})
      {:code code
       :options options
       :response resp})))

(defn evaluate-current-top-list
  ([elin]
   (evaluate-current-top-list elin {}))
  ([{:as elin :component/keys [nrepl host]} options]
   (e/let [{cur-lnum :lnum cur-col :col} (async/<!! (e.p.host/get-cursor-position! host))
           ns-str (e.f.sexpr/get-namespace elin)
           path (async/<!! (e.p.host/get-current-file-path! host))
           {:keys [code lnum col]} (e.f.sexpr/get-top-list elin cur-lnum cur-col)]
     (eval!! nrepl code (merge options
                               {:line lnum
                                :column col
                                :cursor-line cur-lnum
                                :cursor-column cur-col
                                :ns ns-str
                                :file path})))))

(defn evaluate-current-list
  ([elin]
   (evaluate-current-list elin {}))
  ([{:as elin :component/keys [nrepl host]} options]
   (e/let [{cur-lnum :lnum cur-col :col} (async/<!! (e.p.host/get-cursor-position! host))
           ns-str (e.f.sexpr/get-namespace elin)
           path (async/<!! (e.p.host/get-current-file-path! host))
           {:keys [code lnum col]} (e.f.sexpr/get-list elin cur-lnum cur-col)]
     (eval!! nrepl code (merge options
                               {:line lnum
                                :column col
                                :cursor-line cur-lnum
                                :cursor-column cur-col
                                :ns ns-str
                                :file path})))))

(defn evaluate-current-expr
  ([elin]
   (evaluate-current-expr elin {}))
  ([{:as elin :component/keys [nrepl host]} options]
   (e/let [{cur-lnum :lnum cur-col :col} (async/<!! (e.p.host/get-cursor-position! host))
           ns-str (e.f.sexpr/get-namespace elin)
           path (async/<!! (e.p.host/get-current-file-path! host))
           {:keys [code lnum col]} (e.f.sexpr/get-expr elin cur-lnum cur-col)]
     (eval!! nrepl code (merge options
                               {:line lnum
                                :column col
                                :cursor-line cur-lnum
                                :cursor-column cur-col
                                :ns ns-str
                                :file path})))))

(defn evaluate-namespace-form
  ([elin]
   (evaluate-namespace-form elin {}))
  ([{:as elin :component/keys [nrepl host]} options]
   (e/let [{ns-form :code} (e.f.sexpr/get-namespace-sexpr elin)
           path (async/<!! (e.p.host/get-current-file-path! host))]
     (eval!! nrepl ns-form (merge options
                                  {:file path})))))
