(ns elin.handler.navigate
  (:require
   [elin.error :as e]
   [elin.function.nrepl.op :as e.f.n.op]
   [elin.function.vim :as e.f.vim]
   [elin.function.vim.sexp :as e.f.v.sexp]
   [elin.schema.handler :as e.s.handler]
   [malli.core :as m]))

(m/=> jump-to-definition [:=> [:cat e.s.handler/?Elin] any?])
(defn jump-to-definition
  [{:component/keys [nrepl host]}]
  (e/let [{:keys [lnum col]} (e.f.vim/get-cursor-position!! host)
          ns (e.f.v.sexp/get-namespace!! host)
          {:keys [code]} (e.f.v.sexp/get-expr!! host lnum col)
          {:keys [file line column]} (e.f.n.op/lookup!! nrepl ns code)]
    (when (and file line)
      (e.f.vim/jump!! host file line (or column 1)))
    true))
