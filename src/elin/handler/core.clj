(ns elin.handler.core
  (:require
   [clojure.core.async :as async]
   [elin.function.host :as e.f.host]
   [elin.function.nrepl.op :as e.f.n.op]
   [elin.function.sexp :as e.f.sexp]
   [elin.handler :as e.handler]))

(defmethod e.handler/handler* :initialize
  [_]
  ;; TODO Load plugins
  "FIXME")

(defmethod e.handler/handler* :lookup
  [{:component/keys [nrepl writer]}]
  (let [{:keys [lnum col]} (e.f.host/get-cursor-position writer)
        ns (e.f.sexp/get-namespace writer)
        sym (e.f.sexp/get-expr writer lnum col)]
    (pr-str
     (async/<!! (e.f.n.op/lookup nrepl ns sym)))))

(defmethod e.handler/handler* :jump-to-definition
  [{:component/keys [nrepl writer]}]
  (let [{:keys [lnum col]} (e.f.host/get-cursor-position writer)
        ns (e.f.sexp/get-namespace writer)
        sym (e.f.sexp/get-expr writer lnum col)
        ;; TODO elin.function 配下の関数の戻り値を channel にするのか channel から読んだものにするのか統一する
        {:keys [file line column]} (async/<!! (e.f.n.op/lookup nrepl ns sym))]
    (e.f.host/jump writer file line (or column 1))))

(defmethod e.handler/handler* :test
  [_elin] "foo")
