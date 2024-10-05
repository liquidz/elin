(ns elin.interceptor.output
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.protocol.host :as e.p.host]
   [elin.util.interceptor :as e.u.interceptor]
   [elin.util.string :as e.u.string]
   [exoscale.interceptor :as ix]))

(def print-output-interceptor
  "Interceptor to print output on nREPL to InfoBuffer.

  Output format can be configured like below:
  ```
  {:interceptor {:config-map {elin.interceptor.output/print-output-interceptor
                              {:format \"{{text}}\"}}}}
  ```

  Available variables:
  - type: Output type
  - text: Output text"
  {:kind e.c.interceptor/output
   :enter (-> (fn [{:as ctx :component/keys [host] :keys [output]}]
                (let [config (e.u.interceptor/config ctx #'print-output-interceptor)
                      format-str (or (:format config)
                                     "{{text}}")]
                  (->> (e.u.string/render format-str output)
                       (e.p.host/append-to-info-buffer host))))
              (ix/discard))})
