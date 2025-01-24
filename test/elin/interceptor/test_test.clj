(ns elin.interceptor.test-test
  (:require
   [clojure.test :as t]
   [elin.function.nrepl :as e.f.nrepl]
   [elin.function.nrepl.cider.test :as e.f.n.c.test]
   [elin.function.quickfix :as e.f.quickfix]
   [elin.interceptor.test :as sut]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)

(def ^:private dummy-passed-result
  {:result :passed
   :filename "dummy.clj"
   :lnum 1
   :ns "foo"
   :var "bar"})

(def ^:private dummy-failed-result
  (assoc dummy-passed-result
         :result :failed))

(t/deftest parse-test-result-test
  (with-redefs [e.p.nrepl/supported-op? (constantly true)
                e.f.n.c.test/collect-results (constantly [dummy-failed-result])
                e.f.n.c.test/summary (constantly {:succeeded? false
                                                  :summary "dummy summary"})]
    (let [result ((:leave sut/parse-test-result) (h/test-elin))]
      (t/is (= {:passed nil
                :failed [{:result :failed
                          :filename "dummy.clj"
                          :lnum 1
                          :ns "foo"
                          :var "bar"}]
                :succeeded? false
                :summary "dummy summary"}
               (select-keys result [:passed :failed :succeeded? :summary]))))))

(t/deftest update-test-result-sign-test
  (let [unplaced-sign-args (atom [])
        placed-sign-args (atom [])
        reset-all! #(do (reset! unplaced-sign-args [])
                        (reset! placed-sign-args []))
        update-test-result-sign-enter (:enter sut/update-test-result-sign)]

    (with-redefs [e.p.host/unplace-signs-by (fn [_ m] (swap! unplaced-sign-args conj m))
                  e.p.host/place-sign (fn [_ m] (swap! placed-sign-args conj m))]

      (t/testing "Signs are added for failed tests"
        (reset-all!)
        (-> (h/test-elin)
            (assoc :failed [dummy-failed-result]
                   :succeeded? false
                   :summary "dummy")
            (update-test-result-sign-enter))

        (t/is (= [{:name "error" :group "*"}]
                 @unplaced-sign-args))
        (t/is (= [{:name "error" :lnum 1 :file "dummy.clj" :group "bar"}]
                 @placed-sign-args)))

      (t/testing "Signs are removed for passed tests"
        (reset-all!)
        (-> (h/test-elin)
            (assoc :passed [dummy-passed-result]
                   :succeeded? true
                   :summary "dummy")
            (update-test-result-sign-enter))

        (t/is (= [{:name "error" :group "bar"}]
                 @unplaced-sign-args))
        (t/is (empty? @placed-sign-args))))))

;; TODO
;; (t/deftest append-test-result-to-info-buffer-test)

(t/deftest apply-test-result-to-quickfix-test
  (let [set-quickfix-args (atom [])
        reset-all! #(reset! set-quickfix-args [])
        apply-test-result-to-quickfix-enter (:enter sut/apply-test-result-to-quickfix)]
    (t/testing "Failed tests are added to quickfix"
      (reset-all!)
      (with-redefs [e.f.quickfix/get-quickfix-list (constantly [])
                    e.f.quickfix/set-quickfix-list (fn [_ v] (swap! set-quickfix-args conj v))]
        (-> (h/test-elin)
            (assoc :failed [dummy-failed-result]
                   :succeeded? false
                   :summary "dummy")
            (apply-test-result-to-quickfix-enter)))

      (t/is (= 1 (count @set-quickfix-args)))
      (t/is (= [{:type "Error" :lnum 1 :filename "dummy.clj" :text "foo/bar"}]
               (first @set-quickfix-args))))

    (t/testing "Failed tests are not added to quickfix if already failed"
      (reset-all!)
      (with-redefs [e.f.quickfix/get-quickfix-list (constantly [;; Already exists
                                                                {:type "Error"
                                                                 :filename (:filename dummy-failed-result)
                                                                 :lnum (:lnum dummy-failed-result)
                                                                 :text "foo/bar"}
                                                                ;; Another error
                                                                {:type "Error"
                                                                 :filename (:filename dummy-failed-result)
                                                                 :lnum (* 10 (:lnum dummy-failed-result))
                                                                 :text "bar/baz"}])
                    e.f.quickfix/set-quickfix-list (fn [_ v] (swap! set-quickfix-args conj v))]
        (-> (h/test-elin)
            (assoc :failed [dummy-failed-result]
                   :succeeded? false
                   :summary "dummy")
            (apply-test-result-to-quickfix-enter)))

      (t/is (= 1 (count @set-quickfix-args)))
      ;; FIXME
      (t/is (= (->> [{:type "Error" :lnum 1 :filename "dummy.clj" :text "foo/bar"}
                     {:type "Error" :lnum 10 :filename "dummy.clj" :text "bar/baz"}]
                    (sort-by :lnum))
               (->> (first @set-quickfix-args)
                    (sort-by :lnum)))))

    (t/testing "Passed tests are removed from quickfix"
      (reset-all!)
      (with-redefs [e.f.quickfix/get-quickfix-list (constantly [;; Already exists
                                                                {:type "Error"
                                                                 :filename (:filename dummy-failed-result)
                                                                 :lnum (:lnum dummy-failed-result)
                                                                 :text "foo/bar"}
                                                                ;; Another error
                                                                {:type "Error"
                                                                 :filename (:filename dummy-failed-result)
                                                                 :lnum (* 10 (:lnum dummy-failed-result))
                                                                 :text "bar/baz"}])
                    e.f.quickfix/set-quickfix-list (fn [_ v] (swap! set-quickfix-args conj v))]
        (-> (h/test-elin)
            (assoc :passed [dummy-passed-result]
                   :succeeded? true
                   :summary "dummy")
            (apply-test-result-to-quickfix-enter)))

      (t/is (= 1 (count @set-quickfix-args)))
      (t/is (= [{:type "Error" :lnum 10 :filename "dummy.clj" :text "bar/baz"}]
               (first @set-quickfix-args))))

    (t/testing "Quickfix is empty if all tests are passed"
      (reset-all!)
      (with-redefs [e.f.quickfix/get-quickfix-list (constantly [{:type "Error"
                                                                 :filename (:filename dummy-failed-result)
                                                                 :lnum (:lnum dummy-failed-result)
                                                                 :text "foo/bar"}])
                    e.f.quickfix/set-quickfix-list (fn [_ v] (swap! set-quickfix-args conj v))]
        (-> (h/test-elin)
            (assoc :passed [dummy-passed-result]
                   :succeeded? true
                   :summary "dummy")
            (apply-test-result-to-quickfix-enter)))

      (t/is (= 1 (count @set-quickfix-args)))
      (t/is (empty? (first @set-quickfix-args))))))

;; TODO
;; (t/deftest store-last-failed-test-query-test)

;; TODO
;; (t/deftest output-test-result-to-cmdline-test)

;; TODO
;; (t/deftest focus-current-testing-test)

(def ^:private correct-test-vars-automatically-enter
  (:enter sut/correct-test-vars-automatically))

(t/deftest correct-test-vars-automatically-test
  (t/testing "Source namespace"
    (let [ctx (assoc (h/test-elin)
                     :ns "foo.bar"
                     :file "/foo/bar.clj")]
      (with-redefs [e.f.nrepl/load-file!! (constantly nil)
                    slurp (constantly "")]
        (t/is (= (assoc ctx
                        :ns "foo.bar-test"
                        :file "/foo/bar_test.clj")
                 (correct-test-vars-automatically-enter ctx))))))

  (t/testing "Test namespace"
    (let [ctx (assoc (h/test-elin)
                     :ns "foo.bar-test"
                     :file "/foo/bar_test.clj")]
      (t/is (= ctx (correct-test-vars-automatically-enter ctx))))))
