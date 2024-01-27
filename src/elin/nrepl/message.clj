(ns elin.nrepl.message
  (:require
   [elin.schema.nrepl :as e.s.nrepl]
   [elin.util.schema :as e.u.schema]
   [malli.core :as m]))

(def ^:private ?Messages
  [:sequential e.s.nrepl/?Message])

(def ^:private array-key-set
  #{"status" "sessions" "classpath"})

(m/=> bytes->str [:=> [:cat any?] e.u.schema/?NotBytes])
(defn- bytes->str
  [x]
  (if (bytes? x)
    (String. (bytes x))
    x))

(m/=> format-message [:=> [:cat [:map-of string? any?]] e.s.nrepl/?Message])
(defn format-message
  [msg]
  (reduce-kv
   (fn [accm k v]
     (assoc accm
            (keyword k)
            (cond
              (contains? array-key-set k)
              (mapv bytes->str v)

              (map? v)
              (format-message v)

              :else
              (bytes->str v))))
   {}
   msg))

(m/=> merge-messages [:=> [:cat ?Messages] e.s.nrepl/?Message])
(defn merge-messages
  [messages]
  (let [array-keys (map keyword array-key-set)
        array-res (reduce (fn [accm k]
                            (if-let [arr (some->> (keep k messages)
                                                  (seq)
                                                  (apply concat)
                                                  (distinct))]
                              (assoc accm k arr)
                              accm))
                          {} array-keys)]
    (->> (map #(apply dissoc % array-keys) messages)
         (apply merge array-res))))

(m/=> update-messages [:=> [:cat keyword? fn? ?Messages] ?Messages])
(defn update-messages
  [k f messages]
  (map
   (fn [res]
     (cond-> res
       (contains? res k)
       (update k f)))
   messages))
