(ns elin.function.nrepl.cider.test
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [elin.constant.nrepl :as e.c.nrepl]
   [elin.function.nrepl.cider :as e.f.n.cider]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema.component :as e.s.component]
   [elin.schema.nrepl :as e.s.nrepl]
   [malli.core :as m]))

(m/=> summary [:=> [:cat map?] e.s.nrepl/?TestSummary])
(defn summary
  [test-resp]
  (let [{:keys [summary]} test-resp]
    (if (zero? (:test summary))
      {:summary "No tests found"
       :succeeded? true}
      {:summary (format "%s: Ran %d assertions, in %d test functions. %d failures, %d errors."
                        (:testing-ns test-resp)
                        (:test summary) (:var summary)
                        (:fail summary) (:error summary))
       :succeeded? (= 0
                      (:fail summary)
                      (:error summary))})))

(defn- readable-filename
  [filename]
  (when (and (not= "NO_SOURCE_FILE" filename)
             (.exists (io/file filename)))
    filename))

(m/=> test-message [:=> [:cat map?] string?])
(defn- test-message
  [test-result]
  (let [var' (:var test-result)
        {:keys [context message]} test-result]
    (cond
      (and (seq context) (seq message))
      (format "%s: %s / %s" var' context message)

      (seq context)
      (format "%s: %s" var' context)

      (seq message)
      (format "%s: %s" var' message)

      :else
      "")))

(m/=> test-actual-values [:=> [:cat map?] [:sequential e.s.nrepl/?TestActualValue]])
(defn- test-actual-values
  [{:keys [diffs actual]}]
  (cond
    (sequential? diffs)
    (for [diff diffs]
      {:actual (str/trim (first diff))
       :diffs (format "- %s\n+ %s"
                      (str/trim (first (second diff)))
                      (str/trim (second (second diff))))})

    :else
    [{:actual (str/trim actual)}]))

(m/=> collect-results [:=> [:cat e.s.component/?Nrepl map?] [:sequential e.s.nrepl/?TestResult]])
(defn collect-results
  [nrepl test-resp]
  (let [ns-path-op-supported? (e.p.nrepl/supported-op? nrepl e.c.nrepl/ns-path-op)]
    (flatten
      (for [[_ns-kw var-map] (or (:results test-resp) {})]
        (for [[_var-kw test-results] (or var-map {})]
          (for [test-result test-results
                :let [{test-type :type ns-str :ns var-str :var lnum :line} test-result]]
            (if (and (not= test-type "fail")
                     (not= test-type "error"))
              {:result :passed
               :ns ns-str
               :var var-str
               :text (test-message test-result)}

              (let [filename (or (readable-filename (:file test-result))
                                 (when ns-path-op-supported?
                                   (e.f.n.cider/ns-path!! nrepl ns-str))
                                 (:file test-result))
                    error (cond-> {:filename filename
                                   :text (test-message test-result)
                                   :expected (or (some-> (:expected test-result)
                                                         (str/trim))
                                                 "")
                                   :ns ns-str
                                   :var var-str}
                            lnum (assoc :lnum lnum))]
                (condp = test-type
                  "fail"
                  (for [actual-value (test-actual-values test-result)]
                    (merge error
                           actual-value
                           {:result :failed}))

                  "error"
                  (merge error
                         {:result :failed
                          :actual (or (:error test-result)
                                      (:actual test-result))}))))))))))
