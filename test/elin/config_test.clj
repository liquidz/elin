(ns elin.config-test
  (:require
   [clojure.test :as t]
   [elin.config :as sut]
   [elin.test-helper :as h]
   [elin.util.interceptor :as e.u.interceptor]))

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
                 (filter #(= interceptor-symbol (:symbol (e.u.interceptor/parse %)))))]
    (cond
      (empty? res) nil
      (= 1 (count res)) (first res)
      :else (throw (ex-info "interceptor should be only once" {})))))

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
                     (find-included-handler (test-load-config) sample-handler)))))

        (t/testing "interceptor"
          (with-redefs [sut/load-user-config (constantly {:interceptor {:includes [[sample-interceptor "param"]]}})]
            (t/is (= [sample-interceptor "param"]
                     (find-included-interceptor (test-load-config) sample-interceptor))))))


      (t/testing "project-local-config can override default-config"
        (with-redefs [sut/load-project-local-config (constantly {:env {:cwd "bar"}})]
          (t/is (= {:cwd "bar"}
                   (:env (test-load-config)))))

        (t/testing "handler"
          (with-redefs [sut/load-project-local-config (constantly {:handler {:includes [sample-handler]}})]
            (t/is (= sample-handler
                     (find-included-handler (test-load-config) sample-handler)))))

        (t/testing "interceptor"
          (with-redefs [sut/load-project-local-config (constantly {:interceptor {:includes [[sample-interceptor "param"]]}})]
            (t/is (= [sample-interceptor "param"]
                     (find-included-interceptor (test-load-config) sample-interceptor))))))


      (t/testing "project-local-config can override user-config"
        (with-redefs [sut/load-user-config (constantly {:env {:cwd "foo"}})
                      sut/load-project-local-config (constantly {:env {:cwd "bar"}})]
          (t/is (= {:cwd "bar"}
                   (:env (test-load-config)))))

        (t/testing "handler"
          (with-redefs [sut/load-user-config (constantly {:handler {:includes [random-symbol]}})
                        sut/load-project-local-config (constantly {:handler {:includes [random-symbol]}})]
            (t/is (= random-symbol
                     (find-included-handler (test-load-config) random-symbol)))))

        (t/testing "interceptor"
          (with-redefs [sut/load-user-config (constantly {:interceptor {:includes [random-symbol]}})
                        sut/load-project-local-config (constantly {:interceptor {:includes [[random-symbol "param"]]}})]
            (t/is (= [random-symbol "param"]
                     (find-included-interceptor (test-load-config) random-symbol))))))


      (t/testing "user-config should exclude handlers/interceptors in default-config"
        (t/testing "handler"
          (with-redefs [sut/load-user-config (constantly {:handler {:excludes [sample-handler]}})]
            (t/is (nil? (find-included-handler (test-load-config) sample-handler)))))

        (t/testing "interceptor"
          (t/testing "no parameter"
            (with-redefs [sut/load-user-config (constantly {:interceptor {:excludes [sample-interceptor]}})]
              (t/is (nil? (find-included-interceptor (test-load-config) sample-interceptor)))))


          (t/testing "parameter"
            (with-redefs [sut/load-user-config (constantly {:interceptor {:excludes [[sample-interceptor "param"]]}})]
              (t/is (nil? (find-included-interceptor (test-load-config) sample-interceptor)))))))


      (t/testing "project-local-config should exclude handlers/interceptors in default-config"
        (t/testing "handler"
          (with-redefs [sut/load-project-local-config (constantly {:handler {:excludes [sample-handler]}})]
            (t/is (nil? (find-included-handler (test-load-config) sample-handler)))))

        (t/testing "interceptor"
          (t/testing "no parameter"
            (with-redefs [sut/load-project-local-config (constantly {:interceptor {:excludes [sample-interceptor]}})]
              (t/is (nil? (find-included-interceptor (test-load-config) sample-interceptor)))))

          (t/testing "parameter"
            (with-redefs [sut/load-project-local-config (constantly {:interceptor {:excludes [[sample-interceptor "param"]]}})]
              (t/is (nil? (find-included-interceptor (test-load-config) sample-interceptor)))))))


      (t/testing "project-local-config should exclude handlers/interceptors in user-config"
        (t/testing "handler"
          (with-redefs [sut/load-user-config (constantly {:handler {:includes [random-symbol]}})
                        sut/load-project-local-config (constantly {:handler {:excludes [random-symbol]}})]
            (t/is (nil? (find-included-handler (test-load-config) random-symbol)))))

        (t/testing "interceptor"
          (t/testing "no parameter"
            (with-redefs [sut/load-user-config (constantly {:interceptor {:includes [random-symbol]}})
                          sut/load-project-local-config (constantly {:interceptor {:excludes [random-symbol]}})]
              (t/is (nil? (find-included-interceptor (test-load-config) random-symbol)))))

          (t/testing "parameter"
            (with-redefs [sut/load-user-config (constantly {:interceptor {:includes [[random-symbol "param"]]}})
                          sut/load-project-local-config (constantly {:interceptor {:excludes [random-symbol]}})]
              (t/is (nil? (find-included-interceptor (test-load-config) random-symbol))))))))))
