(ns elin.interceptor.autocmd
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.constant.server :as e.c.server]
   [elin.error :as e]
   [elin.function.nrepl :as e.f.nrepl]
   [elin.function.vim :as e.f.vim]
   [elin.function.vim.sexp :as e.f.v.sexp]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.protocol.storage :as e.p.storage]))

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

(def setting-http-server-port-interceptor
  {:name ::setting-http-server-port-interceptor
   :kind e.c.interceptor/autocmd
   :enter (fn [{:as ctx :component/keys [host session-storage] :keys [autocmd-type]}]
            (when (contains? #{"BufRead" "BufEnter"} autocmd-type)
              (when-let [port (e.p.storage/get session-storage e.c.server/http-server-port-key)]
                (e.f.vim/set-variable!! host
                                        (str "g:" e.c.server/http-server-port-variable)
                                        port)
                ;; NOTE buffer scoped variable is used by coc.nvim
                (e.f.vim/set-variable!! host
                                        (str "b:" e.c.server/http-server-port-variable)
                                        port)))
            ctx)})
