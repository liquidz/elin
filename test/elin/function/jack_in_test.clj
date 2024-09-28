(ns elin.function.jack-in-test
  (:require
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [clojure.test :as t]
   [elin.constant.jack-in :as e.c.jack-in]
   [elin.function.jack-in :as sut]
   [elin.protocol.host :as e.p.host]
   [elin.test-helper :as h]
   [elin.util.process :as e.u.process]))

(def ^:private dummy-path
  *file*)
(def ^:private dummy-root
  (-> (io/file dummy-path)
      (.getParentFile)
      (.getAbsolutePath)))

(t/deftest launch-process-test
  (let [started-args (atom [])]
    (with-redefs [e.p.host/get-current-file-path! (fn [& _] (async/go dummy-path))
                  e.u.process/start (fn [_ args]
                                      (swap! started-args conj args))]
      (t/testing "auto detected project"
        (reset! started-args [])
        (let [port (sut/launch-process h/test-elin)
              [started-arg] @started-args]
          (t/is (int? port))
          (t/is (= 1 (count @started-args)))

          (t/is (= {:dir (deref #'sut/elin-root-dir)}
                   (first started-arg)))
          (t/is (= (deref #'sut/clojure-command)
                   (second started-arg)))))

      (t/testing "force project"
        (reset! started-args [])
        (let [port (sut/launch-process h/test-elin {:forced-project e.c.jack-in/babashka})
              [started-arg] @started-args]
          (t/is (int? port))
          (t/is (= 1 (count @started-args)))

          (t/is (= {:dir dummy-root}
                   (first started-arg)))
          (t/is (= (deref #'sut/babashka-command)
                   (second started-arg))))))))
