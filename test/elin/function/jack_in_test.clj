(ns elin.function.jack-in-test
  (:require
   [clojure.core.async :as async]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :as t]
   [elin.constant.jack-in :as e.c.jack-in]
   [elin.constant.nrepl :as e.c.nrepl]
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

(def ^:private dummy-port 123)

(defn- generate-test-command
  [project-type additional-args options]
  (#'sut/generate-command project-type
                          dummy-port
                          additional-args
                          options))

(def ^:private generate-clojure-cli-command
  (partial generate-test-command e.c.jack-in/clojure-cli))

(def ^:private generate-leiningen-command
  (partial generate-test-command e.c.jack-in/leiningen))

(t/deftest generate-command-test
  (t/testing "Clojure CLI"
    (t/testing "additional dependencies"
      (let [actuals (->> (generate-clojure-cli-command [] {:dependencies '{dummy/dependency {:mvn/version "0.1.0"}}})
                         (:command)
                         (filter #(str/includes? % "nrepl/nrepl"))
                         (map edn/read-string))]
        (t/is (= 1 (count actuals)))
        (t/is (= {:mvn/version "0.1.0"}
                 (-> (first actuals)
                     (get-in [:deps 'dummy/dependency]))))))

    (t/testing "additional middlewares"
      (let [actuals (->> (generate-clojure-cli-command [] {:middlewares '[dummy/middleware]})
                         (:command)
                         (filter #(str/includes? % "cider-middleware"))
                         (map edn/read-string))]
        (t/is (= 1 (count actuals)))
        (t/is (true? (some #(= 'dummy/middleware %)
                           (first actuals)))))))

  (t/testing "Leiningen"
    (t/testing "additional dependencies"
      (let [actuals (->> (generate-leiningen-command [] {:dependencies '{dummy/dependency {:mvn/version "0.1.0"}}})
                         (:command)
                         (drop-while #(not= ":dependencies" %))
                         (drop 2)
                         (take-while #(not= "--" %)))]
        (t/is (true? (some #(= "[dummy/dependency \"0.1.0\"]" %)
                           actuals)))))

    (t/testing "additional middlewares"
      (let [actuals (->> (generate-leiningen-command [] {:middlewares '[dummy/middleware]})
                         (:command)
                         (drop-while #(not (str/includes? % ":nrepl-middleware")))
                         (drop 2)
                         (take-while #(not= "--" %)))]
        (t/is (true? (some #(= 'dummy/middleware %)
                           actuals)))))))

(t/deftest launch-process-test
  (let [started-args (atom [])]
    (with-redefs [e.p.host/get-current-file-path! (fn [& _] (async/go dummy-path))
                  e.u.process/start (fn [_ args]
                                      (swap! started-args conj args))]
      (t/testing "auto detected project"
        (reset! started-args [])
        (let [{:keys [port language]} (sut/launch-process h/test-elin)
              [started-arg] @started-args]
          (t/is (int? port))
          (t/is (= 1 (count @started-args)))

          (t/is (= {:dir (deref #'sut/elin-root-dir)}
                   (first started-arg)))
          (t/is (= e.c.jack-in/clojure-command
                   (second started-arg)))

          (t/is (= e.c.nrepl/lang-clojure
                   language))))

      (t/testing "force project"
        (reset! started-args [])
        (let [{:keys [port language]} (sut/launch-process h/test-elin {:forced-project e.c.jack-in/babashka})
              [started-arg] @started-args]
          (t/is (int? port))
          (t/is (= 1 (count @started-args)))

          (t/is (= {:dir dummy-root}
                   (first started-arg)))
          (t/is (= e.c.jack-in/babashka-command
                   (second started-arg)))

          (t/is (= e.c.nrepl/lang-clojure
                   language))))

      (t/testing "Clojure CLI"
        (t/testing "add dependencies"
          (reset! started-args []))

        (t/testing "add middlewares")))))
