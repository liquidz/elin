(ns elin.interceptor.nrepl
  (:require
   [clojure.core.async :as async]
   [clojure.string :as str]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.constant.nrepl :as e.c.nrepl]
   [elin.message :as e.message]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.util.file :as e.u.file]
   [elin.util.id :as e.u.id]
   [elin.util.map :as e.u.map]
   [elin.util.nrepl :as e.u.nrepl]
   [exoscale.interceptor :as ix]))

(def eval-ns
  "Interceptor to delete ns keyword from nREPL request on evaluating ns form."
  {:kind e.c.interceptor/nrepl
   :enter (-> (fn [{:as ctx :keys [request]}]
                (let [{:keys [code]} request]
                  (if (str/starts-with? code "(ns")
                    (update ctx :request dissoc :ns)
                    ctx)))
              (ix/when #(= e.c.nrepl/eval-op (get-in % [:request :op]))))})

(def normalize-path
  "Interceptor to normalize path on nREPL response."
  {:kind e.c.interceptor/nrepl
   :leave (fn [{:as ctx :keys [request response]}]
            (cond
              (contains? #{e.c.nrepl/lookup-op e.c.nrepl/info-op} (:op request))
              (->> response
                   (e.u.nrepl/update-messages :file e.u.file/normalize-path)
                   (assoc ctx :response))

              (contains? #{e.c.nrepl/ns-path-op} (:op request))
              (->> response
                   (e.u.nrepl/update-messages :url e.u.file/normalize-path)
                   (e.u.nrepl/update-messages :path e.u.file/normalize-path)
                   (assoc ctx :response))

              :else
              ctx))})

(def output-result-to-cmdline
  "Interceptor to output nREPL result as message."
  {:kind e.c.interceptor/nrepl
   :leave (-> (fn [{:component/keys [host] :keys [request response]}]
                (let [msg (e.u.nrepl/merge-messages response)
                      text (condp = (:op request)
                             e.c.nrepl/interrupt-op "Interrupted."
                             e.c.nrepl/load-file-op "Required."
                             e.c.nrepl/reload-op "Reloaded."
                             e.c.nrepl/reload-all-op "Reloaded all."
                             e.c.nrepl/undef-op (if-let [sym (:undef msg)]
                                                  (str "Undefined '" sym "'.")
                                                  "Undefined.")
                             e.c.nrepl/undef-all-op "Undefined all."
                             "Processed.")]
                  (if (e.u.nrepl/has-status? msg "eval-error")
                    (when-let [v (:err msg)]
                      (e.message/error host (str/trim (str v))))
                    (e.message/info host text))))
              (ix/discard))})

(def progress
  "Interceptor to show progress popup on nREPL request."
  (let [target-ops #{e.c.nrepl/eval-op
                     e.c.nrepl/load-file-op
                     e.c.nrepl/test-var-query-op
                     e.c.nrepl/reload-op
                     e.c.nrepl/reload-all-op}
        channel-store (atom {})]
    {:kind e.c.interceptor/nrepl
     :enter (-> (fn [{:as ctx :component/keys [host] :keys [request]}]
                  (let [timeout-ch (async/timeout 300)
                        result-ch (async/promise-chan)
                        ctx (if (contains? (:request ctx) :id)
                              ctx
                              (assoc-in ctx [:request :id] (e.u.id/next-id)))
                        id (get-in ctx [:request :id])]
                    (swap! channel-store assoc id {:result-ch result-ch
                                                   :timeouted false})

                    (async/go
                      (async/<! timeout-ch))
                    (async/go
                      (let [[_ ch] (async/alts! [timeout-ch result-ch])]
                        (if (= timeout-ch ch)
                          (let [text (condp = (:op request)
                                       e.c.nrepl/eval-op "Evaluating..."
                                       e.c.nrepl/load-file-op "Loading..."
                                       e.c.nrepl/test-var-query-op "Testing..."
                                       e.c.nrepl/reload-op "Reloading..."
                                       e.c.nrepl/reload-all-op "Reloading all..."
                                       "Processing...")
                                popup-id (async/<!
                                          (e.p.host/open-popup!
                                           host
                                           text
                                           {:line "bottom"
                                            :col "right"
                                            :border []
                                            :filetype "help"}))]
                            (swap! channel-store update id #(assoc %
                                                                   :timeouted true
                                                                   :popup-id popup-id)))
                          (async/close! timeout-ch))))

                    ctx))
                (ix/when #(contains? target-ops (get-in % [:request :op]))))
     :leave (-> (fn [{:component/keys [host] :keys [response]}]
                  (when-let [id (:id (e.u.nrepl/merge-messages response))]
                    (let [{:keys [result-ch timeouted popup-id]} (get @channel-store id)]
                      (async/put! result-ch true)
                      (when timeouted
                        (e.p.host/close-popup host popup-id)))
                    (swap! channel-store dissoc id)))
                (ix/when #(contains? target-ops (get-in % [:request :op])))
                (ix/discard))}))

(def nrepl-output
  "Interceptor to intercept nREPL output.
  This interceptor executes interceptors with e.c.interceptor/output kind."
  {:kind e.c.interceptor/raw-nrepl
   :leave (-> (fn [{:as ctx :component/keys [interceptor] :keys [message]}]
                (let [output (cond
                               (string? (:out message))
                               {:type "out" :text (:out message)}

                               (string? (:pprint-out message))
                               {:type "pprint-out" :text (:pprint-out message)}

                               (string? (:err message))
                               {:type "err" :text (:err message)}

                               :else nil)]
                  (when output
                    (-> ctx
                        (e.u.map/select-keys-by-namespace :component)
                        (assoc :output output)
                        (->> (e.p.interceptor/execute interceptor e.c.interceptor/output))))))
              (ix/when #(:message %))
              (ix/discard))})
