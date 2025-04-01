(ns elin.core-test
  (:require
   [cheshire.core :as json]
   [clojure.test :as t]
   [com.stuartsierra.component :as component]
   [elin.config :as e.config]
   [elin.core :as sut]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)
(t/use-fixtures :once h/warn-log-level-fixture)

(t/deftest -main-test
  (let [used-config (atom nil)]
    (with-redefs [component/start-system (fn [sys-map]
                                           (reset! used-config sys-map))
                  e.config/load-default-config (constantly {})
                  e.config/load-user-config (constantly {})
                  e.config/load-project-local-config (constantly {})
                  promise (constantly (atom nil))]
      (t/testing "No custom config"
        (do (reset! used-config nil)
            (sut/-main (json/encode {:env {:cwd "dummy"}
                                     :server {:host "vim" :port 123}})))
        (t/is (= {:host "vim" :port 123}
                 (-> (:server @used-config)
                     (select-keys [:host :port]))))
        (t/is (= {:includes [] :excludes []}
                 (get-in @used-config [:handler :base-config])))
        (t/is (= {:includes [] :excludes []}
                 (get-in @used-config [:interceptor :base-config]))))

      (t/testing "Interceptor config-map"
        (do (reset! used-config nil)
            (sut/-main (json/encode {:env {:cwd "dummy"}
                                     :server {:host "vim" :port 123}
                                     :interceptor '{:config-map {elin.interceptor/dummy {:dummy value}}}})))
        (t/is (= {:includes [] :excludes []
                  :config-map '{elin.interceptor/dummy {:dummy "value"}}}
                 (get-in @used-config [:interceptor :base-config]))))

      (t/testing "Handler config-map"
        (do (reset! used-config nil)
            (sut/-main (json/encode {:env {:cwd "dummy"}
                                     :server {:host "vim" :port 123}
                                     :handler '{:config-map {elin.handler/dummy {:dummy value
                                                                                 :interceptor {:uses [elin.interceptor/dummy {:key value}]}}}}})))
        (t/is (= {:includes [] :excludes []
                  :config-map '{elin.handler/dummy {:dummy "value"
                                                    :interceptor {:uses [elin.interceptor/dummy {:key "value"}]}}}}
                 (get-in @used-config [:handler :base-config])))))))
