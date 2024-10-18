(ns elin.interceptor.evaluate
  (:require
   [clojure.core.async :as async]
   [clojure.string :as str]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.storage :as e.p.storage]
   [elin.util.interceptor :as e.u.interceptor]
   [exoscale.interceptor :as ix]
   [pogonos.core :as pogonos]
   [rewrite-clj.zip :as r.zip]))

(def output-eval-result-to-cmdline
  {:kind e.c.interceptor/evaluate
   :leave (-> (fn [{:component/keys [host] :keys [response]}]
                (when-let [v (:value response)]
                  (e.p.host/echo-text host (str/trim (str v)))))
              (ix/discard))})

(def set-eval-result-to-virtual-text
  "Set evaluated result to virtual text.

  .Configuration
  [%autowidth.stretch]
  |===
  | key | type | description

  | format | string | Format of virtual text. It can contain the following placeholders: `result`.
  | highlight | string | Highlight group for virtual text.
  | align | string | Alignment of virtual text. Possible values are: `after`, `right`.
  | close-after | integer | Close virtual text after the specified number of milliseconds.
  |==="
  {:kind e.c.interceptor/evaluate
   :leave (-> (fn [{:as ctx :component/keys [host] :keys [response options]}]
                (let [config (e.u.interceptor/config ctx #'set-eval-result-to-virtual-text)]
                  (when-let [v (:value response)]
                    (e.p.host/set-virtual-text host
                                               (pogonos/render-string (:format config) {:result v})
                                               (assoc (dissoc config :format)
                                                      :lnum (:cursor-line options))))))
              (ix/discard))})

(def yank-eval-result
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

(def unwrap-comment-form
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

(def wrap-eval-code
  {:kind e.c.interceptor/evaluate
   :enter (fn [{:as ctx :keys [code]}]
            (let [config (e.u.interceptor/config ctx #'wrap-eval-code)]
              (if (seq (:code config))
                (assoc ctx :code (format "(%s %s)" (:code config) code))
                ctx)))})

(def eval-with-context
  {:kind e.c.interceptor/evaluate
   :enter (fn [{:as ctx :interceptor/keys [kind] :component/keys [host session-storage] :keys [code]}]
            (let [last-context (or (e.p.storage/get session-storage kind)
                                   "")
                  context (async/<!! (e.p.host/input! host "Evaluation context (let-style): " last-context))]
              (if (seq context)
                (do (e.p.storage/set session-storage kind context)
                    (assoc ctx :code (format "(clojure.core/let [%s] %s)"
                                             context code)))
                ctx)))})
