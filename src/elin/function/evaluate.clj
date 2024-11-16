(ns elin.function.evaluate
  (:require
   [clojure.core.async :as async]
   [clojure.string :as str]
   [elin.error :as e]
   [elin.function.mark :as e.f.mark]
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

(defn evaluate-code
  ([elin code]
   (evaluate-code elin code {}))
  ([{:component/keys [nrepl]} code options]
   (eval!! nrepl code options)))

(defn evaluate-current-top-list
  ([elin]
   (evaluate-current-top-list elin {}))
  ([{:as elin :component/keys [nrepl host]} options]
   (e/let [{cur-lnum :lnum cur-col :col} (async/<!! (e.p.host/get-cursor-position! host))
           ns-str (e/error-or (e.f.sexpr/get-namespace elin))
           path (async/<!! (e.p.host/get-current-file-path! host))
           {:keys [code lnum col]} (e.f.sexpr/get-top-list elin cur-lnum cur-col)
           params (cond-> {:line lnum
                           :column col
                           :cursor-line cur-lnum
                           :cursor-column cur-col
                           :file path}
                    ns-str
                    (assoc :ns ns-str))]
     (eval!! nrepl code (merge options params)))))

(defn evaluate-current-list
  ([elin]
   (evaluate-current-list elin {}))
  ([{:as elin :component/keys [nrepl host]} options]
   (e/let [{cur-lnum :lnum cur-col :col} (async/<!! (e.p.host/get-cursor-position! host))
           ns-str (e/error-or (e.f.sexpr/get-namespace elin))
           path (async/<!! (e.p.host/get-current-file-path! host))
           {:keys [code lnum col]} (e.f.sexpr/get-list elin cur-lnum cur-col)
           params (cond-> {:line lnum
                           :column col
                           :cursor-line cur-lnum
                           :cursor-column cur-col
                           :file path}
                    ns-str
                    (assoc :ns ns-str))]
     (eval!! nrepl code (merge options params)))))

(defn evaluate-current-expr
  ([elin]
   (evaluate-current-expr elin {}))
  ([{:as elin :component/keys [nrepl host]} options]
   (e/let [{cur-lnum :lnum cur-col :col} (async/<!! (e.p.host/get-cursor-position! host))
           ns-str (e/error-or (e.f.sexpr/get-namespace elin))
           path (async/<!! (e.p.host/get-current-file-path! host))
           {:keys [code lnum col]} (e.f.sexpr/get-expr elin cur-lnum cur-col)
           params (cond-> {:line lnum
                           :column col
                           :cursor-line cur-lnum
                           :cursor-column cur-col
                           :file path}
                    ns-str
                    (assoc :ns ns-str))]
     (eval!! nrepl code (merge options params)))))

(defn evaluate-namespace-form
  ([elin]
   (evaluate-namespace-form elin {}))
  ([{:as elin :component/keys [nrepl host]} options]
   (e/let [{ns-form :code} (e.f.sexpr/get-namespace-sexpr elin)
           path (async/<!! (e.p.host/get-current-file-path! host))]
     (eval!! nrepl ns-form (merge options
                                  {:file path})))))

(defn evaluate-at-mark
  ([elin mark-id]
   (evaluate-at-mark elin mark-id {}))
  ([{:as elin :component/keys [host nrepl]} mark-id options]
   (e/let [{cur-lnum :lnum cur-col :col} (async/<!! (e.p.host/get-cursor-position! host))
           {:as mark-pos :keys [path]} (e.f.mark/get-by-id elin mark-id)
           {:keys [code lnum col]} (e.f.sexpr/get-list elin path (:lnum mark-pos) (:col mark-pos))
           ns-str (e.f.sexpr/get-namespace elin path)]
     (eval!! nrepl code (merge options
                               {:line lnum
                                :column col
                                :cursor-line cur-lnum
                                :cursor-column cur-col
                                :ns ns-str
                                :file path})))))

(defn expand-1
  [{:component/keys [nrepl]} ns-str code]
  (e/let [code' (format "(clojure.core/macroexpand-1 '%s)" code)
          options (if (seq ns-str)
                    {:ns ns-str}
                    {})
          resp (e.f.nrepl/eval!! nrepl code' options)]
    resp))
