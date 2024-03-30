(ns elin.handler.navigate
  (:require
   [clojure.core.async :as async]
   [elin.error :as e]
   [elin.function.file :as e.f.file]
   [elin.function.lookup :as e.f.lookup]
   [elin.function.nrepl.namespace :as e.f.n.namespace]
   [elin.function.sexpr :as e.f.sexpr]
   [elin.protocol.host :as e.p.host]
   [elin.schema.handler :as e.s.handler]
   [elin.util.file :as e.u.file]
   [malli.core :as m]))

(m/=> jump-to-definition [:=> [:cat e.s.handler/?Elin] any?])
(defn jump-to-definition
  [{:as elin :component/keys [host]}]
  (e/let [{:keys [lnum col]} (async/<!! (e.p.host/get-cursor-position! host))
          ns-str (e.f.sexpr/get-namespace elin)
          {:keys [code]} (e.f.sexpr/get-expr elin lnum col)
          {:keys [file line column]} (e.f.lookup/lookup elin ns-str code)]
    (when (and file line)
      (async/<!! (e.p.host/jump! host file line (or column 1))))
    true))

(m/=> cycle-source-and-test [:=> [:cat e.s.handler/?Elin] any?])
(defn cycle-source-and-test
  [{:as elin :component/keys [host]}]
  (let [ns-path (async/<!! (e.p.host/get-current-file-path! host))
        ns-str (e.f.sexpr/get-namespace elin)
        file-sep (e.u.file/guess-file-separator ns-path)
        cycled-path (e.f.n.namespace/get-cycled-namespace-path
                     {:ns ns-str :path ns-path :file-separator file-sep})]
    (e.f.file/open-as elin cycled-path)))
