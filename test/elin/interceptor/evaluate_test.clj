(ns elin.interceptor.evaluate-test
  (:require
   [clojure.test :as t]
   [elin.interceptor.evaluate :as sut]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)

(t/deftest unwrap-comment-form-test
  (let [test-code (str '(comment (+ 1 2) (+ 3 4)))
        elin (assoc (h/test-elin)
                    :code test-code)
        {:keys [enter]} sut/unwrap-comment-form
        unwrap-comment-form-test (fn [column]
                                   (-> elin
                                       (assoc :options {:line 1 :column 1 :cursor-line 1 :cursor-column column})
                                       (enter)
                                       (:code)))
        results (->> (range 1 (inc (count test-code)))
                     (map #(vector % (unwrap-comment-form-test %))))]
    (t/is (->> results
               (take-while #(<= (first %) 9))
               (map second)
               (every? #(= (str '(do (+ 1 2) (+ 3 4))) %))))

    (t/is (->> results
               (drop-while #(<= (first %) 9))
               (take-while #(<= (first %) 16))
               (map second)
               (every? #(= (str '(+ 1 2)) %))))

    (t/is (->> results
               (drop-while #(<= (first %) 16))
               (take-while #(<= (first %) 17))
               (map second)
               (every? #(= (str '(do (+ 1 2) (+ 3 4))) %))))

    (t/is (->> results
               (drop-while #(<= (first %) 17))
               (take-while #(<= (first %) 24))
               (map second)
               (every? #(= (str '(+ 3 4)) %))))

    (t/is (->> results
               (drop-while #(<= (first %) 24))
               (map second)
               (every? #(= (str '(do (+ 1 2) (+ 3 4))) %))))))
