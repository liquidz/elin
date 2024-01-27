(ns elin.nrepl.response
  (:require
   [clojure.core.async :as async]
   [elin.log :as e.log]
   [elin.schema.nrepl :as e.s.nrepl]
   [malli.core :as m]))

(m/=> done? [:=> [:cat e.s.nrepl/?Message] boolean?])
(defn- done?
  [msg]
  (boolean
   (some #(= % "done")
         (:status msg))))

(m/=> add-message [:=> [:cat e.s.nrepl/?Manager e.s.nrepl/?Message] e.s.nrepl/?Manager])
(defn- add-message
  [this
   {:as msg :keys [id]}]
  (if (and id
           (int? id)
           (contains? this id))
    (update-in this [id :responses] conj msg)
    this))

(m/=> put-done-responses [:=> [:cat e.s.nrepl/?Manager e.s.nrepl/?Message] e.s.nrepl/?Manager])
(defn- put-done-responses
  [this
   {:as msg :keys [id]}]
  (if (and id
           (int? id)
           (done? msg))
    (if-let [{:keys [responses channel]} (get this id)]
      (do
        (async/go
          (try
            (async/>! channel responses)
            (catch Exception ex
              (e.log/log "put done responses" (ex-message ex)))))
        (dissoc this id))
      this)
    this))

(m/=> process-message [:=> [:cat e.s.nrepl/?Manager e.s.nrepl/?Message] e.s.nrepl/?Manager])
(defn process-message
  [this
   msg]
  (-> this
      (add-message msg)
      (put-done-responses msg)))

(m/=> register-message [:=> [:cat e.s.nrepl/?Manager e.s.nrepl/?Message] e.s.nrepl/?Manager])
(defn register-message
  [this
   msg]
  (let [id (:id msg)]
    (if (and id
             (int? id))
      (assoc this id {:channel (async/chan)
                      :responses []})
      this)))
