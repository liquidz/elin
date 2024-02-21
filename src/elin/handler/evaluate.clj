(ns elin.handler.evaluate
  (:require
   [elin.error :as e]
   [elin.function.nrepl :as e.f.nrepl]
   [elin.function.nrepl.vim :as e.f.n.vim]
   [elin.function.vim :as e.f.vim]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.schema.handler :as e.s.handler]
   [malli.core :as m]))

;; TODO status: ["namespace-not-found" "done" "error"]

(m/=> evaluate [:=> [:cat e.s.handler/?Elin] any?])
(defn evaluate
  [{:component/keys [nrepl] :keys [message]}]
  (e/let [code (->> message
                    (:params)
                    (first))
          res (e.f.nrepl/eval!! nrepl code {})]
    (:value res)))

(m/=> evaluate-current-top-list [:=> [:cat e.s.handler/?Elin] any?])
(defn evaluate-current-top-list
  [{:component/keys [host nrepl]}]
  (e/-> (e.f.n.vim/evaluate-current-top-list!! {:host host :nrepl nrepl})
        (:response)))

(m/=> evaluate-current-list [:=> [:cat e.s.handler/?Elin] any?])
(defn evaluate-current-list
  [{:component/keys [host nrepl]}]
  (e/-> (e.f.n.vim/evaluate-current-list!! {:host host :nrepl nrepl})
        (:response)))

(m/=> evaluate-current-expr [:=> [:cat e.s.handler/?Elin] any?])
(defn evaluate-current-expr
  [{:component/keys [host nrepl]}]
  (e/-> (e.f.n.vim/evaluate-current-expr!! {:host host :nrepl nrepl})
        (:response)))

(m/=> load-current-file [:=> [:cat e.s.handler/?Elin] any?])
(defn load-current-file
  [{:component/keys [nrepl host]}]
  (e/let [path (e.f.vim/get-full-path!! host)
          _ (e.f.nrepl/load-file!! nrepl path)]
    (e.p.rpc/echo-text host "Required")
    true))
