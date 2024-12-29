(ns elin.util.overview-test
  (:require
   [clojure.test :as t]
   [elin.test-helper :as h]
   [elin.util.overview :as sut]))

(t/use-fixtures :once h/malli-instrument-fixture)
(t/use-fixtures :once h/warn-log-level-fixture)

(t/deftest cut-test
  (t/is (= nil (sut/cut nil 5)))
  (t/is (= 'sym (sut/cut 'sym 5)))
  (t/is (= 'kwd (sut/cut 'kwd 5)))
  (t/is (= 1234 (sut/cut 1234 5))))

(t/deftest cut-list-test
  (t/is (= '(1 2 ...)
           (sut/cut '(1 2 3) 2)))
  (t/is (= '(1 2 3)
           (sut/cut '(1 2 3) 3)))
  (t/is (= '(1 2 3)
           (sut/cut '(1 2 3) 4)))
  (t/is (= '(...)
           (sut/cut '(1 2 3) 0)))
  (t/is (= '(...)
           (sut/cut '(1 2 3) -1)))

  (t/is (= '()
           (sut/cut '() 2)))
  (t/is (= '()
           (sut/cut '() 0)))
  (t/is (= '()
           (sut/cut '() -1))))

(t/deftest cut-vector-test
  (t/is (= '[1 2 ...]
           (sut/cut [1 2 3] 2)))
  (t/is (= '[1 2 3]
           (sut/cut [1 2 3] 3)))
  (t/is (= '[1 2 3]
           (sut/cut [1 2 3] 4)))
  (t/is (= '[...]
           (sut/cut [1 2 3] 0)))
  (t/is (= '[...]
           (sut/cut [1 2 3] -1)))

  (t/is (= '[]
           (sut/cut [] 2)))
  (t/is (= '[]
           (sut/cut [] 0)))
  (t/is (= '[]
           (sut/cut [] -1))))

(t/deftest cut-map-test
  (t/is (= '{:a 1 :b 2 etc ...}
           (sut/cut {:a 1 :b 2 :c 3} 2)))
  (t/is (= '{:a 1 :b 2 :c 3}
           (sut/cut {:a 1 :b 2 :c 3} 3)))
  (t/is (= '{:a 1 :b 2 :c 3}
           (sut/cut {:a 1 :b 2 :c 3} 4)))
  (t/is (= '{etc ...}
           (sut/cut {:a 1 :b 2 :c 3} 0)))
  (t/is (= '{etc ...}
           (sut/cut {:a 1 :b 2 :c 3} -1)))

  (t/is (= '{}
           (sut/cut {} 2)))
  (t/is (= '{}
           (sut/cut {} 0)))
  (t/is (= '{}
           (sut/cut {} -1))))

(t/deftest cut-set-test
  (t/is (contains? (set [#{1 2 '...}
                         #{1 3 '...}
                         #{2 3 '...}])
          (sut/cut #{1 2 3} 2)))
  (t/is (= #{1 2 3}
           (sut/cut #{1 2 3} 3)))
  (t/is (= #{1 2 3}
           (sut/cut #{1 2 3} 4)))
  (t/is (= #{'...}
           (sut/cut #{1 2 3} 0)))
  (t/is (= #{'...}
           (sut/cut #{1 2 3} -1)))

  (t/is (= #{}
           (sut/cut #{} 2)))
  (t/is (= #{}
           (sut/cut #{} 0)))
  (t/is (= #{}
           (sut/cut #{} -1))))

(t/deftest cut-string-test
  (t/is (= "ab..."
           (sut/cut "abc" 2)))
  (t/is (= "abc"
           (sut/cut "abc" 3)))
  (t/is (= "abc"
           (sut/cut "abc" 4)))
  (t/is (= "..."
           (sut/cut "abc" 0)))
  (t/is (= "..."
           (sut/cut "abc" -1)))

  (t/is (= ""
           (sut/cut "" 2)))
  (t/is (= ""
           (sut/cut "" 0)))
  (t/is (= ""
           (sut/cut "" -1))))

(t/deftest overview-test
  (t/is (= nil (sut/overview nil)))
  (t/is (= "foo" (sut/overview "foo")))
  (t/is (= "fo..." (sut/overview "foo" {:max-depth 0 :max-string-length 2}))))

(t/deftest overview-list-test
  (t/is (= '() (sut/overview '())))

  (t/is (= '(1 2 3 4)
           (sut/overview '(1 2 3 4))))
  (t/is (= '(1 2 ...)
           (sut/overview '(1 2 3 4) {:max-list-length 2})))
  (t/is (= '(1 2 3 ...)
           (sut/overview '(1 2 3 4) {:max-list-length 3})))
  (t/is (= '(1 2 3 4)
           (sut/overview '(1 2 3 4) {:max-list-length 4})))

  (t/is (= '(1 (2 3 4))
           (sut/overview '(1 (2 3 4)))))
  (t/is (= '(1 ...)
           (sut/overview '(1 (2 3 4)) {:max-list-length 1})))
  (t/is (= '(1 (2 3 ...))
           (sut/overview '(1 (2 3 4)) {:max-list-length 2})))
  (t/is (= '(1 (2 3 4))
           (sut/overview '(1 (2 3 4)) {:max-list-length 3})))

  (t/is (= '(1 (2 ...))
           (sut/overview '(1 (2 (3 4))) {:max-depth 1}))))

(t/deftest overview-vector-test
  (t/is (= [] (sut/overview [])))

  (t/is (= '[1 2 3 4]
           (sut/overview '[1 2 3 4])))
  (t/is (= '[1 2 ...]
           (sut/overview '[1 2 3 4] {:max-vector-length 2})))
  (t/is (= '[1 2 3 ...]
           (sut/overview '[1 2 3 4] {:max-vector-length 3})))
  (t/is (= '[1 2 3 4]
           (sut/overview '[1 2 3 4] {:max-vector-length 4})))

  (t/is (= '[1 [2 3 4]]
           (sut/overview '[1 [2 3 4]])))
  (t/is (= '[1 ...]
           (sut/overview '[1 [2 3 4]] {:max-vector-length 1})))
  (t/is (= '[1 [2 3 ...]]
           (sut/overview '[1 [2 3 4]] {:max-vector-length 2})))
  (t/is (= '[1 [2 3 4]]
           (sut/overview '[1 [2 3 4]] {:max-vector-length 3})))

  (t/is (= '[1 [2 ...]]
           (sut/overview '[1 [2 [3 4]]] {:max-depth 1}))))

(t/deftest overview-map-test
  (t/is (= {} (sut/overview {})))

  (t/is (= '{:a 1 :b 2 :c 3 :d 4}
           (sut/overview {:a 1 :b 2 :c 3 :d 4})))
  (t/is (= '{:a 1 :b 2 etc ...}
           (sut/overview {:a 1 :b 2 :c 3 :d 4} {:max-map-length 2})))
  (t/is (= '{:a 1 :b 2 :c 3 etc ...}
           (sut/overview {:a 1 :b 2 :c 3 :d 4} {:max-map-length 3})))
  (t/is (= '{:a 1 :b 2 :c 3 :d 4}
           (sut/overview {:a 1 :b 2 :c 3 :d 4} {:max-map-length 4})))

  (t/is (= '{:a {:b 2 :c 3 :d 4}}
           (sut/overview {:a {:b 2 :c 3 :d 4}})))
  (t/is (= '{:a {:b 2 etc ...}}
           (sut/overview {:a {:b 2 :c 3 :d 4}} {:max-map-length 1})))
  (t/is (= '{:a {:b 2 :c 3 etc ...}}
           (sut/overview {:a {:b 2 :c 3 :d 4}} {:max-map-length 2})))
  (t/is (= '{:a {:b 2 :c 3 :d 4}}
           (sut/overview {:a {:b 2 :c 3 :d 4}} {:max-map-length 3})))

  (t/is (= '{:a {:b ...}}
           (sut/overview {:a {:b {:c 3 :d 4}}} {:max-depth 1}))))

(t/deftest overview-set-test
  (t/is (= #{} (sut/overview #{})))

  (t/is (= #{1 2 3 4}
          (sut/overview #{1 2 3 4})))
  (t/is (contains? (set [#{1 2 3 '...}
                         #{1 2 4 '...}
                         #{1 3 4 '...}
                         #{2 3 4 '...}])
          (sut/overview #{1 2 3 4} {:max-set-length 3})))
  (t/is (= #{1 2 3 4}
          (sut/overview #{1 2 3 4} {:max-set-length 4})))

  (t/is (contains? (set [#{1 #{2 3 '...}}
                         #{1 #{2 4 '...}}
                         #{1 #{3 4 '...}}])
          (sut/overview #{1 #{2 3 4}} {:max-set-length 2})))

  (t/is (contains? (set [#{1 #{2 '...}}
                         #{1 #{#{3 '...} '...}}
                         #{1 #{#{4 '...} '...}}])
          (sut/overview #{1 #{2 #{3 4}}} {:max-depth 1}))))

(t/deftest overview-string-test
  (t/is (= "" (sut/overview "")))

  (t/is (= "abcd" (sut/overview "abcd")))
  (t/is (= "abcd"
           (sut/overview "abcd" {:max-string-length 2})))

  (t/is (= "ab..."
           (sut/overview "abcd" {:max-depth 0 :max-string-length 2})))
  (t/is (= "abcd"
           (sut/overview "abcd" {:max-depth 0 :max-string-length 4}))))
