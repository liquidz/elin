(ns elin.handler.lookup
  (:require
   [clojure.core.async :as async]
   [clojure.string :as str]
   [elin.error :as e]
   [elin.function.evaluate :as e.f.evaluate]
   [elin.function.lookup :as e.f.lookup]
   [elin.function.sexpr :as e.f.sexpr]
   [elin.protocol.host :as e.p.host]
   [elin.schema :as e.schema]
   [elin.schema.handler :as e.s.handler]
   [elin.schema.nrepl :as e.s.nrepl]
   [elin.util.file :as e.u.file]
   [elin.util.handler :as e.u.handler]
   [elin.util.sexpr :as e.u.sexpr]
   [malli.core :as m]
   [pogonos.core :as pogonos]))

(m/=> generate-doc [:=> [:cat map? e.s.nrepl/?RenderingData] string?])
(defn- generate-doc
  [format-config {:as rendering-data :keys [format-type]}]
  (if-let [format-str (get format-config format-type)]
    (pogonos/render-string format-str rendering-data)
    (e/unsupported {:message (str "Not supported format type: " format-type)})))

(defn- parse-code-to-ns-and-name
  [code]
  (let [[head tail] (str/split code #"/" 2)]
    (if tail
      [head tail]
      ["user" head])))

(m/=> lookup [:=> [:cat e.s.handler/?Elin] any?])
(defn lookup
  "Look up symbol at cursor position."
  [{:as elin :component/keys [host]}]
  (e/let [{:as config :keys [lookup-config]} (e.u.handler/config elin #'lookup)
          {:keys [lnum col]} (async/<!! (e.p.host/get-cursor-position! host))
          {:keys [code]} (e.f.sexpr/get-expr elin lnum col)
          ns-str (e/error-or (e.f.sexpr/get-namespace elin))
          resp (if ns-str
                 (e.f.lookup/lookup elin ns-str code lookup-config)
                 (let [[ns-str sym-str] (parse-code-to-ns-and-name code)]
                   (e.f.lookup/lookup elin ns-str sym-str lookup-config)))
          doc-str (generate-doc (:format config)
                    (e.f.lookup/get-lookup-rendering-data resp))]
    (reduce
      (fn [accm [from to]]
        (str/replace accm from to))
      doc-str
      (:replace-string config))))

(m/=> show-source [:=> [:cat e.s.handler/?Elin] (e.schema/error-or string?)])
(defn show-source
  "Show source code of symbol at cursor position."
  [{:as elin :component/keys [host]}]
  (e/let [{:keys [lookup-config]} (e.u.handler/config elin #'show-source)
          {:keys [lnum col]} (async/<!! (e.p.host/get-cursor-position! host))
          {:keys [code]} (e.f.sexpr/get-expr elin lnum col)
          ns-str (e/error-or (e.f.sexpr/get-namespace elin))
          resp (if ns-str
                 (e.f.lookup/lookup elin ns-str code lookup-config)
                 (let [[ns-str sym-str] (parse-code-to-ns-and-name code)]
                   (e.f.lookup/lookup elin ns-str sym-str lookup-config)))
          path (:file resp)
          content (e.u.file/slurp path)]
    (if (:local? resp)
      (e.u.sexpr/extract-local-binding-by-position
        content
        (:line resp)
        (:column resp))
      (e.u.sexpr/extract-form-by-position
        content
        (:line resp)
        (:column resp)))))

(defn show-clojuredocs
  "Show clojuredocs of symbol at cursor position."
  [elin]
  (e/let [{:as config :keys [lookup-config]} (e.u.handler/config elin #'show-clojuredocs)
          export-edn-url (:export-edn-url (e.u.handler/config elin #'show-clojuredocs))
          resp (e.f.lookup/clojuredocs-lookup elin export-edn-url lookup-config)]
    (generate-doc {:clojuredocs (:format config)}
                  (e.f.lookup/get-clojuredocs-rendering-data resp))))

(defn open-javadoc
  "Open a browser window displaying the javadoc for a symbol a t cursor position."
  [{:as elin :component/keys [host]}]
  (e/let [{:keys [lnum col]} (async/<!! (e.p.host/get-cursor-position! host))
          ns-str (e/error-or (e.f.sexpr/get-namespace elin))
          {:keys [code]} (e.f.sexpr/get-expr elin lnum col)
          code (str `((requiring-resolve 'clojure.java.javadoc/javadoc) ~(symbol code)))]
    (e.f.evaluate/evaluate-code elin code (when ns-str
                                            {:ns ns-str}))))
