(ns elin.interceptor.evaluate
  (:require
   [clojure.string :as str]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.protocol.host :as e.p.host]
   [exoscale.interceptor :as ix]
   [rewrite-clj.zip :as r.zip]))

(def output-eval-result-to-cmdline-interceptor
  {:kind e.c.interceptor/evaluate
   :leave (-> (fn [{:component/keys [host] :keys [response]}]
                (when-let [v (:value response)]
                  (e.p.host/echo-text host (str/trim (str v)))))
              (ix/discard))})

(def set-eval-result-to-virtual-text-interceptor
  {:kind e.c.interceptor/evaluate
   :leave (-> (fn [{:component/keys [host] :keys [response options]}]
                (when-let [v (:value response)]
                  (e.p.host/set-virtual-text host
                                             (str "=> " v)
                                             {:lnum (:cursor-line options)
                                              :highlight "DiffText"
                                              :close-after 3000})))
              (ix/discard))})

(def store-eval-result-to-clipboard-interceptor
  {:kind e.c.interceptor/evaluate
   :leave (-> (fn [{:component/keys [host] :keys [response]}]
                (some->> (:value response)
                         (e.p.host/yank host)))
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
  {:kind e.c.interceptor/evaluate
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
