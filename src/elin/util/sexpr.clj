(ns elin.util.sexpr
  (:require
   [clj-commons.digest :as digest]
   [clojure.string :as str]
   [elin.error :as e]
   [elin.schema :as e.schema]
   [elin.util.string :as e.u.string]
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

(m/=> add-import [:=> [:cat string? symbol?] (e.schema/error-or string?)])
(defn add-import
  [form-code class-name-sym]
  (e/let [import-node (r.parser/parse-string (str class-name-sym))
          zloc (r.zip/of-string form-code)
          zloc (if-let [zloc' (r.zip/find-value zloc r.zip/next :import)]
                 zloc'
                 (or (some-> zloc
                             (r.zip/down)
                             (r.zip/rightmost)
                             (r.zip/insert-right (r.parser/parse-string "(:import)"))
                             (r.zip/insert-space-right)
                             (r.zip/insert-newline-right)
                             (r.zip/find-value r.zip/next :import))
                     (e/not-found)))
          right-zloc (some-> zloc r.zip/right*)
          linebreaked? (some-> right-zloc r.zip/node r.node/linebreak?)
          zloc (r.zip/insert-right zloc import-node)]
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
  "Extract a form at the given position.
  line and column are 1-based."
  [code line col]
  (try
    (-> (r.zip/of-string code {:track-position? true})
        (r.zip/find-last-by-pos [line col])
        (r.zip/string))
    (catch Exception ex
      (e/not-found {:message (ex-message ex)}))))

(m/=> extract-local-binding-by-position [:=> [:cat string? int? int?] (e.schema/error-or string?)])
(defn extract-local-binding-by-position
  "Extract a local binding at the given position.
  line and column are 1-based."
  [code line col]
  (try
    (let [zloc (-> (r.zip/of-string code {:track-position? true})
                   (r.zip/find-last-by-pos [line col]))
          s (str (r.zip/string zloc)
                 " "
                 (r.zip/string (r.zip/right zloc)))]
      (e.u.string/trim-indent (dec col) s 1))
    (catch Exception ex
      (e/not-found {:message (ex-message ex)}))))

(defn skip-reader-macro
  [zloc]
  (if (= :reader-macro (r.zip/tag zloc))
    (-> zloc
        (r.zip/down)
        (r.zip/right)
        (skip-reader-macro))
    zloc))

(defn skip-meta
  [zloc]
  (if (= :meta (r.zip/tag zloc))
    (-> zloc
        (r.zip/down)
        (r.zip/right))
    zloc))

(defn- apply-cider-coordination*
  [code coordination]
  (loop [zloc (r.zip/of-string code {:track-position? true})
         [n & rest-coor] coordination]
    (if-not n
      zloc
      (recur (nth (->> zloc
                       (skip-reader-macro)
                       (skip-meta)
                       (r.zip/down)
                       (iterate r.zip/right))
                  n)
             rest-coor))))

(m/=> apply-cider-coordination [:=> [:cat string? [:sequential int?]]
                                [:map
                                 [:code string?]
                                 [:position [:cat int? int?]]]])
(defn apply-cider-coordination
  "a COORDINATES list of '(1 0 2) means:
   - enter next sexp then `forward-sexp' once,
   - enter next sexp,
   - enter next sexp then `forward-sexp' twice."
  [code coordination]
  (let [zloc (apply-cider-coordination* code coordination)
        [lnum col] (r.zip/position zloc)]
    {:code (r.zip/string zloc)
     :position [(dec lnum) (dec col)]}))

(defn- testing-form?
  [zloc]
  (let [sym (-> zloc
                (r.zip/down)
                (r.zip/sexpr))]
    (boolean
      (when (symbol? sym)
        (= "testing" (name sym))))))

(defn- sexpr-digest
  [zloc]
  (digest/md5 (r.zip/string zloc)))

(defn- parent-testing-form-digests
  [zloc]
  (loop [zloc zloc
         result []]
    (if-let [up-zloc (r.zip/up zloc)]
      (if (testing-form? up-zloc)
        (recur up-zloc (conj result (sexpr-digest up-zloc)))
        (recur up-zloc result))
      result)))

(defn- wrap-by-comment-form
  [node]
  (r.node/list-node [(r.node/token-node 'comment)
                     (r.node/whitespace-node " ")
                     node]))

(defn convert-code-to-testing-focused-code
  [test-code current-lnum current-column]
  (let [zloc (r.zip/of-string test-code {:track-position? true})
        current-zloc (r.zip/find-last-by-pos zloc [current-lnum current-column])
        parent-testing-form-digest-set (-> current-zloc
                                           (parent-testing-form-digests)
                                           (set))]
    (r.zip/root-string
      (loop [zloc zloc]
        (if-let [zloc (r.zip/find-next zloc r.zip/next testing-form?)]
          (if (contains? parent-testing-form-digest-set
                         (sexpr-digest zloc))
            (recur zloc)
            (recur (-> zloc
                       (r.zip/edit* wrap-by-comment-form)
                       (r.zip/next) ; comment symbol
                       (r.zip/next) ; testing form
                       (r.zip/next))))
          zloc)))))
