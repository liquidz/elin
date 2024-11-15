(ns elin.handler.namespace
  (:require
   [clojure.core.async :as async]
   [clojure.string :as str]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.error :as e]
   [elin.function.evaluate :as e.f.evaluate]
   [elin.function.namespace :as e.f.namespace]
   [elin.function.select :as e.f.select]
   [elin.function.sexpr :as e.f.sexpr]
   [elin.message :as e.message]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.util.map :as e.u.map]
   [elin.util.sexpr :as e.u.sexpr]
   [elin.util.string :as e.u.string]))

(declare add-libspec)
(declare add-missing-libspec)

(defn- has-namespace?
  [form ns-sym]
  (-> (re-pattern (str (str/replace (str ns-sym) "." "\\.")
                       "[ \r\n\t\\]\\)]"))
      (re-seq form)
      (some?)))

(defn add-libspec
  "Add libspec to namespace form."
  [{:as elin :component/keys [handler host interceptor]}]
  (e/let [candidates (e.f.namespace/get-namespaces elin)
          ns-sym (or (some-> (e.f.select/select-from-candidates elin candidates)
                             (symbol))
                     (e/interrupted))
          favorites (get-in handler [:config-map (symbol #'add-libspec) :favorites])
          default-alias-sym (or (get favorites ns-sym)
                                (e.f.namespace/most-used-namespace-alias elin ns-sym))
          alias-str (async/<!! (e.p.host/input! host
                                                (format "Alias for '%s': " ns-sym)
                                                (str default-alias-sym)))
          alias-sym (when (seq alias-str)
                      (symbol alias-str))
          {ns-form :code lnum :lnum col :col} (e.f.sexpr/get-namespace-sexpr elin)
          context (-> (e.u.map/select-keys-by-namespace elin :component)
                      (assoc :code ns-form))
          {:keys [result code]} (e.p.interceptor/execute
                                  interceptor e.c.interceptor/modify-code context
                                  (fn [{:as ctx :keys [code]}]
                                    (if (has-namespace? code ns-sym)
                                      (assoc ctx :result false)
                                      (e/let [code' (e.u.sexpr/add-require code ns-sym alias-sym)]
                                        (assoc ctx :result true :code code')))))]
    (when result
      (e.f.sexpr/replace-list-sexpr elin lnum col code)
      (e.f.evaluate/evaluate-namespace-form elin))
    {:result result
     :target ns-sym
     :alias alias-sym}))

(defn- add-missing-import*
  [{:as elin :component/keys [interceptor]} class-name-sym]
  (e/let [{ns-form :code lnum :lnum col :col} (e.f.sexpr/get-namespace-sexpr elin)
          context (-> (e.u.map/select-keys-by-namespace elin :component)
                      (assoc :code ns-form))
          {:keys [result code]} (e.p.interceptor/execute
                                  interceptor e.c.interceptor/modify-code context
                                  (fn [{:as ctx :keys [code]}]
                                      (e/let [code' (e.u.sexpr/add-import code class-name-sym)]
                                        (assoc ctx :result true :code code'))))]
    (when result
      (e.f.sexpr/replace-list-sexpr elin lnum col code)
      (e.f.evaluate/evaluate-namespace-form elin))
    {:result result
     :target class-name-sym}))

(defn- add-missing-require*
  [{:as elin :component/keys [interceptor]} alias-sym ns-sym]
  (e/let [{ns-form :code lnum :lnum col :col} (e.f.sexpr/get-namespace-sexpr elin)
          context (-> (e.u.map/select-keys-by-namespace elin :component)
                      (assoc :code ns-form))
          {:keys [result code]} (e.p.interceptor/execute
                                  interceptor e.c.interceptor/modify-code context
                                  (fn [{:as ctx :keys [code]}]
                                    (if (has-namespace? code ns-sym)
                                      (assoc ctx :result false)
                                      (e/let [code' (e.u.sexpr/add-require code ns-sym alias-sym)]
                                        (assoc ctx :result true :code code')))))]
    (when result
      (e.f.sexpr/replace-list-sexpr elin lnum col code)
      (e.f.evaluate/evaluate-namespace-form elin))
    {:result result
     :target ns-sym
     :alias alias-sym}))

(defn- add-missing-libspec*
  [elin ns-str alias-str]
  (let [alias-sym (some-> alias-str
                          (symbol))
        ns-sym (some-> ns-str
                       (symbol))]
    (cond
      (or (not alias-sym)
          (not ns-sym))
      (e/not-found)

      (and (e.u.string/java-class-name? alias-str)
           (e.u.string/java-class-name? ns-str))
      (add-missing-import* elin ns-sym)

      :else
      (add-missing-require* elin alias-sym ns-sym))))

(defn add-missing-libspec
  "Add missing libspec to namespace form."
  [{:as elin :component/keys [handler host]}]
  (e/let [{:keys [favorites java-classes]} (get-in handler [:config-map (symbol #'add-missing-libspec)])
          {:keys [lnum col]} (async/<!! (e.p.host/get-cursor-position! host))
          {:keys [code]} (e.f.sexpr/get-expr elin lnum col)
          [alias-str _] (str/split code #"/" 2)
          candidates (e.f.namespace/missing-candidates elin {:code code
                                                             :requiring-favorites favorites
                                                             :java-classes java-classes})]
    (condp = (count candidates)
      0
      (e.message/warning host "There are no candidates.")

      1
      (add-missing-libspec* elin
                            (str (:name (first candidates)))
                            alias-str)

      ;; else
      (when-let [ns-str (e.f.select/select-from-candidates elin (map :name candidates))]
        (add-missing-libspec* elin
                              ns-str
                              alias-str)))))
