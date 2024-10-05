(ns elin.handler.evaluate
  (:require
   [clojure.core.async :as async]
   [clojure.pprint :as pp]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.error :as e]
   [elin.function.evaluate :as e.f.evaluate]
   [elin.function.nrepl :as e.f.nrepl]
   [elin.function.nrepl.cider :as e.f.n.cider]
   [elin.function.sexpr :as e.f.sexpr]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.schema.handler :as e.s.handler]
   [elin.util.map :as e.u.map]
   [malli.core :as m]))

(defn- evaluate-interceptor-middleware
  [{:as elin :component/keys [interceptor]}]
  (fn [eval-fn]
    (fn [code options]
      (let [context (-> (e.u.map/select-keys-by-namespace elin :component)
                        (assoc :code code
                               :options options))]
        (:response
         (e.p.interceptor/execute interceptor e.c.interceptor/evaluate context
                                  (fn [{:as ctx :keys [code options]}]
                                    (assoc ctx :response (eval-fn code options)))))))))

(m/=> evaluate [:=> [:cat e.s.handler/?Elin] any?])
(defn evaluate
  [{:as elin :keys [message]}]
  (let [code (->> message
                  (:params)
                  (first))]
    (e/->> {:middleware (evaluate-interceptor-middleware elin)}
           (e.f.evaluate/evaluate-code elin code)
           (:response)
           (:value))))

(m/=> evaluate-current-top-list [:=> [:cat e.s.handler/?Elin] any?])
(defn evaluate-current-top-list
  [elin]
  (e/->> {:middleware (evaluate-interceptor-middleware elin)}
         (e.f.evaluate/evaluate-current-top-list elin)
         (:response)))

(m/=> evaluate-current-list [:=> [:cat e.s.handler/?Elin] any?])
(defn evaluate-current-list
  [elin]
  (e/->> {:middleware (evaluate-interceptor-middleware elin)}
         (e.f.evaluate/evaluate-current-list elin)
         (:response)))

(m/=> evaluate-current-expr [:=> [:cat e.s.handler/?Elin] any?])
(defn evaluate-current-expr
  [elin]
  (e/->> {:middleware (evaluate-interceptor-middleware elin)}
         (e.f.evaluate/evaluate-current-expr elin)
         (:response)))

(m/=> evaluate-namespace-form [:=> [:cat e.s.handler/?Elin] any?])
(defn evaluate-namespace-form
  [elin]
  (e/->> {:middleware (evaluate-interceptor-middleware elin)}
         (e.f.evaluate/evaluate-namespace-form elin)
         (:response)))

(defn evaluate-at-mark
  [{:as elin :keys [message]}]
  (let [[mark-id] (:params message)]
    (e/->> {:middleware (evaluate-interceptor-middleware elin)}
           (e.f.evaluate/evaluate-at-mark elin mark-id)
           (:response))))

(m/=> evaluate-current-buffer [:=> [:cat e.s.handler/?Elin] any?])
(defn evaluate-current-buffer
  [{:component/keys [nrepl host]}]
  (e/let [path (async/<!! (e.p.host/get-current-file-path! host))
          contents (async/<!! (e.p.host/get-lines host))]
    (e.f.nrepl/load-file!! nrepl path contents)
    true))

(defn print-last-result
  [{:component/keys [host nrepl]}]
  (let [resp (e.f.nrepl/eval!! nrepl "(with-out-str (clojure.pprint/pprint *1))")]
    (e.p.host/append-to-info-buffer host (read-string (:value resp)))))

(defn reload
  [{:component/keys [nrepl]}]
  (e.f.n.cider/reload!! nrepl))

(defn reload-all
  [{:component/keys [nrepl]}]
  (e.f.n.cider/reload-all!! nrepl))

(defn interrupt
  [{:component/keys [nrepl]}]
  (e.f.nrepl/interrupt!! nrepl))

(defn undef
  [{:as elin :component/keys [host nrepl]}]
  (e/let [{:keys [lnum col]} (async/<!! (e.p.host/get-cursor-position! host))
          ns-str (e.f.sexpr/get-namespace elin)
          {:keys [code]} (e.f.sexpr/get-expr elin lnum col)]
    (e.f.n.cider/undef!! nrepl ns-str code)))

(defn undef-all
  [{:as elin :component/keys [nrepl]}]
  (e/let [ns-str (e.f.sexpr/get-namespace elin)]
    (e.f.n.cider/undef-all!! nrepl ns-str)))

(defn expand-1-current-list
  [{:as elin :component/keys [host]}]
  (e/let [{cur-lnum :lnum cur-col :col} (async/<!! (e.p.host/get-cursor-position! host))
          {:keys [code]} (e.f.sexpr/get-list elin cur-lnum cur-col)
          ns-str (e/error-or (e.f.sexpr/get-namespace elin)
                             "")
          resp (e.f.evaluate/expand-1 elin ns-str code)]
    (with-out-str
      (-> (:value resp)
          (read-string)
          (pp/pprint)))))
