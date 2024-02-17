(ns elin.interceptor.autocmd
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.error :as e]
   [elin.function.nrepl :as e.f.nrepl]
   [elin.function.vim :as e.f.vim]
   [elin.function.vim.sexp :as e.f.v.sexp]
   [elin.protocol.nrepl :as e.p.nrepl]))

(def ^:priavte ns-created-var-name
  "b:elin_ns_created")

(def ns-create-interceptor
  {:name ::ns-create-interceptor
   :kind e.c.interceptor/autocmd
   :enter (fn [{:as ctx :component/keys [host nrepl] :keys [autocmd-type]}]
            (when (and (contains? #{"BufRead" "BufEnter"} autocmd-type)
                       (not (e.p.nrepl/disconnected? nrepl))
                       (nil? (e.f.vim/get-variable!! host ns-created-var-name)))
              (e/let [ns-str (e.f.v.sexp/get-namespace!! host)
                      ns-sym (or (symbol ns-str)
                                 (e/incorrect))]
                (->> `(when-not (clojure.core/find-ns '~ns-sym)
                        (clojure.core/create-ns '~ns-sym)
                        (clojure.core/in-ns '~ns-sym)
                        (clojure.core/refer-clojure))
                     (str)
                     (e.f.nrepl/eval!! nrepl))
                (e.f.vim/set-variable!! host ns-created-var-name true)))
            ctx)})
