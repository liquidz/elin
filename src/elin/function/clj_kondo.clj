(ns elin.function.clj-kondo
  (:require
   [clojure.string :as str]
   [elin.protocol.clj-kondo :as e.p.clj-kondo]))

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

#_{:clj-kondo/ignore [:unresolved-namespace]}
(comment
  (e.p.clj-kondo/analyzing? (elin.dev/$ :clj-kondo))

  (async/<!! (e.p.clj-kondo/analyze (elin.dev/$ :clj-kondo)))

  (:summary @(elin.dev/$ :clj-kondo :analyzed-atom))

  (namespace-usages (elin.dev/$ :clj-kondo)))
