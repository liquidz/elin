(ns elin.handler.evaluate
  (:require
   [elin.error :as e]
   [elin.function.nrepl :as e.f.nrepl]
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
          res (e.f.nrepl/eval!! nrepl code options)]
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
  [{:as elin :component/keys [host]}]
  (e/let [{:keys [lnum col]} (e.f.vim/get-cursor-position!! host)
          ns-str (e.f.v.sexp/get-namespace!! host)
          path (e.f.vim/get-full-path!! host)
          {:keys [code lnum col]} (e.f.v.sexp/get-top-list!! host lnum col)]
    (evaluation* elin code {:line lnum
                            :column col
                            :ns ns-str
                            :file path})))

(m/=> evaluate-current-list [:=> [:cat e.s.handler/?Elin] any?])
(defn evaluate-current-list
  [{:as elin :component/keys [host]}]
  (e/let [{:keys [lnum col]} (e.f.vim/get-cursor-position!! host)
          ns-str (e.f.v.sexp/get-namespace!! host)
          path (e.f.vim/get-full-path!! host)
          {:keys [code lnum col]} (e.f.v.sexp/get-list!! host lnum col)]
    (evaluation* elin code {:line lnum
                            :column col
                            :ns ns-str
                            :file path})))

(m/=> evaluate-current-expr [:=> [:cat e.s.handler/?Elin] any?])
(defn evaluate-current-expr
  [{:as elin :component/keys [host]}]
  (e/let [{:keys [lnum col]} (e.f.vim/get-cursor-position!! host)
          ns-str (e.f.v.sexp/get-namespace!! host)
          path (e.f.vim/get-full-path!! host)
          {:keys [code lnum col]} (e.f.v.sexp/get-expr!! host lnum col)]
    (evaluation* elin code {:line lnum
                            :column col
                            :ns ns-str
                            :file path})))

(m/=> load-current-file [:=> [:cat e.s.handler/?Elin] any?])
(defn load-current-file
  [{:component/keys [nrepl host]}]
  (e/let [path (e.f.vim/get-full-path!! host)]
    (e.f.nrepl/load-file!! nrepl path)
    true))
