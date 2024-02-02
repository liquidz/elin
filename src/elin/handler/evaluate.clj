(ns elin.handler.evaluate
  (:require
   [elin.error :as e]
   [elin.function.nrepl.op :as e.f.n.op]
   [elin.function.vim :as e.f.vim]
   [elin.function.vim.sexp :as e.f.v.sexp]
   [elin.schema.handler :as e.s.handler]
   [malli.core :as m]))

(m/=> evaluation* [:=> [:cat e.s.handler/?Elin string? map?] any?])
(defn- evaluation*
  [{:component/keys [nrepl]}
   code options]
  (e/let [options (reduce-kv (fn [accm k v]
                               (if v
                                 (assoc accm k v)
                                 accm))
                             {:nrepl.middleware.print/stream? 1}
                             options)
          res (e.f.n.op/eval!! nrepl code options)]
    (:value res)))

;; TODO status: ["namespace-not-found" "done" "error"]

(m/=> evaluate [:=> [:cat e.s.handler/?Elin] any?])
(defn evaluate
  [{:as elin :keys [message]}]
  (let [code (->> message
                  (:params)
                  (first))]
    (evaluation* elin code {})))

(m/=> evaluate-current-top-list [:=> [:cat e.s.handler/?Elin] any?])
(defn evaluate-current-top-list
  [{:as elin :component/keys [writer]}]
  (e/let [{:keys [lnum col]} (e.f.vim/get-cursor-position!! writer)
          ns-str (e.f.v.sexp/get-namespace!! writer)
          path (e.f.vim/get-full-path!! writer)
          {:keys [code lnum col]} (e.f.v.sexp/get-top-list!! writer lnum col)]
    (evaluation* elin code {:line lnum
                            :column col
                            :ns ns-str
                            :file path})))

(m/=> evaluate-current-list [:=> [:cat e.s.handler/?Elin] any?])
(defn evaluate-current-list
  [{:as elin :component/keys [writer]}]
  (e/let [{:keys [lnum col]} (e.f.vim/get-cursor-position!! writer)
          ns-str (e.f.v.sexp/get-namespace!! writer)
          path (e.f.vim/get-full-path!! writer)
          {:keys [code lnum col]} (e.f.v.sexp/get-list!! writer lnum col)]
    (evaluation* elin code {:line lnum
                            :column col
                            :ns ns-str
                            :file path})))

(m/=> evaluate-current-expr [:=> [:cat e.s.handler/?Elin] any?])
(defn evaluate-current-expr
  [{:as elin :component/keys [writer]}]
  (e/let [{:keys [lnum col]} (e.f.vim/get-cursor-position!! writer)
          ns-str (e.f.v.sexp/get-namespace!! writer)
          path (e.f.vim/get-full-path!! writer)
          {:keys [code lnum col]} (e.f.v.sexp/get-expr!! writer lnum col)]
    (evaluation* elin code {:line lnum
                            :column col
                            :ns ns-str
                            :file path})))
