(ns reception.operations-test
  "The desk's authority boundary: which operations it may perform at all.

  Before `reception.operations` existed the governor enumerated only the
  operations that ESCALATE (:send-confirmation) and the ones that need a
  slot (:schedule-appointment). Every OTHER :op keyword — including one an
  LLM advisor invented — was allow-by-default: high confidence, no slot, no
  hard rule to trip, so `check` returned :ok? true and the actor committed a
  record for it. These tests pin the opposite: an op absent from the catalog
  is refused, and refused HARD (no confidence and no human approver can get
  past it)."
  (:require [clojure.test :refer [deftest is testing]]
            [reception.store :as store]
            [reception.actor :as actor]
            [reception.operations :as operations]
            [reception.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kawa Clinic"})
    st))

(defn- check [proposal]
  (governor/check {:client-id "client-1"} {} proposal (fresh-store)))

;; ---------------------------------------------------------------- catalog

(deftest catalog-is-the-authority-list
  (testing "the three operations a reception desk is authorized to perform"
    (is (= #{:schedule-appointment :take-message :send-confirmation}
           operations/authorized-ops)))
  (testing "authorized? answers from the catalog, not from a second list"
    (is (every? operations/authorized? operations/authorized-ops))
    (is (not (operations/authorized? :issue-refund)))
    (is (not (operations/authorized? :unknown)))
    (is (not (operations/authorized? nil)))))

(deftest catalog-drives-the-slot-requirement
  (is (operations/requires-slot? :schedule-appointment))
  (is (not (operations/requires-slot? :take-message)))
  (testing "an op outside the catalog requires nothing because it is refused first"
    (is (not (operations/requires-slot? :issue-refund)))))

(deftest catalog-drives-escalation
  (testing "escalation is derived from :external-send?, not a hand-kept set"
    (is (= #{:send-confirmation} operations/escalating-ops))
    (is (operations/external-send? :send-confirmation))
    (is (not (operations/external-send? :take-message)))))

;; ------------------------------------------------- the refusal being added

(deftest hard-on-operation-outside-the-catalog
  (testing "an op the desk was never authorized to perform is refused"
    (let [v (check {:op :issue-refund :effect :propose
                    :confidence 0.99 :stake :low})]
      (is (:hard? v))
      (is (not (:ok? v)))
      (is (some #(= :unauthorized-operation (:rule %)) (:violations v))))))

(deftest unauthorized-operation-is-not-approvable
  (testing "hard means hard: max confidence does not convert it to an escalation"
    (let [v (check {:op :wire-transfer :effect :propose
                    :confidence 1.0 :stake :low})]
      (is (:hard? v))
      (is (not (:escalate? v)))
      (is (not (:ok? v))))))

(deftest hard-on-the-llm-parse-failure-op
  (testing ":op :unknown is what advisor/parse-proposal emits on a bad LLM
            response. It used to arrive as a low-confidence ESCALATION, which
            a human approver could sign off into a committed record."
    (let [v (check {:op :unknown :effect :propose
                    :confidence 0.0 :stake :high})]
      (is (:hard? v))
      (is (not (:escalate? v))))))

(deftest missing-op-is-refused
  (testing "a proposal with no :op at all is not silently authorized"
    (let [v (check {:effect :propose :confidence 0.9 :stake :low})]
      (is (:hard? v))
      (is (some #(= :unauthorized-operation (:rule %)) (:violations v))))))

(deftest every-catalog-operation-clears-the-authorization-rule
  (testing "the new rule refuses only what is outside the catalog"
    (doseq [op operations/authorized-ops]
      (let [v (check {:op op :effect :propose :confidence 0.9 :stake :low
                      :slot (when (operations/requires-slot? op)
                              {:resource "room-1" :start 600 :end 630})})]
        (is (not (some #(= :unauthorized-operation (:rule %)) (:violations v)))
            (str op " is in the catalog and must clear the authorization rule"))))))

;; ------------------------------------------------------------ through the graph

(deftest actor-holds-an-unauthorized-operation-and-writes-nothing
  (testing "end to end: no record, no calendar write, and the hold is audited"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:client-id "client-1" :op :issue-refund :stake :low
                   :party "Tanaka"}
          result (actor/run-request! graph request {} "thread-unauth")]
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "client-1")))
      (is (= 1 (count (store/ledger st))))
      (is (= :hold (:disposition (first (store/ledger st))))))))

(deftest actor-never-interrupts-for-an-unauthorized-operation
  (testing "it must not reach :request-approval — a human must not be offered
            the chance to approve an operation the desk cannot perform"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:client-id "client-1" :op :unknown :stake :high
                   :party "Tanaka"}
          result (actor/run-request! graph request {} "thread-unauth-2")]
      (is (not= :interrupted (:status result)))
      (is (= :hold (:disposition (:state result)))))))
