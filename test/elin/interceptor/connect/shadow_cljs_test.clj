(ns elin.interceptor.connect.shadow-cljs-test
  (:require
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [clojure.test :as t]
   [elin.function.select :as e.f.select]
   [elin.interceptor.connect.shadow-cljs :as sut]
   [elin.protocol.host :as e.p.host]
   [elin.test-helper :as h]
   [elin.util.file :as e.u.file]))

(t/use-fixtures :once h/malli-instrument-fixture)
(t/use-fixtures :once h/warn-log-level-fixture)

(def ^:private detect-shadow-cljs-port-enter
  (:enter sut/detect-shadow-cljs-port))

;; (def ^:private detect-shadow-cljs-port-leave
;;   (:leave sut/detect-shadow-cljs-port))

(t/deftest detect-shadow-cljs-port-enter-test
  (let [cwd (.getAbsolutePath (io/file "."))
        test-elin (h/test-elin)
        detect-shadow-cljs-port-enter-test (fn [hostname port-file]
                                             (-> test-elin
                                                 (assoc :hostname hostname
                                                        :port-file port-file)
                                                 (detect-shadow-cljs-port-enter)
                                                 (select-keys [:hostname port-file])))]
    (t/testing "Positive"
      (t/testing "No shadow-cljs port file"
        (with-redefs [e.p.host/get-current-working-directory! (fn [& _]
                                                                (async/go cwd))
                      e.f.select/select-from-candidates (fn [_ candidates]
                                                          (first candidates))]
          (t/is (= {:hostname nil}
                   (detect-shadow-cljs-port-enter-test nil nil)))))

      (t/testing "Exists shadow-cljs port file, but not selected")
      (t/testing "Exists shadow-cljs port file, and selected")

      (t/testing "Failed to fetch project root directory"
        (with-redefs [e.p.host/get-current-working-directory! (fn [& _]
                                                                (async/go cwd))
                      e.u.file/get-project-root-directory (constantly nil)]
          (t/is (= {:hostname nil}
                   (detect-shadow-cljs-port-enter-test nil nil))))))

    (t/testing "Negative")))
