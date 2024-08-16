(ns elin.function.namespace
  (:require
   [clojure.edn :as edn]
   [clojure.string :as str]
   [elin.constant.nrepl :as e.c.nrepl]
   [elin.error :as e]
   [elin.function.clj-kondo :as e.f.clj-kondo]
   [elin.function.nrepl :as e.f.nrepl]
   [elin.function.nrepl.refactor :as e.f.n.refactor]
   [elin.function.sexpr :as e.f.sexpr]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema.handler :as e.s.handler]
   [elin.util.string :as e.u.string]
   [malli.core :as m]))

(defn get-namespaces
  [{:component/keys [clj-kondo nrepl]}]
  (let [ns-list (e/-> (e.f.nrepl/eval!! nrepl (str '(map (comp str ns-name) (all-ns))))
                      (:value)
                      (edn/read-string))
        ns-list (if (e/error? ns-list)
                  []
                  ns-list)]
    (->> (e.f.clj-kondo/namespace-symbols clj-kondo)
         (map str)
         (concat ns-list)
         (distinct)
         (sort))))

(defn most-used-namespace-alias
  [{:component/keys [clj-kondo]} ns-sym]
  (e.f.clj-kondo/most-used-namespace-alias clj-kondo ns-sym))

(defn namespace-by-alias
  [{:component/keys [clj-kondo]} alias-sym]
  (e.f.clj-kondo/namespace-by-alias clj-kondo alias-sym))

(m/=> add-missing-libspec [:=> [:cat e.s.handler/?Elin string? map?] [:sequential [:map [:name symbol?] [:type keyword?]]]])
(defn add-missing-libspec
  [{:as elin :component/keys [clj-kondo nrepl]} sym-str favorites]
  (if (and (not (e.p.nrepl/disconnected? nrepl))
           (e.p.nrepl/supported-op? nrepl e.c.nrepl/resolve-missing-op))
    ;; refactor-nrepl
    (e.f.n.refactor/resolve-missing!! nrepl sym-str)
    ;; clj-kondo
    (let [[alias-str _] (str/split sym-str #"/" 2)
          alias-sym (symbol alias-str)
          ns-str (e.f.sexpr/get-namespace elin)
          requires (set (e.f.clj-kondo/requiring-namespaces clj-kondo ns-str))
          favorites' (some->> favorites
                              (filter #(= alias-sym (val %)))
                              (map key))]
      (some->> (concat (e.f.clj-kondo/namespaces-by-alias clj-kondo alias-sym)
                       favorites')
               (distinct)
               (remove requires)
               (map #(hash-map :name % :type :ns))))))

(defn- missing-require-candidates
  [{:as elin :component/keys [clj-kondo nrepl]} sym-str favorites]
  (if (and (not (e.p.nrepl/disconnected? nrepl))
           (e.p.nrepl/supported-op? nrepl e.c.nrepl/resolve-missing-op))
    ;; refactor-nrepl
    (e.f.n.refactor/resolve-missing!! nrepl sym-str)
    ;; clj-kondo
    (let [[alias-str _] (str/split sym-str #"/" 2)
          alias-sym (symbol alias-str)
          ns-str (e.f.sexpr/get-namespace elin)
          requires (set (e.f.clj-kondo/requiring-namespaces clj-kondo ns-str))
          favorites' (some->> favorites
                              (filter #(= alias-sym (val %)))
                              (map key))]
      (some->> (concat (e.f.clj-kondo/namespaces-by-alias clj-kondo alias-sym)
                       favorites')
               (distinct)
               (remove requires)
               (map #(hash-map :name % :type :ns))))))

(defn- missing-import-candidates
  [sym-str java-classes]
  (let [class-name-sym (symbol sym-str)]
    (->> java-classes
         (reduce-kv
          (fn [res pkg class-set]
            (if (contains? class-set class-name-sym)
              (conj res (symbol (str (name pkg) "." class-name-sym)))
              res))
          [])
         (distinct)
         (map #(hash-map :name % :type :class)))))

(def ^:private ?MissingCandidatesInput
  [:map
   [:code string?]
   [:requiring-favorites map?]
   [:java-classes map?]])

(def ^:private ?MissingCandidatesOutput
  [:sequential
   [:map
    [:name symbol?]
    [:type [:enum :ns :class]]]])

(m/=> missing-candidates [:=> [:cat e.s.handler/?Elin ?MissingCandidatesInput]
                          ?MissingCandidatesOutput])
(defn missing-candidates
  [{:as elin}
   {:keys [code requiring-favorites java-classes]}]
  (if (e.u.string/java-class-name? code)
    (missing-import-candidates code java-classes)
    (missing-require-candidates elin code requiring-favorites)))
