(ns elin.function.lookup-test
  (:require
   [clojure.test :as t]
   [elin.function.lookup :as sut]
   [elin.function.nrepl.cider :as e.f.n.cider]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)

(t/deftest lookup-test
  (t/testing "positive"
    (t/testing "cider info"
      (t/testing "regular"
        (let [info-resp {:ns "foo.bar"
                         :name "baz"
                         :file "./core.clj"
                         :arglists-str ""
                         :column 1
                         :line 2}]
          (with-redefs [e.f.n.cider/info!! (constantly info-resp)]
            (t/is (= info-resp
                     (sut/lookup (h/test-elin) "foo.bar" "baz"))))))

      ;; TODO
      (t/testing "protocol"))

    (t/testing "nrepl lookup"))

  (t/testing "negative"))
