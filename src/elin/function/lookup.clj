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

(m/=> local-lookup [:=> [:cat ?NreplAndCljKondo string? string?] e.s.nrepl/?Lookup])
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
     :column (+ base-col (dec col))
     :local? true}))

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
                               (not protocol-var-str)
                               (:ns res)
                               (:name res))
                      (e.f.clj-kondo/protocol-definition clj-kondo (:ns res) (:name res)))]
      (cond
        protocol-var-str
        (protocol-lookup elin protocol-var-str res)

        proto-def
        (protocol-lookup elin
                         (format "%s/%s" (:protocol-ns proto-def) (:protocol-name proto-def))
                         res)

        (and (not error?)
             (or
               ;; clojure
               (contains? res :name)
               ;; java
               (contains? res :class)))
        res

        :else
        (let [res (e.f.clj-kondo/lookup clj-kondo ns-str sym-str)]
          (if-not (e/error? res)
            res
            (local-lookup elin ns-str sym-str)))))

    (catch Exception e
      (e/fault {:message (pr-str e)}))))

(defn clojuredocs-lookup
  "Returns a result of looking up the help of the function under the cursor in clojuredocs."
  [{:as elin :component/keys [host nrepl]} export-edn-url]
  (e/let [{:keys [lnum col]} (async/<!! (e.p.host/get-cursor-position! host))
          {:keys [code]} (e.f.sexpr/get-expr elin lnum col)
          [ns-str name-str] (e/error-or
                              (e/let [ns-str (e.f.sexpr/get-namespace elin)
                                      resp (lookup elin ns-str code)]
                                [(:ns resp) (:name resp)])
                              (str/split code #"/" 2))]
    (or (e/error-or (e.f.n.cider/clojuredocs-lookup!! nrepl ns-str name-str export-edn-url))
        (e/not-found))))

(m/=> get-java-rendering-data [:-> e.s.nrepl/?Lookup e.s.nrepl/?LookupJavaRenderingData])
(defn- get-java-rendering-data
  [lookup-resp]
  {:format-type :java
   :name (if-let [member-str (:member lookup-resp)]
           (format "%s/%s" (:class lookup-resp) member-str)
           (:class lookup-resp))
   :arglists (or (some->> (:arglists-str lookup-resp)
                          (str/split-lines))
                 [])
   :document (:doc lookup-resp)
   :return (:returns lookup-resp)
   :javadoc (:javadoc lookup-resp)})

(m/=> get-clojure-rendering-data [:-> e.s.nrepl/?Lookup e.s.nrepl/?LookupClojureRenderingData])
(defn- get-clojure-rendering-data
  [{:as lookup-resp :keys [see-also]}]
  {:format-type :clojure
   :name (if-let [ns-str (:ns lookup-resp)]
           (format "%s/%s" ns-str (:name lookup-resp))
           (:name lookup-resp))
   :arglists (or (some->> (:arglists-str lookup-resp)
                          (str/split-lines))
                 [])
   :document (:doc lookup-resp)
   :has-see-alsos (some? (seq see-also))
   :see-also-count (or (some-> see-also count)
                       0)
   :see-alsos (->> see-also
                   (map-indexed (fn [idx v]
                                  {:index idx
                                   :content v})))})

(m/=> get-lookup-rendering-data [:-> e.s.nrepl/?Lookup e.s.nrepl/?RenderingData])
(defn get-lookup-rendering-data
  [lookup-resp]
  (if (contains? lookup-resp :javadoc)
    (get-java-rendering-data lookup-resp)
    (get-clojure-rendering-data lookup-resp)))

(m/=> get-clojuredocs-rendering-data [:-> map? e.s.nrepl/?RenderingData])
(defn get-clojuredocs-rendering-data
  [{:as cljdocs-resp :keys [examples see-alsos notes]}]
  {:format-type :clojuredocs
   :name (format "%s/%s" (:ns cljdocs-resp) (:name cljdocs-resp))
   :arglists (or (some->> (:arglists cljdocs-resp)
                          (map str/trim))
                 [])
   :document (:doc cljdocs-resp)
   :has-examples (some? (seq examples))
   :example-count (or (some-> examples count)
                      0)
   :examples (->> examples
                  (map-indexed (fn [idx v]
                                 {:index idx
                                  :content v})))
   :has-see-alsos (some? (seq see-alsos))
   :see-also-count (or (some-> see-alsos count)
                       0)
   :see-alsos (->> see-alsos
                   (map-indexed (fn [idx v]
                                  {:index idx
                                   :content v})))
   :has-notes (some? (seq notes))
   :note-count (or (some-> notes count)
                   0)
   :notes (->> notes
               (map-indexed (fn [idx v]
                              {:index idx
                               :content v})))})
