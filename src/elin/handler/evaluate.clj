(ns elin.handler.evaluate
  (:require
   [clojure.core.async :as async]
   [elin.constant.kind :as e.c.kind]
   [elin.function.host :as e.f.host]
   [elin.function.nrepl.op :as e.f.n.op]
   [elin.function.sexp :as e.f.sexp]
   [elin.handler :as e.handler]
   [elin.protocol.interceptor :as e.p.interceptor]))

(defn- evaluation*
  [{:as elin :component/keys [nrepl interceptor] :keys [writer]}
   code & [options]]
  (let [options (merge (or options {})
                       {:ns (e.f.sexp/get-namespace writer)
                        :file (e.f.host/get-full-path writer)
                        :nrepl.middleware.print/stream? 1})
        intercept #(apply e.p.interceptor/execute interceptor e.c.kind/evaluate %&)]
    (-> {:elin elin :code code :options options}
        (intercept
         (fn [{:as ctx :keys [code options]}]
           (assoc ctx :response (async/<!! (e.f.n.op/eval nrepl code options)))))
        (get-in [:response :value]))))

(defmethod e.handler/handler* :evaluate
  [{:as elin :keys [message]}]
  (->> message
       (:params)
       (first)
       (evaluation* elin)))

(defmethod e.handler/handler* :evaluate-current-top-list
  [{:as elin :keys [writer]}]
  (let [{:keys [lnum col]} (e.f.host/get-cursor-position writer)
        code (e.f.sexp/get-top-list writer lnum col)]
    (evaluation* elin code {:line lnum :column col})))

(defmethod e.handler/handler* :evaluate-current-list
  [{:as elin :keys [writer]}]
  (let [{:keys [lnum col]} (e.f.host/get-cursor-position writer)
        code (e.f.sexp/get-list writer lnum col)]
    (evaluation* elin code {:line lnum :column col})))

(defmethod e.handler/handler* :evaluate-current-expr
  [{:as elin :keys [writer]}]
  (let [{:keys [lnum col]} (e.f.host/get-cursor-position writer)
        code (e.f.sexp/get-expr writer lnum col)]
    (evaluation* elin code {:line lnum :column col})))
