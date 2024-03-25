(ns elin.function.nrepl.vim
  (:require
   [elin.error :as e]
   [elin.function.nrepl :as e.f.nrepl]
   [elin.function.vim.sexp :as e.f.v.sexp]
   [elin.protocol.host :as e.p.host]
   [elin.util.nrepl :as e.u.nrepl]))

(defn- eval!!
  [nrepl code options]
  (e/let [options (reduce-kv (fn [accm k v]
                               (if v
                                 (assoc accm k v)
                                 accm))
                             {:nrepl.middleware.print/stream? 1}
                             options)
          resp (e.f.nrepl/eval!! nrepl code options)]
    (if (e.u.nrepl/has-status? resp "eval-error")
      (e/fault {:message (:err resp)})
      {:code code
       :options options
       :response resp})))

(defn evaluate-current-top-list!!
  [{:keys [nrepl host options]}]
  (e/let [{cur-lnum :lnum cur-col :col} (e.p.host/get-cursor-position!! host)
          ns-str (e.f.v.sexp/get-namespace!! host)
          path (e.p.host/get-current-file-path!! host)
          {:keys [code lnum col]} (e.f.v.sexp/get-top-list!! host cur-lnum cur-col)]
    (eval!! nrepl code (merge options
                              {:line lnum
                               :column col
                               :cursor-line cur-lnum
                               :cursor-column cur-col
                               :ns ns-str
                               :file path}))))

(defn evaluate-current-list!!
  [{:keys [nrepl host options]}]
  (e/let [{cur-lnum :lnum cur-col :col} (e.p.host/get-cursor-position!! host)
          ns-str (e.f.v.sexp/get-namespace!! host)
          path (e.p.host/get-current-file-path!! host)
          {:keys [code lnum col]} (e.f.v.sexp/get-list!! host cur-lnum cur-col)]
    (eval!! nrepl code (merge options
                              {:line lnum
                               :column col
                               :cursor-line cur-lnum
                               :cursor-column cur-col
                               :ns ns-str
                               :file path}))))

(defn evaluate-current-expr!!
  [{:keys [nrepl host options]}]
  (e/let [{cur-lnum :lnum cur-col :col} (e.p.host/get-cursor-position!! host)
          ns-str (e.f.v.sexp/get-namespace!! host)
          path (e.p.host/get-current-file-path!! host)
          {:keys [code lnum col]} (e.f.v.sexp/get-expr!! host cur-lnum cur-col)]
    (eval!! nrepl code (merge options
                              {:line lnum
                               :column col
                               :cursor-line cur-lnum
                               :cursor-column cur-col
                               :ns ns-str
                               :file path}))))

(defn evaluate-namespace-form!!
  [{:keys [nrepl host options]}]
  (e/let [ns-form (e.f.v.sexp/get-namespace-form!! host)
          path (e.p.host/get-current-file-path!! host)]
    (eval!! nrepl ns-form (merge options
                                 {:file path}))))
