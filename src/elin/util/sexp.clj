(ns elin.util.sexp
  (:require
   [clojure.string :as str]
   [elin.error :as e]
   [rewrite-clj.zip :as r.zip]))

(defn extract-ns-form
  [code]
  (some-> (r.zip/of-string code)
          (r.zip/find-value r.zip/next 'ns)
          (r.zip/up)
          (r.zip/sexpr)
          (str)))

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
