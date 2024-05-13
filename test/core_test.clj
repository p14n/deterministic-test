(ns core-test
  (:require [clojure.test :refer [deftest is testing]]
            [core :as c]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.results :as tcr]
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

(defrecord Result [pass data]
  tcr/Result
  (pass? [_] pass)
  (result-data [_] data))


(defspec run-handlers-in-expected-order
  10
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

(defn ensure-event-in-context [handler-execution-context event handlers seed]
  (if (contains? handler-execution-context event)
    handler-execution-context
    (assoc handler-execution-context event
           ;handlers
           (shuffle seed handlers))))

(defn first-handler-from-context [handler-execution-context event]
  (let [handlers (handler-execution-context event)]
    [(first handlers) (assoc handler-execution-context event (rest handlers))]))


(defspec run-handlers-in-random-order
  (prop/for-all [verified? gen/boolean
                 blacklisted? gen/boolean
                 events-shuffle-seed gen/large-integer
                 handlers-shuffle-seed gen/large-integer]

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
                         handler-execution-context {}]
                    (let [first-event (first events)
                          handler-ec-with-event (ensure-event-in-context handler-execution-context first-event all-handlers handlers-shuffle-seed)
                          [handler updated-ec] (first-handler-from-context handler-ec-with-event first-event)
                          event-fully-processed? (nil? (first (first-handler-from-context updated-ec first-event)))
                          new-events (->> [(handler first-event) (when (not event-fully-processed?) first-event)]
                                          (concat (rest events))
                                          (remove nil?)
                                          (shuffle events-shuffle-seed))]
                      (if (empty? new-events)
                        (let [final-processed-events (conj processed-events first-event)]
                          (->Result (= (expected-customer-email-status final-processed-events) (:email-status @c/customer))
                                    {:expected-email-status (expected-customer-email-status final-processed-events)
                                     :actual-email-status (:email-status @c/customer)
                                     :final-processed-events final-processed-events}))
                        (recur new-events (if event-fully-processed?
                                            (conj processed-events first-event)
                                            processed-events)
                               updated-ec)))))))
