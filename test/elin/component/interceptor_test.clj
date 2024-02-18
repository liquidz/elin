(ns elin.component.interceptor-test
  (:require
   [clojure.test :as t]
   [com.stuartsierra.component :as component]
   [elin.component.interceptor :as sut]
   [elin.protocol.config :as e.p.config]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.system :as e.system]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)

(def ^:private test-interceptor
  {:name ::test-interceptor
   :kind ::test
   :enter (fn [ctx]
            (update ctx :x inc))})

(def ^:private test-interceptor2
  {:name ::test-interceptor2
   :kind ::test
   :enter (fn [ctx]
            (update ctx :x (partial * 2)))})

(t/deftest new-interceptor-test
  (with-redefs [sut/valid-interceptor? (constantly true)]
    (let [config {:interceptor {:includes [(symbol #'test-interceptor)]}}
          {:as sys :keys [interceptor]} (-> (e.system/new-system config)
                                            (dissoc :nrepl :clj-kondo :handler :http-server :server)
                                            (component/start-system))]
      (try
        (t/is (= 2
                 (:x (e.p.interceptor/execute interceptor ::test {:x 1}))))

        (t/is (= 4
                 (:x (e.p.interceptor/execute interceptor ::test {:x 1} #(update % :x (partial * 2))))))

        (t/testing "configure"
          (t/testing "includes"
            (t/is (= {:x 8}
                     (-> interceptor
                         (e.p.config/configure {:interceptor {:includes [(symbol #'test-interceptor2)]}})
                         (e.p.interceptor/execute ::test {:x 1} #(update % :x (partial * 2)))
                         (select-keys [:x])))))

          (t/testing "excludes"
            (t/is (= {:x 2}
                     (-> interceptor
                         (e.p.config/configure {:interceptor {:excludes [(symbol #'test-interceptor)]}})
                         (e.p.interceptor/execute ::test {:x 1} #(update % :x (partial * 2)))
                         (select-keys [:x]))))))

        (finally
          (component/stop-system sys))))))
