(ns elin.component.interceptor-test
  (:require
   [clojure.test :as t]
   [com.stuartsierra.component :as component]
   [elin.component.interceptor :as sut]
   [elin.protocol.config :as e.p.config]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.system :as e.system]
   [elin.test-helper :as h]
   [malli.core :as m]))

(t/use-fixtures :once h/malli-instrument-fixture)
(t/use-fixtures :once h/warn-log-level-fixture)

(def ^:private test-interceptor
  {:name 'elin.component.interceptor-test/test-interceptor
   :kind ::test
   :enter (fn [ctx]
            (update ctx :x inc))})

(def ^:private test-interceptor2
  {:name 'elin.component.interceptor-test/test-interceptor2
   :kind ::test
   :enter (fn [ctx]
            (update ctx :x (partial * 2)))})

(def ^:private test-optional-interceptor
  {:name 'elin.component.interceptor-test/test-optional-interceptor
   :kind ::test
   :optional true
   :enter (fn [ctx]
            (update ctx :x (partial + 100)))})

(t/deftest new-interceptor-test
  (let [config {:interceptor {:includes [(symbol #'test-interceptor)
                                         (symbol #'test-optional-interceptor)]}}
        {:as sys :keys [interceptor]} (with-redefs [m/validate (constantly true)]
                                        (-> (e.system/new-system config)
                                            (dissoc :nrepl :clj-kondo :handler :http-server :server)
                                            (component/start-system)))]
    (try
      (t/is (= 2
               (:x (e.p.interceptor/execute interceptor ::test {:x 1})))
            "optional interceptor should not be executed at start time")

      (t/is (= 4
               (:x (e.p.interceptor/execute interceptor ::test {:x 1} #(update % :x (partial * 2))))))

      (t/testing "configure"
        (t/testing "includes"
          (let [interceptor' (with-redefs [m/validate (constantly true)]
                               (e.p.config/configure interceptor
                                                     {:interceptor {:includes [(symbol #'test-interceptor2)]}}))]
            (t/is (= {:x 8}
                     (-> interceptor'
                         (e.p.interceptor/execute ::test {:x 1} #(update % :x (partial * 2)))
                         (select-keys [:x])))))

          (t/testing "optional"
            (let [interceptor' (with-redefs [m/validate (constantly true)]
                                 (e.p.config/configure interceptor
                                                       {:interceptor {:includes [(symbol #'test-optional-interceptor)]}}))]
              (t/is (= {:x 204}
                       (-> interceptor'
                           (e.p.interceptor/execute ::test {:x 1} #(update % :x (partial * 2)))
                           (select-keys [:x])))
                    "optional interceptor should be executed at configure time"))))

        (t/testing "excludes"
          (let [interceptor' (with-redefs [m/validate (constantly true)]
                               (e.p.config/configure interceptor
                                                     {:interceptor {:excludes [(symbol #'test-interceptor)]}}))]
            (t/is (= {:x 2}
                     (-> interceptor'
                         (e.p.interceptor/execute ::test {:x 1} #(update % :x (partial * 2)))
                         (select-keys [:x])))))))

      (finally
        (component/stop-system sys)))))

(t/deftest new-interceptor-includes-excludes-test
  (with-redefs [m/validate (constantly true)
                sut/wrap-interceptor-for-logging identity]
    (t/testing "only includes"
      (t/is (= {::test [test-interceptor]}
               (-> {:base-config {:includes [(symbol #'test-interceptor)]}}
                   (sut/map->Interceptor)
                   (component/start)
                   (:interceptor-map)))))

    (t/testing "only excludes"
      (t/is (empty? (-> {:excludes [(symbol #'test-interceptor)]}
                        (sut/map->Interceptor)
                        (component/start)
                        (:interceptor-map)))))

    (t/testing "includes and excludes"
      (t/is (= {::test [test-interceptor]}
               (-> {:base-config {:includes [(symbol #'test-interceptor)]
                                  :excludes [(symbol #'test-interceptor)]}}
                   (sut/map->Interceptor)
                   (component/start)
                   (:interceptor-map)))
            "includes should be prioritized over excludes"))))

(t/deftest new-interceptor-configure-includes-excludes-test
  (with-redefs [m/validate (constantly true)
                sut/wrap-interceptor-for-logging identity]
    (let [interceptor (-> {:base-config {:includes [(symbol #'test-interceptor)]}}
                          (sut/map->Interceptor)
                          (component/start))]
      (t/testing "only includes"
        (t/is (= {::test [test-interceptor test-interceptor2]}
                 (-> interceptor
                     (e.p.config/configure {:interceptor {:includes [(symbol #'test-interceptor2)]}})
                     (:interceptor-map))))

        (t/testing "optional"
          (t/is (= {::test [test-interceptor test-optional-interceptor]}
                   (-> interceptor
                       (e.p.config/configure {:interceptor {:includes [(symbol #'test-optional-interceptor)]}})
                       (:interceptor-map))))))

      (t/testing "only excludes"
        (t/is (= {::test [test-interceptor]}
                 (-> interceptor
                     (e.p.config/configure {:interceptor {:excludes [(symbol #'test-interceptor2)]}})
                     (:interceptor-map))))

        (t/is (= {::test []}
                 (-> interceptor
                     (e.p.config/configure {:interceptor {:excludes [(symbol #'test-interceptor)]}})
                     (:interceptor-map)))))

      (t/testing "includes and excludes"
        (t/is (= {::test [test-interceptor test-interceptor2]}
                 (-> interceptor
                     (e.p.config/configure {:interceptor {:includes [(symbol #'test-interceptor2)]
                                                          :excludes [(symbol #'test-interceptor2)]}})
                     (:interceptor-map))))

        (t/is (= {::test [test-interceptor2]}
                 (-> interceptor
                     (e.p.config/configure {:interceptor {:includes [(symbol #'test-interceptor2)]
                                                          :excludes [(symbol #'test-interceptor)]}})
                     (:interceptor-map))))

        (t/is (= {::test [test-interceptor2 test-interceptor]}
                 (-> interceptor
                     (e.p.config/configure {:interceptor {:includes [(symbol #'test-interceptor2)
                                                                     (symbol #'test-interceptor)]
                                                          :excludes [(symbol #'test-interceptor)]}})
                     (:interceptor-map))))))))
