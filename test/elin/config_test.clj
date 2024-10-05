(ns elin.config-test
  (:require
   [clojure.test :as t]
   [elin.config :as sut]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)

(t/deftest merge-configs-test
  (t/is (= {}
           (sut/merge-configs {} {})))
  (t/is (= {:a 1 :b 2}
           (sut/merge-configs {:a 1} {:b 2})))
  (t/is (= {:a 2}
           (sut/merge-configs {:a 1} {:a 2})))
  (t/is (= {:a {:b 1 :c 2}}
           (sut/merge-configs {:a {:b 1}}
                              {:a {:c 2}})))
  (t/is (= {:a {:b 2}}
           (sut/merge-configs {:a {:b 1}}
                              {:a {:b 2}})))
  (t/is (= {:a {:b [2]}}
           (sut/merge-configs {:a {:b [1]}}
                              {:a {:b [2]}})))

  (t/testing "nil"
    (t/is (= {:a 1}
             (sut/merge-configs {:a 1} nil)))
    (t/is (= {:b 2}
             (sut/merge-configs nil {:b 2}))))

  (t/testing "includes"
    (t/is (= {:a {:includes [1 2]}}
             (sut/merge-configs {:a {:includes [1]}}
                                {:a {:includes [2]}}))))
  (t/testing "excludes"
    (t/is (= {:a {:excludes [1 2]}}
             (sut/merge-configs {:a {:excludes [1]}}
                                {:a {:excludes [2]}}))))

  (t/testing "uses"
    (t/is (= {:a {:uses [1 2]}}
             (sut/merge-configs {:a {:uses [1]}}
                                {:a {:uses [2]}}))))

  (t/testing "set"
    (t/is (= {:a {:set #{1 2}}}
             (sut/merge-configs {:a {:set #{1}}}
                                {:a {:set #{2}}}))))

  (t/testing "three or more"
    (t/is (= {:a {:b 1 :c 2 :d 3}}
             (sut/merge-configs {:a {:b 1}}
                                {:a {:c 2}}
                                {:a {:d 3}})))
    (t/is (= {:a {:b 3}}
             (sut/merge-configs {:a {:b 1}}
                                {:a {:b 2}}
                                {:a {:b 3}})))
    (t/is (= {:a {:includes [1 2 3]}}
             (sut/merge-configs {:a {:includes [1]}}
                                {:a {:includes [2]}}
                                {:a {:includes [3]}})))
    (t/is (= {:a {:excludes [1 2 3]}}
             (sut/merge-configs {:a {:excludes [1]}}
                                {:a {:excludes [2]}}
                                {:a {:excludes [3]}})))))

(defn- find-included-handler
  [target-config handler-symbol]
  (let [res (->> (get-in target-config [:handler :includes])
                 (filter #(= handler-symbol %)))]
    (cond
      (empty? res) nil
      (= 1 (count res)) (first res)
      :else (throw (ex-info "handler should be only once" {})))))

(defn- find-included-interceptor
  [target-config interceptor-symbol]
  (let [res (->> (get-in target-config [:interceptor :includes])
                 (filter #(= interceptor-symbol %)))]
    (cond
      (empty? res) nil
      (= 1 (count res)) {:included (first res)
                         :config (get-in target-config [:interceptor :config-map interceptor-symbol])}
      :else (throw (ex-info "interceptor should be only once" {})))))

(t/deftest configure-test
  (t/is (= {:handler {:includes [] :excludes []}
            :interceptor {:includes [] :excludes []}}
           (sut/configure {} {})))

  (t/testing "handler"
    (t/testing "includes"
      (t/is (= {:handler {:includes ['foo] :excludes []}
                :interceptor {:includes [] :excludes []}}
               (sut/configure {}
                              {:handler {:includes ['foo]}})))

      (t/is (= {:handler {:includes ['foo] :excludes []}
                :interceptor {:includes [] :excludes []}}
               (sut/configure {:handler {:includes ['foo]}}
                              {:handler {:includes ['foo]}})))

      (t/is (= {:handler {:includes ['foo 'bar] :excludes []}
                :interceptor {:includes [] :excludes []}}
               (sut/configure {:handler {:includes ['foo]}}
                              {:handler {:includes ['bar]}}))))

    (t/testing "exclude"
      (t/is (= {:handler {:includes [] :excludes []}
                :interceptor {:includes [] :excludes []}}
               (sut/configure {:handler {:includes ['foo]}}
                              {:handler {:excludes ['foo]}}))))

    (t/testing "includes and excludes"
      (t/is (= {:handler {:includes ['foo] :excludes []}
                :interceptor {:includes [] :excludes []}}
               (sut/configure {}
                              {:handler {:includes ['foo] :excludes ['foo]}}))))

    (t/testing "config-map"
      (t/is (= {:handler {:includes [] :excludes [] :config-map {'foo {:a 1}}}
                :interceptor {:includes [] :excludes []}}
               (sut/configure {}
                              {:handler {:config-map {'foo {:a 1}}}})))

      (t/is (= {:handler {:includes [] :excludes [] :config-map {'foo {:a 1}}}
                :interceptor {:includes [] :excludes []}}
               (sut/configure {:handler {:config-map {'foo {:a 1}}}}
                              {:handler {:config-map {'foo {:a 1}}}})))

      (t/is (= {:handler {:includes [] :excludes [] :config-map {'foo {:a 10}}}
                :interceptor {:includes [] :excludes []}}
               (sut/configure {:handler {:config-map {'foo {:a 1}}}}
                              {:handler {:config-map {'foo {:a 10}}}})))

      (t/is (= {:handler {:includes [] :excludes [] :config-map {'foo {:a 1}
                                                                 'bar {:b 2}}}
                :interceptor {:includes [] :excludes []}}
               (sut/configure {:handler {:config-map {'foo {:a 1}}}}
                              {:handler {:config-map {'bar {:b 2}}}}))))

    (t/testing "uses"
      (t/is (= {:handler {:includes ['foo] :excludes [] :config-map {'foo {:a 1}}}
                :interceptor {:includes [] :excludes []}}
               (sut/configure {}
                              {:handler {:uses ['foo {:a 1}]}})))

      (t/is (= {:handler {:includes ['foo] :excludes [] :config-map {'foo {:a 1}}}
                :interceptor {:includes [] :excludes []}}
               (sut/configure {:handler {:includes ['foo] :config-map {'foo {:a 1}}}}
                              {:handler {:uses ['foo {:a 1}]}})))

      (t/is (= {:handler {:includes ['foo] :excludes [] :config-map {'foo {:a 10}}}
                :interceptor {:includes [] :excludes []}}
               (sut/configure {:handler {:includes ['foo] :config-map {'foo {:a 1}}}}
                              {:handler {:uses ['foo {:a 10}]}})))

      (t/is (= {:handler {:includes ['foo 'bar] :excludes [] :config-map {'foo {:a 1}
                                                                          'bar {:b 2}}}
                :interceptor {:includes [] :excludes []}}
               (sut/configure {:handler {:includes ['foo] :config-map {'foo {:a 1}}}}
                              {:handler {:uses ['bar {:b 2}]}})))))

  (t/testing "interceptor"
    (t/testing "includes"
      (t/is (= {:handler {:includes [] :excludes []}
                :interceptor {:includes ['foo] :excludes []}}
               (sut/configure {}
                              {:interceptor {:includes ['foo]}})))

      (t/is (= {:handler {:includes [] :excludes []}
                :interceptor {:includes ['foo] :excludes []}}
               (sut/configure {:interceptor {:includes ['foo]}}
                              {:interceptor {:includes ['foo]}})))

      (t/is (= {:handler {:includes [] :excludes []}
                :interceptor {:includes ['foo 'bar] :excludes []}}
               (sut/configure {:interceptor {:includes ['foo]}}
                              {:interceptor {:includes ['bar]}}))))

    (t/testing "excludes"
      (t/is (= {:handler {:includes [] :excludes []}
                :interceptor {:includes [] :excludes []}}
               (sut/configure {:interceptor {:includes ['foo]}}
                              {:interceptor {:excludes ['foo]}}))))

    (t/testing "includes and excludes"
      (t/is (= {:handler {:includes [] :excludes []}
                :interceptor {:includes ['foo] :excludes []}}
               (sut/configure {}
                              {:interceptor {:includes ['foo] :excludes ['foo]}}))))

    (t/testing "config-map"
      (t/is (= {:handler {:includes [] :excludes []}
                :interceptor {:includes [] :excludes [] :config-map {'foo {:a 1}}}}
               (sut/configure {}
                              {:interceptor {:config-map {'foo {:a 1}}}})))

      (t/is (= {:handler {:includes [] :excludes []}
                :interceptor {:includes [] :excludes [] :config-map {'foo {:a 1}}}}
               (sut/configure {:interceptor {:config-map {'foo {:a 1}}}}
                              {:interceptor {:config-map {'foo {:a 1}}}})))

      (t/is (= {:handler {:includes [] :excludes []}
                :interceptor {:includes [] :excludes [] :config-map {'foo {:a 10}}}}
               (sut/configure {:interceptor {:config-map {'foo {:a 1}}}}
                              {:interceptor {:config-map {'foo {:a 10}}}})))

      (t/is (= {:handler {:includes [] :excludes []}
                :interceptor {:includes [] :excludes [] :config-map {'foo {:a 1}
                                                                     'bar {:b 2}}}}
               (sut/configure {:interceptor {:config-map {'foo {:a 1}}}}
                              {:interceptor {:config-map {'bar {:b 2}}}}))))

    (t/testing "uses"
      (t/is (= {:handler {:includes [] :excludes []}
                :interceptor {:includes ['foo] :excludes [] :config-map {'foo {:a 1}}}}
               (sut/configure {}
                              {:interceptor {:uses ['foo {:a 1}]}})))

      (t/is (= {:handler {:includes [] :excludes []}
                :interceptor {:includes ['foo] :excludes [] :config-map {'foo {:a 1}}}}
               (sut/configure {:interceptor {:includes ['foo] :config-map {'foo {:a 1}}}}
                              {:interceptor {:uses ['foo {:a 1}]}})))

      (t/is (= {:handler {:includes [] :excludes []}
                :interceptor {:includes ['foo] :excludes [] :config-map {'foo {:a 10}}}}
               (sut/configure {:interceptor {:includes ['foo] :config-map {'foo {:a 1}}}}
                              {:interceptor {:uses ['foo {:a 10}]}})))

      (t/is (= {:handler {:includes [] :excludes []}
                :interceptor {:includes ['foo 'bar] :excludes [] :config-map {'foo {:a 1}
                                                                              'bar {:b 2}}}}
               (sut/configure {:interceptor {:includes ['foo] :config-map {'foo {:a 1}}}}
                              {:interceptor {:uses ['bar {:b 2}]}}))))))

(t/deftest load-config-test
  (with-redefs [sut/load-user-config (constantly {})
                sut/load-project-local-config (constantly {})]
    (let [server-config {:server {:host "" :port 0}
                         :env {:cwd "."}}
          test-load-config #(sut/load-config "." server-config)
          base-config (test-load-config)
          sample-handler (first (get-in base-config [:handler :includes]))
          sample-interceptor (first (get-in base-config [:interceptor :includes]))
          random-symbol (symbol (str (random-uuid))
                                (str (random-uuid)))]
      (t/is (some? base-config))
      (t/is (qualified-symbol? sample-handler))
      (t/is (qualified-symbol? sample-interceptor))

      (t/testing "user-config can override default-config"
        (with-redefs [sut/load-user-config (constantly {:env {:cwd "foo"}})]
          (t/is (= {:cwd "foo"}
                   (:env (test-load-config)))))

        (t/testing "handler"
          (with-redefs [sut/load-user-config (constantly {:handler {:includes [sample-handler]}})]
            (t/is (= sample-handler
                     (find-included-handler (test-load-config) sample-handler))))))

      (t/testing "project-local-config can override default-config"
        (with-redefs [sut/load-project-local-config (constantly {:env {:cwd "bar"}})]
          (t/is (= {:cwd "bar"}
                   (:env (test-load-config)))))

        (t/testing "handler"
          (with-redefs [sut/load-project-local-config (constantly {:handler {:includes [sample-handler]}})]
            (t/is (= sample-handler
                     (find-included-handler (test-load-config) sample-handler))))))

      (t/testing "project-local-config can override user-config"
        (with-redefs [sut/load-user-config (constantly {:env {:cwd "foo"}})
                      sut/load-project-local-config (constantly {:env {:cwd "bar"}})]
          (t/is (= {:cwd "bar"}
                   (:env (test-load-config)))))

        (t/testing "handler"
          (with-redefs [sut/load-user-config (constantly {:handler {:includes [random-symbol]}})
                        sut/load-project-local-config (constantly {:handler {:includes [random-symbol]}})]
            (t/is (= random-symbol
                     (find-included-handler (test-load-config) random-symbol))))))

      (t/testing "user-config should exclude handlers/interceptors in default-config"
        (t/testing "handler"
          (with-redefs [sut/load-user-config (constantly {:handler {:excludes [sample-handler]}})]
            (t/is (nil? (find-included-handler (test-load-config) sample-handler)))))

        (t/testing "interceptor"
          (with-redefs [sut/load-user-config (constantly {:interceptor {:excludes [sample-interceptor]}})]
            (t/is (nil? (find-included-interceptor (test-load-config) sample-interceptor))))))


      (t/testing "project-local-config should exclude handlers/interceptors in default-config"
        (t/testing "handler"
          (with-redefs [sut/load-project-local-config (constantly {:handler {:excludes [sample-handler]}})]
            (t/is (nil? (find-included-handler (test-load-config) sample-handler)))))

        (t/testing "interceptor"
          (with-redefs [sut/load-project-local-config (constantly {:interceptor {:excludes [sample-interceptor]}})]
            (t/is (nil? (find-included-interceptor (test-load-config) sample-interceptor))))))

      (t/testing "project-local-config should exclude handlers/interceptors in user-config"
        (t/testing "handler"
          (with-redefs [sut/load-user-config (constantly {:handler {:includes [random-symbol]}})
                        sut/load-project-local-config (constantly {:handler {:excludes [random-symbol]}})]
            (t/is (nil? (find-included-handler (test-load-config) random-symbol)))))

        (t/testing "interceptor"
          (with-redefs [sut/load-user-config (constantly {:interceptor {:includes [random-symbol]}})
                        sut/load-project-local-config (constantly {:interceptor {:excludes [random-symbol]}})]
            (t/is (nil? (find-included-interceptor (test-load-config) random-symbol)))))))))
