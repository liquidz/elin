(ns elin.function.nrepl.op
  (:refer-clojure :exclude [eval load-file])
  (:require
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema.component :as e.s.component]
   [elin.util.nrepl :as e.u.nrepl]
   [malli.core :as m]))

(def ?Lookup
  [:map
   [:ns string?]
   [:name string?]
   [:file string?]
   [:arglists-str string?]
   [:column int?]
   [:line int?]
   [:doc {:optional true} string?]
   ;; cider-nrepl's info op
   [:arglists {:optional true} [:maybe string?]]])

(def middleware-caught-keys
  #{:nrepl.middleware.caught/caught
    :nrepl.middleware.caught/print?})

(def middleware-print-keys
  #{:nrepl.middleware.print/buffer-size
    :nrepl.middleware.print/keys
    :nrepl.middleware.print/options
    :nrepl.middleware.print/print
    :nrepl.middleware.print/quota
    :nrepl.middleware.print/stream?})

(def ^:private eval-option-keys
  (set/union
   #{:column :eval :file :line :ns :read-cond}
   middleware-caught-keys
   middleware-print-keys))

(def ^:private load-file-option-keys
  (set/union
   #{:file-name :file-path}
   middleware-caught-keys
   middleware-print-keys))

(m/=> close [:function
             [:=> [:cat e.s.component/?Nrepl] any?]
             [:=> [:cat e.s.component/?Nrepl string?] any?]])
(defn close
  ([nrepl]
   (when-let [{:keys [session]} (e.p.nrepl/current-client nrepl)]
     (close nrepl session)))
  ([nrepl session]
   (e.p.nrepl/request nrepl {:op "close" :session session})))

(m/=> eval [:function
            [:=> [:cat e.s.component/?Nrepl string?] any?]
            [:=> [:cat e.s.component/?Nrepl string? map?] any?]])
(defn eval
  ([nrepl code]
   (eval nrepl code {}))
  ([nrepl code options]
   (when-let [{:keys [session]} (e.p.nrepl/current-client nrepl)]
     (async/go
       (->> (merge (select-keys options eval-option-keys)
                   {:op "eval" :session session  :code code})
            (e.p.nrepl/request nrepl)
            (async/<!)
            (e.u.nrepl/merge-messages))))))

(m/=> interrupt [:function
                 [:=> [:cat e.s.component/?Nrepl] any?]
                 [:=> [:cat e.s.component/?Nrepl map?] any?]])
(defn interrupt
  ([nrepl]
   (interrupt nrepl {}))
  ([nrepl options]
   (when-let [{:keys [session]} (e.p.nrepl/current-client nrepl)]
     (->> (merge (select-keys options #{:interrupt-id})
                 {:op "interrupt" :session session})
          (e.p.nrepl/request nrepl)))))

(m/=> load-file [:function
                 [:=> [:cat e.s.component/?Nrepl string?] any?]
                 [:=> [:cat e.s.component/?Nrepl string? map?] any?]])
(defn load-file
  ([nrepl file-path]
   (load-file nrepl file-path {}))
  ([nrepl file-path options]
   (let [{:keys [session]} (e.p.nrepl/current-client nrepl)
         file (io/file file-path)]
     (when (and session (.exists file))
       (->> (merge (select-keys options load-file-option-keys)
                   {:op "load-file"
                    :session session
                    :file (slurp file)
                    :file-name (.getName file)
                    :file-path file-path})
            (e.p.nrepl/request nrepl))))))

(m/=> lookup [:=> [:cat e.s.component/?Nrepl string? string?] ?Lookup])
(defn lookup
  [nrepl ns-str sym-str]
  (async/go
    (if (e.p.nrepl/supported-op? nrepl "info")
      (-> (e.p.nrepl/request nrepl {:op "info" :ns ns-str :sym sym-str})
          (async/<!)
          (e.u.nrepl/merge-messages))
      (-> (e.p.nrepl/request nrepl {:op "lookup" :ns ns-str :sym sym-str})
          (async/<!)
          (e.u.nrepl/merge-messages)
          (:info)))))

(m/=> ls-sessions [:=> [:cat e.s.component/?Nrepl] [:sequential string?]])
(defn ls-sessions
  [nrepl]
  (async/go
    (-> (e.p.nrepl/request nrepl {:op "ls-sessions"})
        (async/<!)
        (e.u.nrepl/merge-messages)
        (:sessions))))
