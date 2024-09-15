(ns elin.util.sexpr-test
  (:require
   [clojure.test :as t]
   [elin.error :as e]
   [elin.util.sexpr :as sut]))

(t/deftest extract-ns-form-test
  (t/is (= "(ns bar)"
           (sut/extract-ns-form "(foo) (ns bar) (baz)")))
  (t/is (e/not-found? (sut/extract-ns-form "(foo) (baz)")))
  (t/is (e/not-found? (sut/extract-ns-form ""))))

(t/deftest extract-namespace-test
  (t/testing "ns"
    (t/is (= "foo.bar"
             (sut/extract-namespace "(ns foo.bar)"))))

  (t/testing "in-ns"
    (t/is (= "foo.bar"
             (sut/extract-namespace "(in-ns 'foo.bar)"))))

  (t/is (e/not-found? (sut/extract-namespace "")))
  (t/is (e/not-found? (sut/extract-namespace "(foo bar)"))))

(t/deftest add-require-test
  (t/testing "require exists"
    (t/testing "with alias"
      (t/is (= "(ns foo.core\n  (:require\n   [baz.core :as baz]\n   [bar.core :as bar]))"
               (sut/add-require "(ns foo.core\n  (:require\n   [bar.core :as bar]))"
                                'baz.core
                                'baz))))

    (t/testing "without alias"
      (t/is (= "(ns foo.core\n  (:require\n   baz.core\n   [bar.core :as bar]))"
               (sut/add-require "(ns foo.core\n  (:require\n   [bar.core :as bar]))"
                                'baz.core
                                nil))))

    (t/testing "no linebreak after :require"
      (t/is (= "(ns foo.core\n  (:require [baz.core :as baz]\n            [bar.core :as bar]))"
               (sut/add-require "(ns foo.core\n  (:require [bar.core :as bar]))"
                                'baz.core
                                'baz)))))

  (t/testing "no require"
    (t/is (= "(ns foo.core\n  (:require [baz.core :as baz]))"
             (sut/add-require "(ns foo.core)"
                              'baz.core
                              'baz))))

  (t/testing "no namespace form"
    (t/is (e/not-found? (sut/add-require ""
                                         'baz.core
                                         'baz)))))

(t/deftest add-import-test
  (t/testing "import exists"
    (t/testing "has linebreak after :import"
      (t/is (= "(ns foo.core\n  (:import\n   java.lang.Number\n   java.lang.String))"
               (sut/add-import "(ns foo.core\n  (:import\n   java.lang.String))"
                               'java.lang.Number))))

    (t/testing "no linebreak after :import"
      (t/is (= "(ns foo.core\n  (:import java.lang.Number\n            java.lang.String))"
               (sut/add-import "(ns foo.core\n  (:import java.lang.String))"
                               'java.lang.Number)))))

  (t/testing "no import"
    (t/is (= "(ns foo.core\n  (:import java.lang.String))"
             (sut/add-import "(ns foo.core)"
                             'java.lang.String))))

  (t/testing "no namespace form"
    (t/is (e/not-found? (sut/add-import "" 'java.lang.String)))))

(t/deftest apply-cider-coordination-test
  (let [code "(defn- foo [a b] #dbg (+ b (+ a 1)))"]
    (t/is (= {:code "b" :position [0 25]}
             (sut/apply-cider-coordination code [3 1])))

    (t/is (= {:code "a" :position [0 30]}
             (sut/apply-cider-coordination code [3 2 1])))

    (t/is (= {:code "(+ a 1)" :position [0 27]}
             (sut/apply-cider-coordination code [3 2])))))
