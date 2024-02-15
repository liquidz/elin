(ns elin.config-test
  (:require
   [clojure.test :as t]
   [elin.config :as sut]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)

(t/deftest merge-configs-test
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

  (t/testing "includes"
    (t/is (= {:a {:includes [1 2]}}
             (sut/merge-configs {:a {:includes [1]}}
                                {:a {:includes [2]}}))))
  (t/testing "excludes"
    (t/is (= {:a {:excludes [1 2]}}
             (sut/merge-configs {:a {:excludes [1]}}
                                {:a {:excludes [2]}}))))

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

(t/deftest load-config-test
  (t/is (some? (sut/load-config "." {:server {:host "" :port 0}}))))
