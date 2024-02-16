(ns elin.component.plugin-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :as t]
   [com.stuartsierra.component :as component]
   [elin.component.plugin :as sut]
   [elin.system :as e.system]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)

(t/deftest new-plugin-test
  (let [edn-file (.getAbsolutePath (io/file "example-plugin" "plugin.edn"))
        config {:plugin {:edn-files [edn-file]}}
        {:as sys :keys [plugin]} (-> (e.system/new-system config)
                                     (select-keys [:lazy-host :plugin])
                                     (component/start-system))]
    (try
      (t/is (= {:name (str ::sut/plugin)
                :handlers '[elin-example.core/hello]
                :interceptors '[elin-example.core/interceptor]}
               (:loaded-plugin plugin)))
      (finally
        (component/stop-system sys)))))

(t/deftest new-plugin-no-plugin-test
  (let [{:as sys :keys [plugin]} (-> (e.system/new-system)
                                     (select-keys [:lazy-host :plugin])
                                     (component/start-system))]
    (try
      (t/is (= {:name (str ::sut/plugin)
                :handlers []
                :interceptors []}
               (:loaded-plugin plugin)))
      (finally
        (component/stop-system sys)))))
