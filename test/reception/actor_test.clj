(ns reception.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [reception.actor :as actor]
            [reception.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kawa Clinic"})
    st))

(deftest commits-a-booking-and-writes-the-calendar
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :schedule-appointment :stake :low
                 :slot {:resource "room-1" :start 600 :end 630} :party "Tanaka"}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/appointments-of st "client-1" "room-1"))))))

(deftest holds-a-double-booking-without-touching-the-calendar
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        first-req {:client-id "client-1" :op :schedule-appointment :stake :low
                   :slot {:resource "room-1" :start 600 :end 630} :party "A"}
        second-req {:client-id "client-1" :op :schedule-appointment :stake :low
                    :slot {:resource "room-1" :start 615 :end 645} :party "B"}]
    (actor/run-request! graph first-req {} "thread-2a")
    (let [result (actor/run-request! graph second-req {} "thread-2b")]
      (is (= :hold (:disposition (:state result))))
      (is (= 1 (count (store/appointments-of st "client-1" "room-1"))))
      (is (= 1 (count (store/records-of st "client-1")))))))

(deftest interrupts-then-sends-confirmation-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        ;; sending a confirmation is external-send: always escalates
        request {:client-id "client-1" :op :send-confirmation :stake :medium
                 :party "Tanaka"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
