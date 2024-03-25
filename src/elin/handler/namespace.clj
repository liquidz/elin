(ns elin.handler.namespace
  (:require
   [clojure.string :as str]
   [elin.error :as e]
   [elin.function.core.namespace :as e.f.c.namespace]
   [elin.function.nrepl.vim :as e.f.n.vim]
   [elin.function.vim :as e.f.vim]
   [elin.function.vim.sexp :as e.f.v.sexp]
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
  [{:as elin :component/keys [handler host nrepl] :keys [message]}]
  (e/let [favorites (get-in handler [:config-map (symbol #'add-namespace*) :favorites])
          ns-sym (-> (:params message)
                     (first)
                     (symbol)
                     (or (e/not-found)))
          default-alias-sym (or (get favorites ns-sym)
                                (e.f.c.namespace/most-used-namespace-alias elin ns-sym))
          alias-str (e.p.host/input!! host
                                      (format "Alias for '%s': " ns-sym)
                                      (str default-alias-sym))
          alias-sym (when (seq alias-str)
                      (symbol alias-str))
          ns-form (e.f.v.sexp/get-namespace-form!! host)]
    (if (has-namespace? ns-form ns-sym)
      (e.message/warning host (format "'%s' already exists." ns-sym))
      (e/let [ns-form' (e.u.sexp/add-require ns-form ns-sym alias-sym)]
        (e.f.v.sexp/replace-namespace-form!! host ns-form')
        (e.f.n.vim/evaluate-namespace-form!! {:host host :nrepl nrepl})
        (e.message/info host (if alias-sym
                               (format "'%s' added as '%s'."
                                       ns-sym alias-sym)
                               (format "'%s' added."
                                       ns-sym)))))))

(defn add-namespace
  [{:as elin :component/keys [host]}]
  (let [coll (e.f.c.namespace/get-namespaces elin)]
    (e.f.vim/notify host "elin#internal#select" [coll (symbol #'add-namespace*)])))

(defn resolve-missing-namespace*
  [{:component/keys [host nrepl] :keys [message]}]
  (e/let [[alias-str ns-str] (:params message)
          alias-sym (some-> alias-str
                            (symbol))
          ns-sym (some-> ns-str
                         (symbol))
          _ (when (or (not alias-sym) (not ns-sym))
              (e/not-found))
          ns-form (e.f.v.sexp/get-namespace-form!! host)
          ns-form' (e.u.sexp/add-require ns-form ns-sym alias-sym)]
    (e.f.v.sexp/replace-namespace-form!! host ns-form')
    (e.f.n.vim/evaluate-namespace-form!! {:host host :nrepl nrepl})
    (e.message/info host (format "'%s' added as '%s'." ns-sym alias-sym))))

(defn resolve-missing-namespace
  [{:as elin :component/keys [handler host]}]
  (e/let [favorites (get-in handler [:config-map (symbol #'resolve-missing-namespace) :favorites])
          {:keys [lnum col]} (e.p.host/get-cursor-position!! host)
          ;; ns-str (e.f.v.sexp/get-namespace!! host)
          {:keys [code]} (e.f.v.sexp/get-expr!! host lnum col)
          [alias-str var-str] (str/split code #"/" 2)
          _ (when-not var-str
              (e/incorrect {:message (format "Fully qualified symbol is required: %s" code)}))
          alias-sym (symbol alias-str)
          resp (e.f.c.namespace/resolve-missing-namespace elin code favorites)]
    (condp = (count resp)
      0
      (e.message/warning host "There are no candidates.")

      1
      (resolve-missing-namespace*
       (assoc elin :message {:params [alias-sym (:name (first resp))]}))

      ;; else
      (e.f.vim/notify host "elin#internal#select" [(map :name resp)
                                                   (symbol #'resolve-missing-namespace*)
                                                   [alias-str]]))))
