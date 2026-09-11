(ns reception.actor
  "ReceptionActor — the ISCO-08 4226 community reception actor as a
  `langgraph.graph/state-graph` (ADR-2607011000 / CLAUDE.md Actors
  section). One graph run = one reception operation request
  (intake → advise → govern → decide → commit/hold, human-approval
  interrupt for escalations). Modeled on cloud-itonami-isco-4311's
  bookkeeping.actor.

  The unconditional invariant: the ReceptionAdvisor can never directly
  book a slot or send a confirmation the ReceptionGovernor refuses —
  every book-appointment!/commit-record! call is gated behind
  `:decide`."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [reception.advisor :as advisor]
            [reception.governor :as governor]
            [reception.store :as store]))

(defn build-graph
  "Build a compiled ReceptionActor graph. `store` implements
  `reception.store/Store`. `advisor` implements
  `reception.advisor/Advisor` (defaults to `mock-advisor`)."
  [{:keys [store advisor checkpointer]
    :or {advisor (advisor/mock-advisor)
         checkpointer (cp/mem-checkpointer)}}]
  (-> (g/state-graph
       {:channels
        {:request     {:default nil}
         :context     {:default nil}
         :proposal    {:default nil}
         :verdict     {:default nil}
         :disposition {:default nil}
         :record      {:default nil}
         :audit       {:reducer into :default []}}})
      (g/add-node :intake (fn [s] s))
      (g/add-node :advise
                   (fn [{:keys [request]}]
                     (let [p (advisor/-advise advisor store request)]
                       {:proposal p
                        :audit [{:node :advise :request request :proposal p}]})))
      (g/add-node :govern
                   (fn [{:keys [request context proposal]}]
                     (let [v (governor/check request context proposal store)]
                       {:verdict v
                        :audit [{:node :govern :verdict v}]})))
      (g/add-node :decide
                   (fn [{:keys [verdict]}]
                     {:disposition (cond
                                     (:hard? verdict) :hold
                                     (:escalate? verdict) :request-approval
                                     :else :commit)}))
      (g/add-node :request-approval (fn [s] s))
      (g/add-node :commit
                   (fn [{:keys [request proposal]}]
                     (let [record {:client-id (:client-id request)
                                    :op (:op proposal)
                                    :payload proposal}]
                       (when (= :schedule-appointment (:op proposal))
                         (store/book-appointment!
                          store (assoc (:slot proposal)
                                       :client-id (:client-id request)
                                       :party (:party proposal))))
                       (store/commit-record! store record)
                       (store/append-ledger! store {:disposition :commit :record record})
                       {:record record
                        :audit [{:node :commit :record record}]})))
      (g/add-node :hold
                   (fn [{:keys [verdict]}]
                     (store/append-ledger! store {:disposition :hold :verdict verdict})
                     {:audit [{:node :hold :verdict verdict}]}))
      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)
      (g/add-conditional-edges
       :decide
       (fn [{:keys [disposition]}]
         (case disposition
           :commit :commit
           :request-approval :request-approval
           :hold)))
      (g/add-edge :request-approval :commit)
      (g/set-finish-point :commit)
      (g/set-finish-point :hold)
      (g/compile-graph {:checkpointer checkpointer
                         :interrupt-before #{:request-approval}})))

(defn run-request!
  [graph request context thread-id]
  (g/run* graph {:request request :context context} {:thread-id thread-id}))

(defn approve!
  [graph thread-id]
  (g/run* graph nil {:thread-id thread-id :resume? true}))
