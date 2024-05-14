(ns core-test
  (:require [core :as c]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop])
  (:import (java.util Random Collections ArrayList)))


(defn expected-customer-email-status [processed-events]
  (let [blocked? (some #(= (:blacklisted %) true) processed-events)
        verified? (some #(= (:verified %) true) processed-events)]
    (if blocked?
      "blocked"
      (if verified?
        "active"
        "pending"))))

(defn wrap-handler [handler data]
  (fn [event]
    (handler event data)))


(defspec run-handlers-in-expected-order
  20
  (prop/for-all
   [verified? gen/boolean
    blacklisted? gen/boolean]
   (let [cmd {:type :create-customer-requested
              :customer-id "123"
              :email "nobody@nowhere.com"}
         all-handlers [(wrap-handler c/email-blacklist-check-sender nil)
                       (wrap-handler c/verification-email-sender nil)
                       (wrap-handler c/email-verifier verified?)
                       (wrap-handler c/email-blacklist-checker blacklisted?)
                       (wrap-handler c/customer-repository nil)]]
     (reset! c/customer {})
     (loop [events [cmd]
            processed-events []]
       (let [new-events (->> all-handlers
                             (mapcat (fn [handler]
                                       (map (fn [event]
                                              (handler event))
                                            events)))
                             (remove nil?))
             new-processed-events (concat processed-events events)]
         (if (empty? new-events)
           (= (expected-customer-email-status new-processed-events) (:email-status @c/customer))
           (recur new-events new-processed-events)))))))


(defn shuffle [seed coll]
  (let [r (Random. seed)
        a (ArrayList. coll)]
    (Collections/shuffle a r)
    (vec (.toArray a))))

(defn ensure-event-in-tracker [handler-execution-tracker event handlers seed]
  (if (contains? handler-execution-tracker event)
    handler-execution-tracker
    (assoc handler-execution-tracker event
           (shuffle seed handlers))))

(defn first-handler-from-tracker [handler-execution-tracker event handlers seed]
  (let [updated-tracker (ensure-event-in-tracker handler-execution-tracker event handlers seed)
        handlers (updated-tracker event)]
    [(first handlers)
     (assoc updated-tracker event (rest handlers))
     (empty? (rest handlers))]))


(defn run-handlers-in-random-order
  [verified? blacklisted? events-shuffle-seed handlers-shuffle-seed]
  (let [cmd {:type :create-customer-requested
             :customer-id "123"
             :email "nobody@nowhere.com"}
        all-handlers [(wrap-handler c/email-blacklist-check-sender nil)
                      (wrap-handler c/verification-email-sender nil)
                      (wrap-handler c/email-verifier verified?)
                      (wrap-handler c/email-blacklist-checker blacklisted?)
                      (wrap-handler c/customer-repository nil)]]
    (reset! c/customer {})
    (loop [events [cmd]
           processed-events []
           handler-execution-tracker {}]
      (let [first-event (first events)
            [handler updated-tracker event-fully-processed?] (first-handler-from-tracker
                                                              handler-execution-tracker
                                                              first-event
                                                              all-handlers
                                                              handlers-shuffle-seed)
            new-events (->> (when (not event-fully-processed?) first-event)
                            (conj [(handler first-event)])
                            (concat (rest events))
                            (remove nil?)
                            (shuffle events-shuffle-seed))]
        (if (empty? new-events)
          (let [final-processed-events (conj processed-events first-event)
                expected-email-status (expected-customer-email-status final-processed-events)
                actual-email-status (:email-status @c/customer)]
            {:pass (= expected-email-status actual-email-status)
             :expected-email-status expected-email-status
             :actual-email-status actual-email-status
             :final-processed-events final-processed-events})
          (recur new-events (if event-fully-processed?
                              (conj processed-events first-event)
                              processed-events)
                 updated-tracker))))))

(defspec run-handlers-in-random-order-spec
  (prop/for-all [verified? gen/boolean
                 blacklisted? gen/boolean
                 events-shuffle-seed gen/large-integer
                 handlers-shuffle-seed gen/large-integer]
                (-> (run-handlers-in-random-order verified? blacklisted? events-shuffle-seed handlers-shuffle-seed)
                    :pass)))
                
