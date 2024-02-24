(ns elin.interceptor.evaluate
  (:require
   [clojure.string :as str]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.function.vim :as e.f.vim]
   [elin.function.vim.virtual-text :as e.f.v.virtual-text]
   [elin.protocol.rpc :as e.p.rpc]
   [exoscale.interceptor :as ix]
   [rewrite-clj.zip :as r.zip]))

(def output-eval-result-to-cmdline-interceptor
  {:name ::output-eval-result-to-cmdline-interceptor
   :kind e.c.interceptor/evaluate
   :leave (-> (fn [{:component/keys [host] :keys [response]}]
                (when-let [v (:value response)]
                  (e.f.vim/notify host "elin#internal#shortening_echo" [(str/trim (str v))]))

                (when-let [v (:err response)]
                  (e.p.rpc/echo-message host (str/trim (str v)) "ErrorMsg")))
              (ix/discard))})

(def set-eval-result-to-virtual-text-interceptor
  {:name ::set-eval-result-to-virtual-text-interceptor
   :kind e.c.interceptor/evaluate
   :leave (-> (fn [{:component/keys [host] :keys [response]}]
                (when-let [v (:value response)]
                  (e.f.v.virtual-text/set host
                                          (str v)
                                          {:highlight "DiffText"})))
              (ix/discard))})

(defn- up-until-top [zloc]
  (loop [zloc zloc]
    (let [up-zloc (r.zip/up zloc)]
      (if (-> up-zloc
              (r.zip/down)
              (r.zip/sexpr)
              (= 'comment))
        zloc
        (recur up-zloc)))))

(def eval-in-comment-interceptor
  {:name ::eval-in-comment-interceptor
   :kind e.c.interceptor/evaluate
   :enter (-> (fn [{:as ctx :keys [code options]}]
                (let [{:keys [line column cursor-line cursor-column]} options
                      one-based-rel-line (inc (- cursor-line line))
                      one-based-rel-column (inc (- cursor-column column))
                      zloc (-> (r.zip/of-string code {:track-position? true})
                               (r.zip/find-last-by-pos [one-based-rel-line
                                                        one-based-rel-column])
                               (up-until-top))
                      code' (if (r.zip/seq? zloc)
                              (str (r.zip/sexpr zloc))
                              (str/replace-first code #"^\(comment" "(do"))]
                  (assoc ctx :code code')))
              (ix/when #(str/starts-with? (:code %) "(comment")))})
