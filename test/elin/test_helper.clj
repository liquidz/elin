(ns elin.test-helper
  (:require
   [babashka.nrepl.server :as b.n.server]
   [elin.config :as e.config]
   [elin.test-helper.host :as h.host]
   [elin.test-helper.message :as h.message]
   [elin.test-helper.nrepl :as h.nrepl]
   [malli.dev.pretty :as m.d.pretty]
   [malli.instrument :as m.inst]
   [taoensso.timbre :as timbre]))

(def ^:dynamic *nrepl-server-port* nil)

(defn malli-instrument-fixture
  [f]
  (m.inst/instrument!
   {:report (m.d.pretty/reporter)})
  (f))

(defn test-nrepl-server-port-fixture
  [f]
  (let [{:as server :keys [socket]} (b.n.server/start-server! {:host "localhost" :port 0})
        port (.getLocalPort socket)]
    (try
      (binding [*nrepl-server-port* port]
        (f))
      (finally
        (b.n.server/stop-server! server)))))

(defn warn-log-level-fixture
  [f]
  (timbre/with-level :warn (f)))

(defn call-function? [msg fn-name]
  (and
   (= "test_call_function" (nth msg 2))
   (= fn-name (first (nth msg 3)))))

(def test-config
  (e.config/load-config "." {:server {:host "vim" :port 0}}))

(def test-message #'h.message/test-message)
(def get-outputs #'h.host/get-outputs)
(def test-host #'h.host/test-host)
(def test-nrepl-connection #'h.nrepl/test-nrepl-connection)
(def test-nrepl-client #'h.nrepl/test-nrepl-client)
(def test-nrepl #'h.nrepl/test-nrepl)
