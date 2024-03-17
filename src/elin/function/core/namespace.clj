(ns elin.function.core.namespace
  (:require
   [clojure.edn :as edn]
   [clojure.string :as str]
   [elin.constant.nrepl :as e.c.nrepl]
   [elin.error :as e]
   [elin.function.clj-kondo :as e.f.clj-kondo]
   [elin.function.nrepl :as e.f.nrepl]
   [elin.function.nrepl.refactor :as e.f.n.refactor]
   [elin.function.vim.sexp :as e.f.v.sexp]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema.handler :as e.s.handler]
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

(m/=> resolve-missing-namespace [:=> [:cat e.s.handler/?Elin string? map?] [:sequential [:map [:name symbol?] [:type keyword?]]]])
(defn resolve-missing-namespace
  [{:component/keys [clj-kondo host nrepl]} sym-str favorites]
  (if (and (not (e.p.nrepl/disconnected? nrepl))
           (e.p.nrepl/supported-op? nrepl e.c.nrepl/resolve-missing-op))
    ;; refactor-nrepl
    (e.f.n.refactor/resolve-missing!! nrepl sym-str)
    ;; clj-kondo
    (let [[alias-str _] (str/split sym-str #"/" 2)
          alias-sym (symbol alias-str)
          ns-str (e.f.v.sexp/get-namespace!! host)
          requires (set (e.f.clj-kondo/requiring-namespaces clj-kondo ns-str))
          favorites' (some->> favorites
                              (filter #(= alias-sym (val %)))
                              (map key))]
      (some->> (concat (e.f.clj-kondo/namespaces-by-alias clj-kondo alias-sym)
                       favorites')
               (distinct)
               (remove requires)
               (map #(hash-map :name % :type :ns))))))
