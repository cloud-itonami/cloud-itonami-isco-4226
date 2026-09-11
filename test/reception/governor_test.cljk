(ns reception.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [reception.store :as store]
            [reception.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kawa Clinic"})
    (store/book-appointment! st {:client-id "client-1" :resource "room-1"
                                 :start 600 :end 630 :party "existing"})
    st))

(defn- booking [slot]
  {:op :schedule-appointment :effect :propose :slot slot :party "Tanaka"
   :confidence 0.9 :stake :low})

(deftest ok-on-free-slot
  (let [st (fresh-store)
        v (governor/check {:client-id "client-1"} {}
                          (booking {:resource "room-1" :start 700 :end 730}) st)]
    (is (:ok? v))))

(deftest ok-on-adjacent-slot-boundary
  (testing "end == next start is NOT an overlap"
    (let [st (fresh-store)
          v (governor/check {:client-id "client-1"} {}
                            (booking {:resource "room-1" :start 630 :end 660}) st)]
      (is (:ok? v)))))

(deftest ok-on-same-time-different-resource
  (let [st (fresh-store)
        v (governor/check {:client-id "client-1"} {}
                          (booking {:resource "room-2" :start 600 :end 630}) st)]
    (is (:ok? v))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "no-such-client"} {}
                          (booking {:resource "room-1" :start 700 :end 730}) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check {:client-id "client-1"} {}
                          (assoc (booking {:resource "room-1" :start 700 :end 730})
                                 :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-double-booking
  (testing "a calendar collision is not approvable at any confidence"
    (let [st (fresh-store)
          v (governor/check {:client-id "client-1"} {}
                            (assoc (booking {:resource "room-1" :start 615 :end 645})
                                   :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :double-booking (:rule %)) (:violations v))))))

(deftest hard-on-invalid-slot
  (let [st (fresh-store)
        v (governor/check {:client-id "client-1"} {}
                          (booking {:resource "room-1" :start 700 :end 700}) st)]
    (is (:hard? v))
    (is (some #(= :invalid-slot (:rule %)) (:violations v)))))

(deftest escalates-confirmation-send
  (let [st (fresh-store)
        v (governor/check {:client-id "client-1"} {}
                          {:op :send-confirmation :effect :propose
                           :confidence 0.9 :stake :medium} st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check {:client-id "client-1"} {}
                          {:op :take-message :effect :propose
                           :confidence 0.3 :stake :low} st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
