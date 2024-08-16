(ns elin.function.namespace-test
  (:require
   [clojure.test :as t]
   [elin.function.clj-kondo :as e.f.clj-kondo]
   [elin.function.namespace :as sut]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)

(t/deftest get-namespaces-test
  (let [elin (-> (h/nrepl-eval-config (constantly (pr-str ["foo" "bar"])))
                 (h/test-elin))]
    (with-redefs [e.f.clj-kondo/namespace-symbols (constantly ["hello" "world"])]
      (t/is (= ["bar" "foo" "hello" "world"]
               (sut/get-namespaces elin))))))

(t/deftest missing-candidates-test
  (let [elin (-> (h/host-get-namespace-sexpr!-config "(ns test.namespace)")
                 (h/test-elin))]
    (t/testing "missing require"
      (t/testing "favorites"
        (t/is (= [{:name 'foo.bar.baz :type :ns}]
                 (->> {:code "foo/bar"
                       :requiring-favorites '{foo.bar.baz foo}
                       :java-classes {}}
                      (sut/missing-candidates elin)))))

      (t/testing "non favorites"
        (t/testing "clj-kondo"
          (t/is (= [{:name 'elin.util.id :type :ns}]
                   (->> {:code "e.u.id/next-id"
                         :requiring-favorites {}
                         :java-classes {}}
                        (sut/missing-candidates elin)))))

        (t/testing "clj-kondo"
          (t/is (empty? (->> {:code "unknown/function"
                              :requiring-favorites {}
                              :java-classes {}}
                             (sut/missing-candidates elin)))))))

    (t/testing "missing import"
      (t/is (= [{:name 'clojure.lang.ExceptionInfo :type :class}]
               (->> {:code "ExceptionInfo"
                     :requiring-favorites {}
                     :java-classes {:clojure.lang #{'ExceptionInfo}}}
                    (sut/missing-candidates elin))))

      (t/is (empty? (->> {:code "ExceptionInfo"
                          :requiring-favorites {}
                          :java-classes {}}
                         (sut/missing-candidates elin)))))))
