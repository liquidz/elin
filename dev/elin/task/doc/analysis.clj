(ns elin.task.doc.analysis
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [elin.config :as e.config]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.task.doc.asciidoc :as asciidoc]
   [elin.task.doc.common :as common]
   [elin.task.help :as help]
   [rewrite-clj.zip :as r.zip]))

(def ^:private analysis
  (some-> "dev/analysis.edn"
          (slurp)
          (edn/read-string)
          (:analysis)))

(def var-usages
  "[{:fixed-arities #{1 3 2}
     :end-row 20
     :name-end-col 5
     :name-end-row 20
     :name-row 20
     :name def
     :filename \"src/elin/component/interceptor.clj\"
     :from elin.component.interceptor
     :macro true
     :col 1
     :name-col 2
     :end-col 40
     :arity 2
     :row 20
     :to clojure.core}]"
  (:var-usages analysis))

(defn- using?
  ([qualified-symbol usage]
   (if (qualified-symbol? qualified-symbol)
     (using? (symbol (namespace qualified-symbol))
             (symbol (name qualified-symbol))
             usage)
     false))
  ([ns-sym name-sym usage]
   (and (= ns-sym (:to usage))
        (= name-sym (:name usage)))))

(defn used?
  ([qualified-symbol usage]
   (if (qualified-symbol? qualified-symbol)
     (used? (symbol (namespace qualified-symbol))
            (symbol (name qualified-symbol))
            usage)
     false))
  ([ns-sym name-sym usage]
   (and (= ns-sym (:from usage))
        (= name-sym (:from-var usage)))))

(defn- recursive-usage?
  [usage]
  (and (= (:from usage) (:to usage))
       (= (:from-var usage) (:name usage))))

(defn- usage->qualified-symbol
  [usage]
  (when-let [from-var (:from-var usage)]
    (symbol (str (:from usage))
            (str from-var))))

(defn- usage-destination?
  [usage]
  (some?
   (when-let [from (:from usage)]
     (re-seq #"^elin\.(handler|interceptor)\."
             (name from)))))

(defn- interceptor-execute-usage->interceptor-kind
  [{:keys [filename row col]}]
  (let [content (slurp filename)
        zloc (-> (r.zip/of-string content {:track-position? true})
                 (r.zip/find-last-by-pos [row col]))]

    (some-> (if (qualified-symbol? (r.zip/sexpr zloc))
              zloc
              (r.zip/down zloc))
            (r.zip/next)
            (r.zip/next)
            (r.zip/sexpr)
            (name))))

(def interceptor-execute-usages
  (filter (partial using? 'elin.protocol.interceptor/execute)
          var-usages))

(defn traverse-usages
  [base-usages]
  (loop [base-usages base-usages
         dest-usages []]
    (let [next-usages (->> base-usages
                           (mapcat (fn [base-usage]
                                     (filter #(using? (:from base-usage) (:from-var base-usage) %)
                                             var-usages)))
                           (distinct)
                           (remove recursive-usage?))
          {next-dest-usages true next-usages false} (group-by usage-destination? next-usages)]
      (if (seq next-usages)
        (recur next-usages (concat dest-usages next-dest-usages))
        (concat dest-usages next-dest-usages)))))


(def handler-using-interceptor-kind-dict
  (->> interceptor-execute-usages
       (mapcat (fn [usage]
                 (if (usage->qualified-symbol usage)
                   (let [kind (interceptor-execute-usage->interceptor-kind usage)]
                     (->> [usage]
                          (traverse-usages)
                          (keep #(some-> %
                                         (usage->qualified-symbol)
                                         (vector kind)))))
                   [])))
       (into {})))

(defn kind-to-interceptor-symbol-dict
  [config]
  (->> (get-in config [:interceptor :includes])
       (group-by #(-> (requiring-resolve %)
                      (deref)
                      (:kind)
                      (name)))))

(defn detect-interceptor-kind [usage]
  (some->> var-usages
           (common/find-first #(and (used? (:from usage) (:from-var usage) %)
                                    (= 'elin.constant.interceptor (:to %))))
           (:name)))
