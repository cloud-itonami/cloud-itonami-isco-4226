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
    2. no-actuation      — proposal :effect must be :propose.
    3. valid slot        — a :schedule-appointment must have integer
                           :start < :end (bad time arithmetic is not
                           approvable).
    4. no double-booking — the proposed slot must not overlap ANY
                           committed appointment for the same client
                           resource. Two parties cannot hold the same
                           chair at the same time; a human approver
                           cannot approve their way past a calendar
                           collision.
  ESCALATION invariants (:escalate? true, human sign-off):
    5. :op :send-confirmation (external-send to a counterparty).
    6. low confidence (< `confidence-floor`)."
  (:require [reception.store :as store]))

(def confidence-floor 0.6)
(def ^:private escalating-ops #{:send-confirmation})

(defn- overlaps? [{s1 :start e1 :end} {s2 :start e2 :end}]
  (< (max s1 s2) (min e1 e2)))

(defn- hard-violations [{:keys [request proposal]} client-record store]
  (let [{:keys [op slot]} proposal
        booking? (= :schedule-appointment op)
        {:keys [resource start end]} slot
        valid-times? (and (integer? start) (integer? end) (< start end))
        collision (when (and booking? valid-times? resource)
                    (first (filter #(overlaps? slot %)
                                   (store/appointments-of store (:client-id request) resource))))]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

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
        risky-op? (contains? escalating-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))
