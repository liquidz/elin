(ns elin.interceptor.test-test
  (:require
   [clojure.test :as t]
   [elin.function.nrepl.cider.test :as e.f.n.c.test]
   [elin.function.quickfix :as e.f.quickfix]
   [elin.function.storage.test :as e.f.s.test]
   [elin.interceptor.test :as sut]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)

(def ^:private done-test-leave
  (:leave sut/done-test))

(t/deftest done-test-test
  (let [unplaced-sign-args (atom [])
        placed-sign-args (atom [])
        set-quickfix-args (atom [])
        reset-all! #(do (reset! unplaced-sign-args [])
                        (reset! placed-sign-args [])
                        (reset! set-quickfix-args []))]
    (with-redefs [e.p.host/unplace-signs-by (fn [_ m] (swap! unplaced-sign-args conj m))
                  e.p.host/place-sign (fn [_ m] (swap! placed-sign-args conj m))
                  e.p.host/append-to-info-buffer (constantly nil)
                  e.f.quickfix/get-quickfix-list (constantly [])
                  e.f.quickfix/set-quickfix-list (fn [_ v] (swap! set-quickfix-args conj v))
                  e.f.s.test/set-last-failed-tests-query (constantly nil)
                  e.p.nrepl/supported-op? (constantly true)
                  e.f.n.c.test/summary (constantly {:succeeded? false
                                                    :summary "dummy summary"})]
      (t/testing "Signs are added for failed tests"
        (reset-all!)
        (with-redefs [e.f.n.c.test/collect-results (constantly [{:result :failed
                                                                 :filename "dummy.clj"
                                                                 :lnum 1
                                                                 :ns "foo"
                                                                 :var "bar"}])]
          (done-test-leave (h/test-elin))
          (t/is (= [{:name "error" :group "*"}]
                   @unplaced-sign-args))
          (t/is (= [{:name "error" :lnum 1 :file "dummy.clj" :group "bar"}]
                   @placed-sign-args))))

      (t/testing "Signs are removed for passed tests"
        (reset-all!)
        (with-redefs [e.f.n.c.test/collect-results (constantly [{:result :passed
                                                                 :filename "dummy.clj"
                                                                 :lnum 1
                                                                 :ns "foo"
                                                                 :var "bar"}])]
          (done-test-leave h/test-elin)
          (t/is (= [{:name "error" :group "bar"}]
                   @unplaced-sign-args))
          (t/is (empty? @placed-sign-args))))

      (t/testing "Failed tests are added to quickfix"
        (reset-all!)
        (with-redefs [e.f.n.c.test/collect-results (constantly [{:result :failed
                                                                 :filename "dummy.clj"
                                                                 :lnum 1
                                                                 :ns "foo"
                                                                 :var "bar"}])]
          (done-test-leave h/test-elin)
          (t/is (= 1 (count @set-quickfix-args)))
          (t/is (= [{:type "Error" :lnum 1 :filename "dummy.clj" :text "foo/bar"}]
                   (first @set-quickfix-args)))))

      (t/testing "Failed tests are not added to quickfix if already failed"
        (reset-all!)
        (with-redefs [e.f.n.c.test/collect-results (constantly [{:result :failed
                                                                 :filename "dummy.clj"
                                                                 :lnum 1
                                                                 :ns "foo"
                                                                 :var "bar"}])
                      e.f.quickfix/get-quickfix-list (constantly [;; Already exists
                                                                  {:type "Error"
                                                                   :filename "dummy.clj"
                                                                   :lnum 1
                                                                   :text "foo/bar"}
                                                                  ;; Another error
                                                                  {:type "Error"
                                                                   :filename "dummy.clj"
                                                                   :lnum 10
                                                                   :text "bar/baz"}])]
          (done-test-leave h/test-elin)
          (t/is (= 1 (count @set-quickfix-args)))
          (t/is (= (->> [{:type "Error" :lnum 1 :filename "dummy.clj" :text "foo/bar"}
                         {:type "Error" :lnum 10 :filename "dummy.clj" :text "bar/baz"}]
                        (sort-by :lnum))
                   (->> (first @set-quickfix-args)
                        (sort-by :lnum))))))

      (t/testing "Passed tests are removed from quickfix"
        (reset-all!)
        (with-redefs [e.f.n.c.test/collect-results (constantly [{:result :passed
                                                                 :filename "dummy.clj"
                                                                 :lnum 1
                                                                 :ns "foo"
                                                                 :var "bar"}])
                      e.f.quickfix/get-quickfix-list (constantly [;; Already exists
                                                                  {:type "Error"
                                                                   :filename "dummy.clj"
                                                                   :lnum 1
                                                                   :text "foo/bar"}
                                                                  ;; Another error
                                                                  {:type "Error"
                                                                   :filename "dummy.clj"
                                                                   :lnum 10
                                                                   :text "bar/baz"}])]
          (done-test-leave h/test-elin)
          (t/is (= 1 (count @set-quickfix-args)))
          (t/is (= [{:type "Error" :lnum 10 :filename "dummy.clj" :text "bar/baz"}]
                   (first @set-quickfix-args)))))

      (t/testing "Quickfix is empty if all tests are passed"
        (reset-all!)
        (with-redefs [e.f.n.c.test/collect-results (constantly [{:result :passed
                                                                 :filename "dummy.clj"
                                                                 :lnum 1
                                                                 :ns "foo"
                                                                 :var "bar"}])
                      e.f.quickfix/get-quickfix-list (constantly [{:type "Error"
                                                                   :filename "dummy.clj"
                                                                   :lnum 1
                                                                   :text "foo/bar"}])]
          (done-test-leave h/test-elin)
          (t/is (= 1 (count @set-quickfix-args)))
          (t/is (empty? (first @set-quickfix-args))))))))
