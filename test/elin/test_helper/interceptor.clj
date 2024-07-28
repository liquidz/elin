(ns elin.test-helper.interceptor
  (:require
   [elin.component.interceptor :as e.c.interceptor]
   [elin.test-helper.host :as h.host]))

(defn test-interceptor
  [option]
  (let [host (h.host/test-host (merge {:handler identity}
                                      (or (:lazy-host option) {})))]
    (e.c.interceptor/new-interceptor
     {:interceptor (merge {:lazy-host host
                           :interceptor-map {}}
                          (or (:interceptor option) {}))})))
