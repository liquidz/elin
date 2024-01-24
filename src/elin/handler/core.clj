(ns elin.handler.core
  (:require
   [clojure.core.async :as async]
   [elin.constant.kind :as e.c.kind]
   [elin.function.host :as e.f.host]
   [elin.function.nrepl.op :as e.f.n.op]
   [elin.function.sexp :as e.f.sexp]
   [elin.log :as e.log]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.protocol.nrepl :as e.p.nrepl]))

(defmulti handler* (comp :method :message))
(defmethod handler* :default [_] nil)

(defmethod handler* :initialize
  [_]
  ;; TODO Load plugins
  "FIXME")

(defmethod handler* :connect
  [{:as elin :component/keys [nrepl interceptor] :keys [message writer]}]
  (let [{:keys [params]} message
        [host port] (condp = (count params)
                      0 [nil nil]
                      1 [nil (first params)]
                      params)
        intercept #(apply e.p.interceptor/execute interceptor e.c.kind/connect %&)
        result (-> {:elin elin :host host :port port}
                   (intercept
                    (fn [{:as ctx :keys [host port]}]
                      (if (and host port)
                        (let [client (e.p.nrepl/add-client! nrepl host port)]
                          (e.p.nrepl/switch-client! nrepl client)
                          (assoc ctx :client client))
                        ctx))))]
    (if (contains? result :client)
      (e.log/info writer (format "Connected to %s:%s" (:host result) (:port result)))
      (e.log/warning writer "Host or port is not specified." (pr-str (select-keys result [:host :port]))))))

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

(defmethod handler* :evaluate
  [{:as elin :keys [message]}]
  (->> message
       (:params)
       (first)
       (evaluation* elin)))

(defmethod handler* :evaluate-current-top-list
  [{:as elin :keys [writer]}]
  (let [{:keys [lnum col]} (e.f.host/get-cursor-position writer)
        code (e.f.sexp/get-top-list writer lnum col)]
    (evaluation* elin code {:line lnum :column col})))

(defmethod handler* :evaluate-current-list
  [{:as elin :keys [writer]}]
  (let [{:keys [lnum col]} (e.f.host/get-cursor-position writer)
        code (e.f.sexp/get-list writer lnum col)]
    (evaluation* elin code {:line lnum :column col})))

(defmethod handler* :evaluate-current-expr
  [{:as elin :keys [writer]}]
  (let [{:keys [lnum col]} (e.f.host/get-cursor-position writer)
        code (e.f.sexp/get-expr writer lnum col)]
    (evaluation* elin code {:line lnum :column col})))

(defmethod handler* :lookup
  [{:component/keys [nrepl] :keys [writer]}]
  (let [{:keys [lnum col]} (e.f.host/get-cursor-position writer)
        ns (e.f.sexp/get-namespace writer)
        sym (e.f.sexp/get-expr writer lnum col)]
    (pr-str
     (async/<!! (e.f.n.op/lookup nrepl ns sym)))))

(defmethod handler* :test
  [_elin] "foo")
