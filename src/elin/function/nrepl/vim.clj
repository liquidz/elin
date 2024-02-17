(ns elin.function.nrepl.vim
  (:require
   [elin.error :as e]
   [elin.function.nrepl :as e.f.nrepl]
   [elin.function.vim :as e.f.vim]
   [elin.function.vim.sexp :as e.f.v.sexp]))

(defn- eval!!
  [nrepl code options]
  (e/let [options (reduce-kv (fn [accm k v]
                               (if v
                                 (assoc accm k v)
                                 accm))
                             {:nrepl.middleware.print/stream? 1}
                             options)]
    {:code code
     :options options
     :response (e.f.nrepl/eval!! nrepl code options)}))

(defn evaluate-current-top-list!!
  [{:keys [nrepl host]}]
  (e/let [{:keys [lnum col]} (e.f.vim/get-cursor-position!! host)
          ns-str (e.f.v.sexp/get-namespace!! host)
          path (e.f.vim/get-full-path!! host)
          {:keys [code lnum col]} (e.f.v.sexp/get-top-list!! host lnum col)]
    (eval!! nrepl code {:line lnum
                        :column col
                        :ns ns-str
                        :file path})))

(defn evaluate-current-list!!
  [{:keys [nrepl host]}]
  (e/let [{:keys [lnum col]} (e.f.vim/get-cursor-position!! host)
          ns-str (e.f.v.sexp/get-namespace!! host)
          path (e.f.vim/get-full-path!! host)
          {:keys [code lnum col]} (e.f.v.sexp/get-list!! host lnum col)]
    (eval!! nrepl code {:line lnum
                        :column col
                        :ns ns-str
                        :file path})))

(defn evaluate-current-expr!!
  [{:keys [nrepl host]}]
  (e/let [{:keys [lnum col]} (e.f.vim/get-cursor-position!! host)
          ns-str (e.f.v.sexp/get-namespace!! host)
          path (e.f.vim/get-full-path!! host)
          {:keys [code lnum col]} (e.f.v.sexp/get-expr!! host lnum col)]
    (eval!! nrepl code {:line lnum
                        :column col
                        :ns ns-str
                        :file path})))
