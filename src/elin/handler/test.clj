(ns elin.handler.test
  (:require
   [clojure.string :as str]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.error :as e]
   [elin.function.nrepl.cider :as e.f.n.cider]
   [elin.function.nrepl.vim :as e.f.n.vim]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.util.map :as e.u.map]
   [rewrite-clj.zip :as r.zip]))

(defn- extract-multi-method-name
  [code]
  (let [zloc (-> (r.zip/of-string code)
                 (r.zip/down))]
    (when (contains? #{'defmulti 'defmethod} (r.zip/sexpr zloc))
      (some-> zloc
              (r.zip/next)
              (r.zip/sexpr)
              (str)))))

(defn run-test-under-cursor
  [{:as elin :component/keys [host nrepl interceptor]}]
  (if (e.p.nrepl/supported-op? nrepl e.f.n.cider/test-var-query-op)
    (e/let [{:keys [code response options]} (e.f.n.vim/evaluate-current-top-list!! {:host host :nrepl nrepl})
            {ns-str :ns} options
            var-name (or (some->> (extract-multi-method-name code)
                                  (str ns-str "/"))
                         (str/replace (:value response) #"^#'" ""))
            context (-> (e.u.map/select-keys-by-namespace elin :component)
                        (assoc :ns ns-str
                               :vars [var-name]))]
      (e.p.interceptor/execute
       interceptor e.c.interceptor/test context
       (fn [ctx]
         (let [query {:ns-query {:exactly [(:ns ctx)]}
                      :exactly (:vars ctx)}]
           (assoc ctx :response (e.f.n.cider/test-var-query!! nrepl query))))))

    "TODO plain"))
