(ns elin.handler.complete
  (:require
   [clojure.string :as str]
   [elin.error :as e]
   [elin.function.nrepl :as e.f.nrepl]
   [elin.function.nrepl.cider :as e.f.n.cider]
   [elin.function.sexpr :as e.f.sexpr]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema.handler :as e.s.handler]
   [malli.core :as m]))

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
  [{:as elin :component/keys [nrepl]} prefix]
  (e/let [ns-str (e.f.sexpr/get-namespace elin)
          candidates (e.f.n.cider/complete!! nrepl ns-str prefix)]
    (format-candidates candidates)))

(defn- nrepl-completions
  [{:as elin :component/keys [nrepl]} prefix]
  (e/let [ns-str (e.f.sexpr/get-namespace elin)
          candidates (e.f.nrepl/completions!! nrepl ns-str prefix)]
    (format-candidates candidates)))

(m/=> complete [:=> [:cat e.s.handler/?Elin] any?])
(defn complete
  "Returns comletion candidates."
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
