(ns elin.handler.namespace
  (:require
   [clojure.core.async :as async]
   [clojure.string :as str]
   [elin.error :as e]
   [elin.function.evaluate :as e.f.evaluate]
   [elin.function.namespace :as e.f.namespace]
   [elin.function.sexpr :as e.f.sexpr]
   [elin.message :as e.message]
   [elin.protocol.host :as e.p.host]
   [elin.util.sexp :as e.u.sexp]))

(defn- has-namespace?
  [form ns-sym]
  (-> (re-pattern (str (str/replace (str ns-sym) "." "\\.")
                       "[ \r\n\t\\]\\)]"))
      (re-seq form)
      (some?)))

(defn add-namespace*
  [{:as elin :component/keys [handler host] :keys [message]}]
  (e/let [favorites (get-in handler [:config-map (symbol #'add-namespace*) :favorites])
          ns-sym (-> (:params message)
                     (first)
                     (symbol)
                     (or (e/not-found)))
          default-alias-sym (or (get favorites ns-sym)
                                (e.f.namespace/most-used-namespace-alias elin ns-sym))
          alias-str (async/<!! (e.p.host/input! host
                                                (format "Alias for '%s': " ns-sym)
                                                (str default-alias-sym)))
          alias-sym (when (seq alias-str)
                      (symbol alias-str))
          {ns-form :code} (e.f.sexpr/get-namespace-form elin)]
    (if (has-namespace? ns-form ns-sym)
      (e.message/warning host (format "'%s' already exists." ns-sym))
      (e/let [ns-form' (e.u.sexp/add-require ns-form ns-sym alias-sym)]
        (e.f.sexpr/replace-namespace-form elin ns-form')
        (e.f.evaluate/evaluate-namespace-form elin)
        (e.message/info host (if alias-sym
                               (format "'%s' added as '%s'."
                                       ns-sym alias-sym)
                               (format "'%s' added."
                                       ns-sym)))))))

(defn add-namespace
  [{:as elin :component/keys [host]}]
  (let [coll (e.f.namespace/get-namespaces elin)]
    (e.p.host/select-from-candidates host coll (symbol #'add-namespace*))))

(defn resolve-missing-namespace*
  [{:as elin :component/keys [host] :keys [message]}]
  (e/let [[alias-str ns-str] (:params message)
          alias-sym (some-> alias-str
                            (symbol))
          ns-sym (some-> ns-str
                         (symbol))
          _ (when (or (not alias-sym) (not ns-sym))
              (e/not-found))
          {ns-form :code} (e.f.sexpr/get-namespace-form elin)
          ns-form' (e.u.sexp/add-require ns-form ns-sym alias-sym)]
    (e.f.sexpr/replace-namespace-form elin ns-form')
    (e.f.evaluate/evaluate-namespace-form elin)
    (e.message/info host (format "'%s' added as '%s'." ns-sym alias-sym))))

(defn resolve-missing-namespace
  [{:as elin :component/keys [handler host]}]
  (e/let [favorites (get-in handler [:config-map (symbol #'resolve-missing-namespace) :favorites])
          {:keys [lnum col]} (async/<!! (e.p.host/get-cursor-position! host))
          {:keys [code]} (e.f.sexpr/get-expr elin lnum col)
          [alias-str var-str] (str/split code #"/" 2)
          _ (when-not var-str
              (e/incorrect {:message (format "Fully qualified symbol is required: %s" code)}))
          alias-sym (symbol alias-str)
          resp (e.f.namespace/resolve-missing-namespace elin code favorites)]
    (condp = (count resp)
      0
      (e.message/warning host "There are no candidates.")

      1
      (resolve-missing-namespace*
       (assoc elin :message {:params [alias-sym (:name (first resp))]}))

      ;; else
      (e.p.host/select-from-candidates
       host (map :name resp) (symbol #'resolve-missing-namespace*) [alias-str]))))
