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

(defn- find-first
  [pred coll]
  (some #(when (pred %) %) coll))

(declare namespace-by-alias)

(defn namespace-usages
  [clj-kondo]
  (when-let [ana (e.p.clj-kondo/analysis clj-kondo)]
    (or (:namespace-usages ana)
        [])))

(defn var-usages
  "e.g.
   [{:fixed-arities #{0}
     :end-row 23
     :name-end-col 67
     :name-end-row 23
     :name-row 23
     :name next-id
     :filename \"src/elin/component/server/impl/function.clj\"
     :alias e.u.id
     :from elin.component.server.impl.function
     :col 52
     :name-col 53
     :end-col 68
     :arity 0
     :row 23
     :to elin.util.id}
    ...]"
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

(defn protocol-definitions
  [clj-kondo]
  (->> (var-definitions clj-kondo)
       (filter :protocol-name)))

(defn- protocol-implementations*
  [clj-kondo]
  (when-let [ana (e.p.clj-kondo/analysis clj-kondo)]
    (or (:protocol-impls ana)
        [])))

(defn protocol-implementations
  [clj-kondo protocol-ns protocol-name method-name]
  (let [protocol-ns-sym (symbol protocol-ns)
        protocol-name-sym (symbol protocol-name)
        method-name-sym (symbol method-name)]
    (->> (protocol-implementations* clj-kondo)
         (filter #(and (= protocol-ns-sym (:protocol-ns %))
                       (= protocol-name-sym (:protocol-name %))
                       (= method-name-sym (:method-name %)))))))

(defn protocol-definition
  [clj-kondo ns-str sym-str]
  (let [ns-sym (symbol ns-str)
        sym-sym (symbol sym-str)]
    (->> (protocol-definitions clj-kondo)
         (filter #(and (= ns-sym (:protocol-ns %))
                       (= sym-sym (:name %))))
         (first))))

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

(def ^:private ?Usage
  [:map
   [:filename string?]
   [:ns symbol?]
   [:col int?]
   [:lnum int?]])

(m/=> references [:=> [:cat e.s.component/?CljKondo string? string?] [:sequential ?Usage]])
(defn references
  [clj-kondo ns-str var-name]
  (let [var-name (str/replace-first var-name #"^'+" "")
        [alias-name var-name'] (str/split var-name #"/" 2)
        ns-sym (symbol (if (seq alias-name)
                         (or (namespace-by-alias clj-kondo (symbol alias-name))
                             ns-str)
                         ns-str))
        var-sym (symbol (if (and alias-name var-name')
                          var-name'
                          var-name))]
    (some->> (var-usages clj-kondo)
             (filter #(and (= ns-sym (:to %))
                           (= var-sym (:name %))))
             (map #(-> %
                       (select-keys [:filename :from :row :col])
                       (set/rename-keys {:from :ns :row :lnum})))
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

(m/=> namespaces-by-alias [:=> [:cat e.s.component/?CljKondo symbol?] [:sequential symbol?]])
(defn namespaces-by-alias
  [clj-kondo alias-sym]
  (->> (namespace-usages clj-kondo)
       (filter #(= alias-sym (:alias %)))
       (map :to)
       (distinct)))

(m/=> namespace-by-alias [:=> [:cat e.s.component/?CljKondo symbol?] [:maybe symbol?]])
(defn namespace-by-alias
  [clj-kondo alias-sym]
  (let [grouped (->> (namespace-usages clj-kondo)
                     (filter #(= alias-sym (:alias %)))
                     (map :to)
                     (group-by identity))]
    (when (seq grouped)
      (->> (update-vals grouped count)
           (sort-by val)
           (last)
           (key)))))

(defn- var-lookup
  [clj-kondo ns-sym var-sym]
  (->> (var-definitions clj-kondo)
       (find-first #(and (= ns-sym (:ns %))
                         (= var-sym (:name %))))))

(defn- namespace-lookup
  [clj-kondo ns-sym]
  (->> (namespace-definitions clj-kondo)
       (find-first #(= ns-sym (:name %)))))

(m/=> lookup [:=> [:cat e.s.component/?CljKondo string? string?] (e.schema/error-or e.s.nrepl/?Lookup)])
(defn lookup
  [clj-kondo ns-str sym-str]
  (e/let [from-ns-sym (symbol ns-str)
          [sym-ns sym-name] (str/split sym-str #"/" 2)
          [alias-sym var-sym] (if sym-name
                                [(symbol sym-ns) (symbol sym-name)]
                                [nil (symbol sym-ns)])
          to-ns-sym (if alias-sym
                      (or (some->> (namespace-usages clj-kondo)
                                   (filter #(and (= from-ns-sym (:from %))
                                                 (= alias-sym (:alias %))))
                                   (first)
                                   (:to))
                          alias-sym)
                      from-ns-sym)
          var-def (or (var-lookup clj-kondo to-ns-sym var-sym)
                      (namespace-lookup clj-kondo var-sym)
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

(defn local-lookup
  [clj-kondo top-code sym-str]
  (e/let [resp (e.p.clj-kondo/analyze-code!! clj-kondo top-code)
          local-vars (get-in resp [:analysis :locals])
          local-def (or (find-first #(= sym-str (:str %)) local-vars)
                        (e/not-found {:message (format "Var '%s' is not found" sym-str)}))]
    (-> local-def
        (select-keys [:name :row :col])
        (set/rename-keys {:row :line
                          :col :column})
        (update :name str))))

(defn requiring-namespaces
  [clj-kondo ns-str]
  (let [ns-sym (symbol ns-str)]
    (some->> (namespace-usages clj-kondo)
             (filter #(= ns-sym (:from %)))
             (map :to))))

(defn- using?
  ([qualified-symbol usage]
   (if (qualified-symbol? qualified-symbol)
     (using? (symbol (namespace qualified-symbol))
             (symbol (name qualified-symbol))
             usage)
     false))
  ([ns-sym name-sym usage]
   (and (= ns-sym (:to usage))
        (= name-sym (:name usage))
        ;; Exclude those that are not used from anywhere
        (:from-var usage))))

(defn- recursive-usage?
  [usage]
  (and (= (:from usage) (:to usage))
       (= (:from-var usage) (:name usage))))

(defn- traverse-usages*
  [all-usages base-usages]
  (loop [usage-deps (->> base-usages
                         (mapv #(vector % [])))
         traversed-usages (set base-usages)]
    (let [next-usage-deps (->> usage-deps
                               (mapcat (fn [usage-dep]
                                         (if-let [base-usage (first usage-dep)]
                                           (let [child-usages (->> all-usages
                                                                   (filter #(using? (:from base-usage) (:from-var base-usage) %))
                                                                   (distinct)
                                                                   (remove #(or (recursive-usage? %)
                                                                                (contains? traversed-usages %))))]

                                             (if (seq child-usages)
                                               (mapv #(vector % usage-dep) child-usages)
                                               [[nil usage-dep]]))
                                           [usage-dep])))
                               (vec))]
      (if (every? (comp nil? first) next-usage-deps)
        (map second next-usage-deps)
        (recur next-usage-deps
               (->> next-usage-deps
                    (map first)
                    (concat traversed-usages)
                    (set)))))))

(defn- extract-qualified-from-var
  [[first-elm :as coll]]
  (cond
    (empty? coll)
    []

    (sequential? first-elm)
    (map extract-qualified-from-var coll)

    (map? first-elm)
    [(symbol (name (:from first-elm))
             (name (:from-var first-elm)))
     (extract-qualified-from-var (second coll))]))

(defn traverse-usages
  [clj-kondo ns-str var-str]
  (let [all-usages (var-usages clj-kondo)
        ns-sym (symbol ns-str)
        var-sym (symbol var-str)]
    (->> all-usages
         (filter #(and (= ns-sym (:to %))
                       (= var-sym (:name %))
                       ;; Exclude those that are not used from anywhere
                       (:from-var %)))
         (traverse-usages* all-usages)
         (extract-qualified-from-var)
         (flatten)
         (distinct))))

(comment
  (def clj-kondo (elin.dev/$ :clj-kondo))
  (e.p.clj-kondo/analyzing? (elin.dev/$ :clj-kondo))

  (async/<!! (e.p.clj-kondo/analyze (elin.dev/$ :clj-kondo)))

  (keys (e.p.clj-kondo/analysis (elin.dev/$ :clj-kondo)))

  (:summary @(elin.dev/$ :clj-kondo :analyzed-atom))

  (clojure.pprint/pprint
    (filter #(= 'references (:name %)) (var-definitions (elin.dev/$ :clj-kondo))))
    ; (first (var-definitions (elin.dev/$ :clj-kondo))))
  (clojure.pprint/pprint
    (first (namespace-usages (elin.dev/$ :clj-kondo))))

  (let [ns-str "elin.handler.lookup"
        sym-str "e.p.host/get-cursor-position!!"
        sym-str "generate-cljdocc"]
    (clojure.pprint/pprint
      (lookup clj-kondo ns-str sym-str)))

  (def code-analysis
    (e.p.clj-kondo/analyze-code!! (elin.dev/$ :clj-kondo) (str '(defn foo [x]
                                                                  (let [y (+ x 1)]
                                                                    y)))))
  (clojure.pprint/pprint
    (:var-definitions (:analysis code-analysis)))
  (clojure.pprint/pprint
    (:locals (:analysis code-analysis))))
