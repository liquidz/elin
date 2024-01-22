(ns elin.nrepl.response
  (:require
   [clojure.core.async :as async]
   [elin.log :as e.log]
   [elin.nrepl.message :as e.n.message]
   [elin.util.schema :as e.u.schema]
   [malli.core :as m])
  (:import
   clojure.core.async.impl.channels.ManyToManyChannel))

(def ?Manager
  [:map-of int? [:map
                 [:responses [:sequential e.n.message/?Message]]
                 [:channel (e.u.schema/?instance ManyToManyChannel)]]])

(m/=> done? [:=> [:cat e.n.message/?Message] boolean?])
(defn- done?
  [msg]
  (boolean
   (some #(= % "done")
         (:status msg))))

(m/=> add-message [:=> [:cat  ?Manager e.n.message/?Message] ?Manager])
(defn- add-message
  [this
   {:as msg :keys [id]}]
  (if (and id
           (int? id))
    (update-in this [id :responses] conj msg)
    this))

(m/=> put-done-responses [:=> [:cat ?Manager e.n.message/?Message] ?Manager])
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

(m/=> process-message [:=> [:cat ?Manager e.n.message/?Message] ?Manager])
(defn process-message
  [this
   msg]
  (-> this
      (add-message msg)
      (put-done-responses msg)))

(m/=> register-message [:=> [:cat ?Manager e.n.message/?Message] ?Manager])
(defn register-message
  [this
   msg]
  (let [id (:id msg)]
    (if (and id
             (int? id))
      (assoc this id {:channel (async/chan)
                      :responses []})
      this)))
