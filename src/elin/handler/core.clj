(ns elin.handler.core
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.error :as e]
   [elin.function.nrepl.op :as e.f.n.op]
   [elin.function.vim :as e.f.vim]
   [elin.function.vim.sexp :as e.f.v.sexp]
   [elin.handler :as e.handler]
   [elin.protocol.interceptor :as e.p.interceptor]))

(defmethod e.handler/handler* :initialize
  [_]
  ;; TODO Load plugins
  "FIXME")

(defmethod e.handler/handler* :intercept
  [{:as elin :component/keys [interceptor] :keys [message]}]
  (let [autocmd-type (first (:params message))]
    (->> {:elin elin :autocmd-type autocmd-type}
         (e.p.interceptor/execute interceptor e.c.interceptor/autocmd))
    true))

(defmethod e.handler/handler* :lookup
  [{:component/keys [nrepl writer]}]
  (e/let [{:keys [lnum col]} (e.f.vim/get-cursor-position!! writer)
          ns (e.f.v.sexp/get-namespace!! writer)
          sym (e.f.v.sexp/get-expr!! writer lnum col)]
    (pr-str
     (e.f.n.op/lookup!! nrepl ns sym))))

(defmethod e.handler/handler* :jump-to-definition
  [{:component/keys [nrepl writer]}]
  (e/let [{:keys [lnum col]} (e.f.vim/get-cursor-position!! writer)
          ns (e.f.v.sexp/get-namespace!! writer)
          sym (e.f.v.sexp/get-expr!! writer lnum col)
          ;; TODO elin.function 配下の関数の戻り値を channel にするのか channel から読んだものにするのか統一する
          {:keys [file line column]} (e.f.n.op/lookup!! nrepl ns sym)]
    (e.f.vim/jump!! writer file line (or column 1))))

(defmethod e.handler/handler* :test
  [_elin] "foo")
