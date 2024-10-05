(ns elin.interceptor.log
  (:require
   [clojure.core.async :as async]
   [clojure.string :as str]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.function.nrepl.cider :as e.f.n.cider]
   [elin.protocol.host :as e.p.host]
   [elin.util.interceptor :as e.u.interceptor]
   [elin.util.nrepl :as e.u.nrepl]
   [elin.util.string :as e.u.string]
   [exoscale.interceptor :as ix]))

(def ^:private appender-name
  (str ::appender))

(def ^:private last-framework
  (atom nil))

(def setting-log-appender-interceptor
  "Interceptor to set log appender on nREPL."
  {:kind e.c.interceptor/evaluate
   :leave (-> (fn [{:as ctx :component/keys [nrepl]}]
                (let [{:keys [framework]} (e.u.interceptor/config ctx #'setting-log-appender-interceptor)]
                  (when (seq framework)
                    (reset! last-framework framework)
                    (async/go
                      (let [frameworks (-> (e.f.n.cider/log-frameworks nrepl)
                                           (async/<!)
                                           (e.u.nrepl/merge-messages)
                                           (:cider/log-frameworks))
                            target-framework (some #(and (= framework (:id %)) %) frameworks)
                            target-appender (some #(= appender-name (:id %)) (:appenders target-framework))]
                        (when target-appender
                          (async/<! (e.f.n.cider/log-remove-appender! nrepl {:framework framework
                                                                             :appender appender-name}))))
                      (e.f.n.cider/log-add-appender nrepl {:framework framework
                                                           :appender appender-name
                                                           :filters {}
                                                           :size 100
                                                           :threshold 100})))))
              (ix/discard))})

(def append-logs-to-info-buffer-interceptor
  "Interceptor to append logs to InfoBuffer.

  Output format can be configured like below:
  ```
  {:interceptor {:config-map {elin.interceptor.log/append-logs-to-info-buffer-interceptor
                              {:format \"{{message}}\"}}}}
  ```

  Available variables:
  - level: Log level
  - timestamp: Log timestamp
  - thread: Log thread
  - logger: Log logger
  - message: Log message"
  (let [last-stop-signal (atom nil)]
    {:kind e.c.interceptor/connect
     :leave (-> (fn [{:as ctx :component/keys [host nrepl]}]
                  (let [config (e.u.interceptor/config ctx #'append-logs-to-info-buffer-interceptor)
                        format-str (or (:format config)
                                       "{{level}} [{{timestamp}}] {{thread}} - {{logger}} {{message}}")]
                    (when-let [ch @last-stop-signal]
                      (async/close! ch))
                    (let [stop-signal (async/chan)]
                      (reset! last-stop-signal stop-signal)
                      (async/go-loop [last-log-id nil]
                        (let [[_ ch] (async/alts! [stop-signal
                                                   (async/timeout 1000)])
                              logs (when @last-framework
                                     (when-let [logs (some->> (e.f.n.cider/log-search! nrepl {:framework @last-framework
                                                                                              :appender appender-name})
                                                              (async/<!)
                                                              (e.u.nrepl/merge-messages)
                                                              (:cider/log-search)
                                                              (take-while #(not= last-log-id (:id %)))
                                                              (seq))]
                                       (->> (reverse logs)
                                            (map #(e.u.string/render format-str %))
                                            (str/join "\n")
                                            (e.p.host/append-to-info-buffer host))
                                       logs))]
                          (when (not= ch stop-signal)
                            (recur (or (:id (first logs))
                                       last-log-id))))))))
                (ix/discard))}))
