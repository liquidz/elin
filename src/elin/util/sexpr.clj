(ns elin.util.sexpr
  (:require
   [clojure.string :as str]
   [elin.error :as e]
   [elin.schema :as e.schema]
   [malli.core :as m]
   [rewrite-clj.node :as r.node]
   [rewrite-clj.parser :as r.parser]
   [rewrite-clj.zip :as r.zip]))

(m/=> extract-ns-form [:=> [:cat string?] (e.schema/error-or string?)])
(defn extract-ns-form
  [code]
  (or (some-> (r.zip/of-string code)
              (r.zip/find-value r.zip/next 'ns)
              (r.zip/up)
              (r.zip/sexpr)
              (str))
      (e/not-found)))

(m/=> extract-namespace [:=> [:cat string?] (e.schema/error-or string?)])
(defn extract-namespace
  [form-code]
  (try
    (e/let [_ (when (empty? form-code)
                (e/not-found {:message "No namespace form found"}))
            target-sym (if (str/includes? form-code "in-ns") 'in-ns 'ns)
            ns-str (-> form-code
                       (r.zip/of-string)
                       (r.zip/find-value r.zip/next target-sym)
                       (r.zip/right)
                       (as-> zloc
                         (if (= :quote (r.zip/tag zloc))
                           (r.zip/down zloc)
                           zloc))
                       (r.zip/sexpr)
                       (str))]
      (if (empty? ns-str)
        (e/not-found {:message "No namespace form found"})
        ns-str))

    (catch Exception ex
      (e/not-found {:message (ex-message ex)}))))

(m/=> add-require [:=> [:cat string? symbol? [:maybe symbol?]] (e.schema/error-or string?)])
(defn add-require
  [form-code ns-sym alias-sym]
  (e/let [require-node (if alias-sym
                         (r.parser/parse-string (format "[%s :as %s]" ns-sym alias-sym))
                         (r.parser/parse-string (str ns-sym)))
          zloc (r.zip/of-string form-code)
          zloc (if-let [zloc' (r.zip/find-value zloc r.zip/next :require)]
                 zloc'
                 (or (some-> zloc
                             (r.zip/down)
                             (r.zip/rightmost)
                             (r.zip/insert-right (r.parser/parse-string "(:require)"))
                             (r.zip/insert-space-right)
                             (r.zip/insert-newline-right)
                             (r.zip/find-value r.zip/next :require))
                     (e/not-found)))
          right-zloc (some-> zloc r.zip/right*)
          linebreaked? (some-> right-zloc r.zip/node r.node/linebreak?)
          zloc (r.zip/insert-right zloc require-node)]
    (r.zip/root-string
     (cond
       linebreaked?
       (-> zloc
           (r.zip/insert-space-right 2)
           (r.zip/insert-newline-right))

       (some? right-zloc)
       (-> zloc
           (r.zip/right)
           (r.zip/insert-space-right 11)
           (r.zip/insert-newline-right))

       :else
       zloc))))

(m/=> extract-form-by-position [:=> [:cat string? int? int?] (e.schema/error-or string?)])
(defn extract-form-by-position
  [code line col]
  (try
    (-> (r.zip/of-string code {:track-position? true})
        (r.zip/find-last-by-pos [line col])
        (r.zip/string))
    (catch Exception ex
      (e/not-found {:message (ex-message ex)}))))
