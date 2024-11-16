(ns elin.function.lookup-test
  (:require
   [clojure.test :as t]
   [elin.function.clj-kondo :as e.f.clj-kondo]
   [elin.function.lookup :as sut]
   [elin.function.nrepl.cider :as e.f.n.cider]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)

(t/deftest lookup-test
  (t/testing "positive"
    (t/testing "cider info"
      (t/testing "regular"
        (let [info-resp (h/dummy-lookup-response)]
          (with-redefs [e.f.n.cider/info!! (constantly info-resp)]
            (t/is (= info-resp
                     (sut/lookup (h/test-elin) "foo.bar" "baz"))))))

      ;; TODO
      (t/testing "protocol"))

    (t/testing "nrepl lookup"
      (t/testing "info does not respond namespace and var name"
        (let [info-resp {:status ["done"]}
              fallback-resp (h/dummy-lookup-response)]
          (with-redefs [e.f.n.cider/info!! (constantly info-resp)
                        e.f.clj-kondo/lookup (constantly fallback-resp)]
            (t/is (= fallback-resp
                     (sut/lookup (h/test-elin) "foo.bar" "baz")))))))))
