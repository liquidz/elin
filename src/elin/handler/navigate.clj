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
  [{:component/keys [nrepl writer]}]
  (e/let [{:keys [lnum col]} (e.f.vim/get-cursor-position!! writer)
          ns (e.f.v.sexp/get-namespace!! writer)
          {:keys [code]} (e.f.v.sexp/get-expr!! writer lnum col)
          {:keys [file line column]} (e.f.n.op/lookup!! nrepl ns code)]
    (when (and file line)
      (e.f.vim/jump!! writer file line (or column 1)))
    true))
