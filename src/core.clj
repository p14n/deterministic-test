(ns core
  (:require [malli.core :as m]))

; Onboarding
; -> Create customer (address, email, phone, name)
; -> send verification email (set status to pending)
;   -> receive verification email (set status to active if not blocked)
; -> send email blacklist check
;   -> receive email blacklist check (set status to blocked)

; Events
(def CreateCustomerRequestedEvent
  [:map {:closed true}
   [:type [:enum :create-customer-requested]]
   [:customer-id :string]
   [:email :string]])

(def EmailVerificationSentEvent
  [:map {:closed true}
   [:type [:enum :email-verification-sent]]
   [:customer-id :string]])

(def EmailVerificationCompletedEvent
  [:map {:closed true}
   [:type [:enum :email-verification-sent]]
   [:customer-id :string]
   [:verified :boolean]])

(def EmailBlacklistCheckSentEvent
  [:map {:closed true}
   [:type [:enum :email-verification-sent]]
   [:customer-id :string]])

(def EmailBlacklistCheckCompletedEvent
  [:map {:closed true}
   [:type [:enum :email-verification-sent]]
   [:customer-id :string]
   [:blacklisted :boolean]])

(def AnyEvent
  [:or
   CreateCustomerRequestedEvent
   EmailVerificationSentEvent
   EmailVerificationCompletedEvent
   EmailBlacklistCheckSentEvent
   EmailBlacklistCheckCompletedEvent])

; Handlers

(defn verification-email-sender
  [{:keys [type customer-id]} _]
  (case type
    :create-customer-requested
    {:type :email-verification-sent
     :customer-id customer-id}
    nil))

(m/=> verification-email-sender
      [:function
       [:=> [:cat AnyEvent :any] EmailVerificationSentEvent]])

(defn email-blacklist-check-sender
  [{:keys [type customer-id]} _]
  (case type
    :create-customer-requested
    {:type :email-blacklist-sent
     :customer-id customer-id}
    nil))

(m/=> email-blacklist-check-sender
      [:function
       [:=> [:cat AnyEvent :any] EmailBlacklistCheckSentEvent]])

(defn email-verifier
  [{:keys [type customer-id]} data]
  (case type
    :email-verification-sent
    {:type :email-verification-completed
     :customer-id customer-id
     :verified data}
    nil))

(m/=> email-verifier
      [:function
       [:=> [:cat AnyEvent :boolean] EmailVerificationCompletedEvent]])

(defn email-blacklist-checker
  [{:keys [type customer-id]} data]
  (case type
    :email-verification-sent
    {:type :email-blacklist-completed
     :customer-id customer-id
     :blacklisted data}
    nil))

(m/=> email-blacklist-checker
      [:function
       [:=> [:cat AnyEvent :boolean] EmailBlacklistCheckCompletedEvent]])

; Repository

(def customer (atom {}))

(defn customer-repository
  [{:keys [type customer-id email verified blacklisted]} _]
  (case type
    :create-customer-requested
    (swap! customer merge {:customer-id customer-id :email email})
    :email-verification-sent
    (swap! customer assoc :email-status "pending")
    :email-verification-completed
    (when (and verified
               (-> @customer :email-status (not= "blocked")))
      (swap! customer assoc :email-status "active"))
    :email-blacklist-completed
    (when blacklisted
      (swap! customer assoc :email-status "blocked"))
    nil)
  nil)

(m/=> customer-repository
      [:function
       [:=> [:cat AnyEvent :any] :any]])

