(ns elin.handler.navigate
  (:require
   [elin.error :as e]
   [elin.function.core :as e.f.core]
   [elin.function.nrepl.namespace :as e.f.n.namespace]
   [elin.function.vim :as e.f.vim]
   [elin.function.vim.sexp :as e.f.v.sexp]
   [elin.protocol.host :as e.p.host]
   [elin.schema.handler :as e.s.handler]
   [elin.util.file :as e.u.file]
   [malli.core :as m]))

(m/=> jump-to-definition [:=> [:cat e.s.handler/?Elin] any?])
(defn jump-to-definition
  [{:as elin :component/keys [host]}]
  (e/let [{:keys [lnum col]} (e.p.host/get-cursor-position!! host)
          ns (e.f.v.sexp/get-namespace!! host)
          {:keys [code]} (e.f.v.sexp/get-expr!! host lnum col)
          {:keys [file line column]} (e.f.core/lookup!! elin ns code)]
    (when (and file line)
      (e.p.host/jump!! host file line (or column 1)))
    true))

(m/=> cycle-source-and-test [:=> [:cat e.s.handler/?Elin] any?])
(defn cycle-source-and-test
  [{:component/keys [host]}]
  (let [ns-path (e.p.host/get-current-file-path!! host)
        ns-str (e.f.v.sexp/get-namespace!! host)
        file-sep (e.u.file/guess-file-separator ns-path)
        cycled-path (e.f.n.namespace/get-cycled-namespace-path
                     {:ns ns-str :path ns-path :file-separator file-sep})]
    (e.f.vim/notify host "elin#internal#open_file" [cycled-path])))
