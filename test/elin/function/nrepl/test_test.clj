(ns elin.function.nrepl.test-test
  (:require
   [clojure.test :as t]
   [com.stuartsierra.component :as component]
   [elin.function.nrepl :as e.f.nrepl]
   [elin.function.nrepl.test :as sut]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.system :as e.system]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)
(t/use-fixtures :each h/test-nrepl-server-port-fixture)
(t/use-fixtures :once h/warn-log-level-fixture)

(t/deftest test-var-query!!-test
  (let [{:as sys :keys [nrepl]} (-> (e.system/new-system)
                                    (dissoc :handler :http-server :server)
                                    (component/start-system))]
    (try
      (let [client (e.p.nrepl/add-client! nrepl "localhost" h/*nrepl-server-port*)]
        (e.p.nrepl/switch-client! nrepl client))

      (e.f.nrepl/eval!! nrepl (str '(do (require '[clojure.test :as t])
                                        (t/deftest pass-test (t/is (= 1 1)))
                                        (t/deftest fail-test (t/is (= 1 2)))
                                        (t/deftest error-test (t/is (/ 2 0)))))
                        {:ns "user"})

      (t/testing "success-test"
        (t/is (= {:summary {:test 1 :pass 1 :fail 0 :error 0 :var 1}
                  :testing-ns "user"
                  :results {"user" {"pass-test" [{:type "pass"
                                                  :message nil
                                                  :ns "user"
                                                  :var "pass-test"
                                                  :file "dummy.clj"
                                                  :line nil}]}}}
                 (sut/test-var-query!! nrepl {:ns "user"
                                              :vars (->> ['user/pass-test]
                                                         (mapv resolve))
                                              :base-line 0
                                              :current-file "dummy.clj"}))))

      (t/testing "fail-test"
        (let [resp (-> (sut/test-var-query!! nrepl {:ns "user"
                                                    :vars (->> ['user/fail-test]
                                                               (mapv resolve))
                                                    :base-line 0
                                                    :current-file "dummy.clj"})
                       (update-in [:results "user" "fail-test"] vec))]
          (t/is (= {:summary {:test 1 :pass 0 :fail 1 :error 0 :var 1}
                    :testing-ns "user"
                    :results {"user" {"fail-test" [{:type "fail"
                                                    :message nil
                                                    :ns "user"
                                                    :var "fail-test"
                                                    :line 0
                                                    :expected "(= 1 2)"
                                                    :actual "(not (= 1 2))"}]}}}
                   (update-in resp [:results "user" "fail-test" 0] dissoc :file)))

          (let [file (get-in resp [:results "user" "fail-test" 0 :file])]
            (t/is (string? file))
            (t/is (seq file)))))

      (t/testing "error-test"
        (let [resp (-> (sut/test-var-query!! nrepl {:ns "user"
                                                    :vars (->> ['user/error-test]
                                                               (mapv resolve))
                                                    :base-line 0
                                                    :current-file "dummy.clj"})
                       (update-in [:results "user" "error-test"] vec))]
          (t/is (= {:summary {:test 1 :pass 0 :fail 0 :error 1 :var 1}
                    :testing-ns "user"
                    :results {"user" {"error-test" [{:type "error"
                                                     :message nil
                                                     :ns "user"
                                                     :var "error-test"
                                                     :line 0
                                                     :expected "(/ 2 0)"
                                                     :actual "class java.lang.ArithmeticException: Divide by zero"}]}}}
                   (update-in resp [:results "user" "error-test" 0] dissoc :file)))

          (let [file (get-in resp [:results "user" "error-test" 0 :file])]
            (t/is (string? file))
            (t/is (seq file)))))

      (t/testing "all tests"
        (let [resp (-> (sut/test-var-query!! nrepl {:ns "user"
                                                    :vars (->> ['user/pass-test
                                                                'user/fail-test
                                                                'user/error-test]
                                                               (mapv resolve))
                                                    :base-line 0
                                                    :current-file "dummy.clj"})
                       (update-in [:results "user" "fail-test"] vec)
                       (update-in [:results "user" "error-test"] vec))]
          (t/is (= {:summary {:test 3 :pass 1 :fail 1 :error 1 :var 3}
                    :testing-ns "user"
                    :results {"user" {"pass-test" [{:type "pass"
                                                    :message nil
                                                    :ns "user"
                                                    :var "pass-test"
                                                    :file "dummy.clj"
                                                    :line nil}]
                                      "fail-test" [{:type "fail"
                                                    :line 0
                                                    :expected "(= 1 2)"
                                                    :actual "(not (= 1 2))"
                                                    :message nil
                                                    :ns "user"
                                                    :var "fail-test"}]
                                      "error-test" [{:type "error"
                                                     :line 0
                                                     :expected "(/ 2 0)"
                                                     :actual "class java.lang.ArithmeticException: Divide by zero"
                                                     :message nil
                                                     :ns "user"
                                                     :var "error-test"}]}}}
                   (-> resp
                       (update-in [:results "user" "fail-test" 0] dissoc :file)
                       (update-in [:results "user" "error-test" 0] dissoc :file))))))

      (finally
        (component/stop-system sys)))))
