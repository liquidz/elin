(ns elin.handler.evaluate
  (:require
   [clojure.core.async :as async]
   [elin.function.host :as e.f.host]
   [elin.function.nrepl.op :as e.f.n.op]
   [elin.function.sexp :as e.f.sexp]
   [elin.handler :as e.handler]))

(defn- evaluation*
  [{:component/keys [nrepl writer]}
   code & [options]]
  (let [options (merge (or options {})
                       {:ns (e.f.sexp/get-namespace writer)
                        :file (e.f.host/get-full-path writer)
                        :nrepl.middleware.print/stream? 1})]
    (-> (e.f.n.op/eval nrepl code options)
        (async/<!!)
        (:value))))

;; TODO status: ["namespace-not-found" "done" "error"]

(defmethod e.handler/handler* :evaluate
  [{:as elin :keys [message]}]
  (->> message
       (:params)
       (first)
       (evaluation* elin)))

(defmethod e.handler/handler* :evaluate-current-top-list
  [{:as elin :component/keys [writer]}]
  (let [{:keys [lnum col]} (e.f.host/get-cursor-position writer)
        code (e.f.sexp/get-top-list writer lnum col)]
    (evaluation* elin code {:line lnum :column col})))

(defmethod e.handler/handler* :evaluate-current-list
  [{:as elin :component/keys [writer]}]
  (let [{:keys [lnum col]} (e.f.host/get-cursor-position writer)
        code (e.f.sexp/get-list writer lnum col)]
    (evaluation* elin code {:line lnum :column col})))

(defmethod e.handler/handler* :evaluate-current-expr
  [{:as elin :component/keys [writer]}]
  (let [{:keys [lnum col]} (e.f.host/get-cursor-position writer)
        code (e.f.sexp/get-expr writer lnum col)]
    (evaluation* elin code {:line lnum :column col})))
