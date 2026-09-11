(ns reception.store
  "SSoT for the ISCO-08 4226 community reception actor. Store is a
  protocol injected into the `reception.actor` StateGraph — `MemStore`
  is the default, deterministic, zero-dep backend (itonami actor
  pattern, ADR-2607011000 / CLAUDE.md Actors section). Modeled on
  cloud-itonami-isco-4311's bookkeeping.store.

  Domain:

    client       — a registered business the desk serves
                   (:client-id, :name)
    appointment  — a BOOKED slot {:client-id :resource :start :end
                   :party} (integer minutes-of-day for :start/:end).
                   Written ONLY via book-appointment! from the actor's
                   :commit node — the calendar is the SSoT the governor
                   checks double-booking against.
    record       — a committed operating record (booking, message,
                   sent confirmation) — written ONLY via commit-record!.
    ledger       — append-only audit trail of every proposal/verdict/
                   disposition, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (appointments-of [s client-id resource])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (book-appointment! [s appointment])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (appointments-of [_ client-id resource]
    (filter #(and (= client-id (:client-id %)) (= resource (:resource %)))
            (:appointments @a)))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (book-appointment! [s appointment]
    (swap! a update :appointments (fnil conj []) appointment) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :appointments []
                                    :records [] :ledger []}
                                   seed)))))
