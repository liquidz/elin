(ns elin.function.clj-kondo
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [elin.error :as e]
   [elin.protocol.clj-kondo :as e.p.clj-kondo]
   [elin.schema :as e.schema]
   [elin.schema.component :as e.s.component]
   [elin.schema.nrepl :as e.s.nrepl]
   [malli.core :as m]))

(defn namespace-usages
  [clj-kondo]
  (when-let [ana (e.p.clj-kondo/analysis clj-kondo)]
    (or (:namespace-usages ana)
        [])))

(defn var-usages
  [clj-kondo]
  (when-let [ana (e.p.clj-kondo/analysis clj-kondo)]
    (or (:var-usages ana)
        [])))

(defn namespace-definitions
  [clj-kondo]
  (when-let [ana (e.p.clj-kondo/analysis clj-kondo)]
    (or (:namespace-definitions ana)
        [])))

(defn var-definitions
  [clj-kondo]
  (when-let [ana (e.p.clj-kondo/analysis clj-kondo)]
    (or (:var-definitions ana)
        [])))

(defn local-usages
  [clj-kondo]
  (when-let [ana (e.p.clj-kondo/analysis clj-kondo)]
    (or (:local-usages ana)
        [])))

(defn local-definitions
  [clj-kondo]
  (when-let [ana (e.p.clj-kondo/analysis clj-kondo)]
    (or (:locals ana)
        [])))

(defn keywords [clj-kondo]
  (when-let [ana (e.p.clj-kondo/analysis clj-kondo)]
    (or (:keywords ana)
        [])))

(defn keyword-usages
  [clj-kondo keyword']
  (when-let [keywords' (keywords clj-kondo)]
    (let [[kw-ns kw-name] ((juxt namespace name) keyword')
          pred (if kw-ns
                 #(and (= kw-ns (:ns %))
                       (= kw-name (:name %)))
                 #(= kw-name (:name %)))]
      (filter pred keywords'))))

(defn keyword-definition
  [clj-kondo filename keyword']
  (when-let [keywords' (keywords clj-kondo)]
    (let [[kw-ns kw-name] ((juxt namespace name) keyword')]
      (if kw-ns
        (when-let [targets (->> keywords'
                                (filter #(and (= filename (:filename %))
                                              (= kw-ns (:alias %))
                                              (= kw-name (:name %))))
                                (seq))]
          (let [target-ns (-> targets (first) (:ns) (or ""))
                target-name (-> targets (first) (:name) (or ""))]
            (->> keywords'
                 (filter #(and (= target-ns (:ns %))
                               (= target-name (:name %))
                               (not= "" (:reg %))))
                 (first))))
        (->> keywords'
             (filter #(and (= filename (:filename %))
                           (= "" (:alias %))
                           (= kw-name (:name %))
                           (not= "" (:reg %))))
             (first))))))

(defn references
  [clj-kondo ns-str var-name]
  (let [var-name (str/replace-first var-name #"^'+" "")]
    (some->> (var-usages clj-kondo)
             (filter #(and (= ns-str (:to %))
                           (= var-name (:name %))))
             (sort-by :filename))))

(m/=> namespace-symbols [:=> [:cat e.s.component/?CljKondo] [:sequential symbol?]])
(defn namespace-symbols
  [clj-kondo]
  (->> (namespace-definitions clj-kondo)
       (map :name)
       (sort)))

(m/=> most-used-namespace-alias [:=> [:cat e.s.component/?CljKondo symbol?] [:maybe symbol?]])
(defn most-used-namespace-alias
  [clj-kondo ns-sym]
  (let [grouped (->> (namespace-usages clj-kondo)
                     (filter #(= ns-sym (:to %)))
                     (map :alias)
                     (group-by identity))]
    (when (seq grouped)
      (->> (update-vals grouped count)
           (sort-by val)
           (last)
           (key)))))

(m/=> lookup [:=> [:cat e.s.component/?CljKondo string? string?] (e.schema/error-or e.s.nrepl/?Lookup)])
(defn lookup
  [clj-kondo ns-str sym-str]
  (e/let [from-ns-sym (symbol ns-str)
          [sym-ns sym-name] (str/split sym-str #"/" 2)
          [alias-sym var-sym] (if sym-name
                                [(symbol sym-ns) (symbol sym-name)]
                                [nil (symbol sym-ns)])
          to-ns-sym (if alias-sym
                      (some->> (namespace-usages clj-kondo)
                               (filter #(and (= from-ns-sym (:from %))
                                             (= alias-sym (:alias %))))
                               (first)
                               (:to))
                      from-ns-sym)
          var-def (or (->> (var-definitions clj-kondo)
                           (filter #(and (= to-ns-sym (:ns %))
                                         (= var-sym (:name %))))
                           (first))
                      (e/not-found {:message (format "Var '%s' is not found in %s" sym-str ns-str)}))]
    (-> var-def
        (select-keys [:ns :name :filename :row :col :doc :arglist-strs])
        (set/rename-keys {:filename :file
                          :row :line
                          :col :column
                          :arglist-strs :arglists-str})
        (update :ns str)
        (update :name str)
        (update :arglists-str #(if (sequential? %)
                                 (str/join " " %)
                                 (str %))))))

(comment
  (def clj-kondo (elin.dev/$ :clj-kondo))
  (e.p.clj-kondo/analyzing? (elin.dev/$ :clj-kondo))

  (async/<!! (e.p.clj-kondo/analyze (elin.dev/$ :clj-kondo)))

  (keys (e.p.clj-kondo/analysis (elin.dev/$ :clj-kondo)))

  (:summary @(elin.dev/$ :clj-kondo :analyzed-atom))

  (clojure.pprint/pprint
    (filter #(= 'references (:name %)) (var-definitions (elin.dev/$ :clj-kondo))))
    ;(first (var-definitions (elin.dev/$ :clj-kondo))))
  (clojure.pprint/pprint
    (first (namespace-usages (elin.dev/$ :clj-kondo))))

  (let [ns-str "elin.handler.lookup"
        sym-str "e.f.vim/get-cursor-position!!"
        sym-str "generate-cljdocc"]
      (clojure.pprint/pprint
        (lookup clj-kondo ns-str sym-str))))
