(ns elin.handler.navigate
  (:require
   [elin.error :as e]
   [elin.function.nrepl :as e.f.nrepl]
   [elin.function.nrepl.namespace :as e.f.n.namespace]
   [elin.function.nrepl.system :as e.f.n.system]
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
          ;; TODO Use cider-nrepl's info!!
          {:keys [file line column]} (e.f.nrepl/lookup!! nrepl ns code)]
    (when (and file line)
      (e.f.vim/jump!! host file line (or column 1)))
    true))

(m/=> cycle-source-and-test [:=> [:cat e.s.handler/?Elin] any?])
(defn cycle-source-and-test
  [{:component/keys [host nrepl]}]
  (let [ns-path (e.f.vim/get-current-file-path!! host)
        ns-str (e.f.v.sexp/get-namespace!! host)
        file-sep (e.f.n.system/get-file-separator nrepl)
        cycled-path (e.f.n.namespace/get-cycled-namespace-path
                     {:ns ns-str :path ns-path :file-separator file-sep})]
    (e.f.vim/notify host "elin#internal#open_file" [cycled-path])))
