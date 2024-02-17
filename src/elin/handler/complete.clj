(ns elin.handler.complete
  (:require
   [clojure.string :as str]
   [elin.error :as e]
   [elin.function.nrepl :as e.f.nrepl]
   [elin.function.nrepl.cider-nrepl.op :as e.f.n.c.op]
   [elin.function.vim.sexp :as e.f.v.sexp]
   [elin.protocol.nrepl :as e.p.nrepl]))

(def ^:private type->kind
  {"var" "v"
   "function" "f"
   "keyword" "k"
   "class" "c"
   "field" "i"
   "local" "l"
   "macro" "m"
   "method" "f"
   "namespace" "n"
   "resource" "r"
   "special-form" "s"
   "static-field" "i"
   "static-method" "f"})

(defn- format-arglist
  [arglist]
  (if (= 0 (str/index-of arglist "(quote "))
    (subs arglist 7 (dec (count arglist)))
    arglist))

(defn- format-candidate
  [{:keys [arglists candidate doc type]}]
  {:word candidate
   :kind (get type->kind type "v")
   :menu (->> (or arglists [])
              (map format-arglist)
              (str/join " "))
   :info (or doc "")
   :icase 1})

(defn- format-candidates
  [candidates]
  (->> candidates
       (map format-candidate)
       (sort-by :word)))

(defn- cider-nrepl-complete
  [{:component/keys [nrepl hsot]} prefix]
  (e/let [ns-str (e.f.v.sexp/get-namespace!! hsot)
          candidates (e.f.n.c.op/complete!! nrepl ns-str prefix)]
    (format-candidates candidates)))

(defn- nrepl-completions
  [{:component/keys [nrepl hsot]} prefix]
  (e/let [ns-str (e.f.v.sexp/get-namespace!! hsot)
          candidates (e.f.nrepl/completions!! nrepl ns-str prefix)]
    (format-candidates candidates)))

(defn complete
  [{:as elin :component/keys [nrepl] :keys [message]}]
  (let [prefix (first (:params message))]
    (if (e.p.nrepl/disconnected? nrepl)
      []
      (cond
        ;; cider-nrepl
        (e.p.nrepl/supported-op? nrepl :complete)
        (cider-nrepl-complete elin prefix)

        ;; nrepl
        (e.p.nrepl/supported-op? nrepl :completions)
        (nrepl-completions elin prefix)

        :else
        []))))
