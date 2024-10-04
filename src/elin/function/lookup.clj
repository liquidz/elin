(ns elin.function.lookup
  (:require
   [clojure.core.async :as async]
   [clojure.string :as str]
   [elin.error :as e]
   [elin.function.clj-kondo :as e.f.clj-kondo]
   [elin.function.nrepl.cider :as e.f.n.cider]
   [elin.function.sexpr :as e.f.sexpr]
   [elin.protocol.host :as e.p.host]
   [elin.schema.handler :as e.s.handler]
   [elin.schema.nrepl :as e.s.nrepl]
   [malli.core :as m]
   [malli.util :as m.util]))

(def ?NreplAndCljKondo
  (m.util/select-keys e.s.handler/?Components [:component/nrepl :component/clj-kondo]))

(defn- local-lookup
  [{:as elin :component/keys [host clj-kondo]} ns-str sym-str]
  (e/let [{cur-lnum :lnum cur-col :col} (async/<!! (e.p.host/get-cursor-position! host))
          {ns-code :code} (e.f.sexpr/get-namespace-sexpr elin)
          {code :code base-lnum :lnum base-col :col} (e.f.sexpr/get-top-list elin cur-lnum cur-col)
          path (async/<!! (e.p.host/get-current-file-path! host))
          ;; NOTE ns-code is required for clj-kondo to analyze correctly if you use plumatic/schema etc.
          code' (str ns-code "\n" code)
          {lnum :line col :column} (e.f.clj-kondo/local-lookup clj-kondo code' sym-str)]
    {:ns ns-str
     :name sym-str
     :file path
     :arglists-str ""
     :line (+ base-lnum (- lnum (count (str/split-lines ns-code)) 1))
     :column (+ base-col (dec col))}))

(defn- protocol-lookup
  [{:component/keys [clj-kondo]}
   protocol-var-str
   info-response]
  (let [[protocol-ns protocol-name] (-> protocol-var-str
                                        (str/replace-first #"^#?'" "")
                                        (str/split #"/" 2))
        impls (e.f.clj-kondo/protocol-implementations
               clj-kondo protocol-ns protocol-name (:name info-response))]
    (assoc info-response :protocol-implementations impls)))

(m/=> lookup [:=> [:cat ?NreplAndCljKondo string? string?] e.s.nrepl/?Lookup])
(defn lookup
  [{:as elin :component/keys [nrepl clj-kondo]}
   ns-str
   sym-str]
  (try
    (let [res (e.f.n.cider/info!! nrepl ns-str sym-str)
          error? (e/error? res)
          protocol-var-str (when-not error?
                             (:protocol res))
          proto-def (when (and (not error?)
                               (not protocol-var-str))
                      (e.f.clj-kondo/protocol-definition clj-kondo (:ns res) (:name res)))]
      (cond
        protocol-var-str
        (protocol-lookup elin protocol-var-str res)

        proto-def
        (protocol-lookup elin
                         (format "%s/%s" (:protocol-ns proto-def) (:protocol-name proto-def))
                         res)

        (not error?)
        res

        :else
        (let [res (e.f.clj-kondo/lookup clj-kondo ns-str sym-str)]
          (if-not (e/error? res)
            res
            (local-lookup elin ns-str sym-str)))))

    (catch Exception e
      (e/fault {:message (pr-str e)}))))
