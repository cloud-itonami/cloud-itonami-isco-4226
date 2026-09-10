(ns reception.governor
  "ReceptionGovernor — the independent safety/traceability layer for the
  ISCO-08 4226 community reception actor (itonami actor pattern,
  ADR-2607011000 / CLAUDE.md Actors section). Modeled on
  cloud-itonami-isco-4311's bookkeeping.governor. The reception-specific
  twist: the governor checks slot overlap DETERMINISTICALLY against the
  committed calendar — the advisor's claim that a slot is free is never
  trusted.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance — the request's business must be registered.
    2. authorized op     — the proposal's :op must be in
                           `reception.operations/catalog`. Deny-by-default:
                           the desk performs the operations it was
                           authorized to perform and no others. An advisor
                           cannot widen its own authority by naming a new
                           one, and a human approver cannot sign off an
                           operation the desk does not have.
    3. no-actuation      — proposal :effect must be :propose.
    4. valid slot        — an op that `operations/requires-slot?` must have
                           integer :start < :end (bad time arithmetic is
                           not approvable).
    5. no double-booking — the proposed slot must not overlap ANY
                           committed appointment for the same client
                           resource. Two parties cannot hold the same
                           chair at the same time; a human approver
                           cannot approve their way past a calendar
                           collision.
  ESCALATION invariants (:escalate? true, human sign-off):
    6. an `operations/external-send?` op (visible to a counterparty).
    7. low confidence (< `confidence-floor`).

  Every op-dependent decision above reads `reception.operations` — there
  is no second list of operations kept in this namespace."
  (:require [reception.operations :as operations]
            [reception.store :as store]))

(def confidence-floor 0.6)

(defn- overlaps? [{s1 :start e1 :end} {s2 :start e2 :end}]
  (< (max s1 s2) (min e1 e2)))

(defn- hard-violations [{:keys [request proposal]} client-record store]
  (let [{:keys [op slot]} proposal
        authorized? (operations/authorized? op)
        ;; Only an authorized op can require a slot, so an unauthorized op
        ;; is refused on authority and never reaches the calendar checks.
        booking? (operations/requires-slot? op)
        {:keys [resource start end]} slot
        valid-times? (and (integer? start) (integer? end) (< start end))
        collision (when (and booking? valid-times? resource)
                    (first (filter #(overlaps? slot %)
                                   (store/appointments-of store (:client-id request) resource))))]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not authorized?)
      (conj {:rule :unauthorized-operation
             :detail (str "未認可の operation: " (pr-str op)
                          "（この受付が行えるのは "
                          (pr-str (vec (sort (map name operations/authorized-ops))))
                          " のみ）")})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (and booking? (not valid-times?))
      (conj {:rule :invalid-slot
             :detail (str "slot が不正: start " start " / end " end)})

      (and booking? valid-times? (some? collision))
      (conj {:rule :double-booking
             :detail (str "リソース " resource " の "
                          (:start collision) "-" (:end collision)
                          " と重複（二重予約は承認不可）")}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `reception.store/Store`. Pure — never mutates
  the store. Returns
  `{:ok? bool :violations [...] :confidence n :hard? bool :escalate? bool}`."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        hard (hard-violations {:request request :proposal proposal}
                              client-record store)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        risky-op? (operations/external-send? (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))
