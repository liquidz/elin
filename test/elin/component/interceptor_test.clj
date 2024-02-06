(ns elin.component.interceptor-test
  (:require
   [clojure.test :as t]
   [com.stuartsierra.component :as component]
   [elin.component.interceptor :as sut]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.system :as e.system]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)

(def ^:private test-interceptor
  {:name ::test-interceptor
   :kind ::test
   :enter (fn [ctx]
            (update ctx :x inc))})

(t/deftest new-interceptor-test
  (with-redefs [sut/valid-interceptor? (constantly true)]
    (let [config {:interceptor {:includes [(symbol #'test-interceptor)]}}
          {:as sys :keys [interceptor]} (-> (e.system/new-system config)
                                            (select-keys [:lazy-writer :plugin :interceptor])
                                            (component/start-system))]
      (try
        (t/is (= 2
                 (:x (e.p.interceptor/execute interceptor ::test {:x 1}))))

        (t/is (= 4
                 (:x (e.p.interceptor/execute interceptor ::test {:x 1} #(update % :x (partial * 2))))))

        (finally
          (component/stop-system sys))))))
