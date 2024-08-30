(ns elin.function.nrepl
  (:refer-clojure :exclude [eval load-file])
  (:require
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.string :as str]
   [elin.constant.nrepl :as e.c.nrepl]
   [elin.error :as e]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema :as e.schema]
   [elin.schema.component :as e.s.component]
   [elin.schema.nrepl :as e.s.nrepl]
   [elin.util.nrepl :as e.u.nrepl]
   [malli.core :as m]))

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

(m/=> close!! [:function
               [:=> [:cat e.s.component/?Nrepl] any?]
               [:=> [:cat e.s.component/?Nrepl string?] any?]])
(defn close!!
  ([nrepl]
   (if-let [session (e.p.nrepl/current-session nrepl)]
     (close!! nrepl session)
     (e/unavailable {:message "Not connected"})))
  ([nrepl session]
   (async/<!! (e.p.nrepl/request nrepl {:op e.c.nrepl/close-op :session session}))))

(m/=> eval!! [:function
              [:=> [:cat e.s.component/?Nrepl string?] any?]
              [:=> [:cat e.s.component/?Nrepl string? map?] any?]])
(defn eval!!
  ([nrepl code]
   (eval!! nrepl code {}))
  ([nrepl code options]
   (if-let [session (e.p.nrepl/current-session nrepl)]
     (let [{:keys [middleware]} options
           eval-fn (fn [code' options']
                     (e/->> (merge (select-keys options' eval-option-keys)
                                   {:op e.c.nrepl/eval-op :session session  :code code'})
                            (e.p.nrepl/request nrepl)
                            (async/<!!)
                            (e.u.nrepl/merge-messages)))
           eval-fn' (if middleware
                      (middleware eval-fn)
                      eval-fn)]
       (eval-fn' code options))
     (e/unavailable {:message "Not connected"}))))

(m/=> interrupt!! [:function
                   [:=> [:cat e.s.component/?Nrepl] any?]
                   [:=> [:cat e.s.component/?Nrepl map?] any?]])
(defn interrupt!!
  ([nrepl]
   (interrupt!! nrepl {}))
  ([nrepl options]
   (if-let [session (e.p.nrepl/current-session nrepl)]
     (e/->> (merge (select-keys options #{:interrupt-id})
                   {:op e.c.nrepl/interrupt-op :session session})
            (e.p.nrepl/request nrepl)
            (async/<!!))
     (e/unavailable {:message "Not connected"}))))

(m/=> load-file!! [:function
                   [:=> [:cat e.s.component/?Nrepl string? [:sequential string?]] any?]
                   [:=> [:cat e.s.component/?Nrepl string? [:sequential string?] map?] any?]])
(defn load-file!!
  ([nrepl file-path contents]
   (load-file!! nrepl file-path contents {}))
  ([nrepl file-path contents options]
   (let [session (e.p.nrepl/current-session nrepl)
         file (io/file file-path)]
     (cond
       (not session)
       (e/unavailable {:message "Not connected"})

       (not (.exists file))
       (e/not-found {:message (str "File not found: " file-path)})

       :else
       (->> (merge (select-keys options load-file-option-keys)
                   {:op e.c.nrepl/load-file-op
                    :session session
                    :file (str/join "\n" contents)
                    :file-name (.getName file)
                    :file-path file-path})
            (e.p.nrepl/request nrepl)
            (async/<!!)
            (e.u.nrepl/merge-messages))))))

(m/=> lookup!! [:=> [:cat e.s.component/?Nrepl string? string?] (e.schema/error-or e.s.nrepl/?Lookup)])
(defn lookup!!
  [nrepl ns-str sym-str]
  (e/let [res (e/-> (e.p.nrepl/request nrepl {:op e.c.nrepl/lookup-op :ns ns-str :sym sym-str})
                    (async/<!!)
                    (e.u.nrepl/merge-messages))
          _ (when (e.u.nrepl/has-status? res "lookup-error")
              (e/not-found {:message (format "Not found: %s/%s" ns-str sym-str)}))
          _ (when (= [] (:info res))
              (e/not-found {:message (format "Not found: %s/%s" ns-str sym-str)}))
          res' (or (:info res)
                   res)]
    (cond
      (or (= [] (:ns res'))
          (= [] (:name res')))
      (e/not-found {:message (format "Not found: %s/%s" ns-str sym-str)})

      :else
      res')))

(m/=> ls-sessions!! [:=> [:cat e.s.component/?Nrepl] e.schema/?ManyToManyChannel])
(defn ls-sessions!!
  [nrepl]
  (e/-> (e.p.nrepl/request nrepl {:op e.c.nrepl/ls-sessions-op})
        (async/<!!)
        (e.u.nrepl/merge-messages)
        (:sessions)))

(defn completions!!
  [nrepl ns-str prefix]
  (e/-> (e.p.nrepl/request nrepl {:op e.c.nrepl/completions-op
                                  :prefix prefix
                                  :ns ns-str})
        (async/<!!)
        (e.u.nrepl/merge-messages)
        (:completions)))
