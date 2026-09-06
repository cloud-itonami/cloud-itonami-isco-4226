(ns reception.operations
  "The catalog of reception operations the ISCO-08 4226 community reception
  desk is AUTHORIZED to perform — the actor's authority boundary, and the
  SSoT the governor reads it from (itonami actor pattern, ADR-2607011000 /
  CLAUDE.md Actors section).

  Why this exists as its own component. The governor originally enumerated
  two things about an operation: which ones need a slot
  (:schedule-appointment) and which ones escalate (:send-confirmation).
  It never enumerated which ones are permitted AT ALL, so `:op` was
  allow-by-default — any keyword an advisor produced fell through every
  hard rule and, at a confidence above the floor, committed a record. The
  LLM advisor can produce such a keyword two ways: a parse failure emits
  `:op :unknown`, and a well-formed response can name any operation it
  likes. Deny-by-default on the operation itself is the missing invariant.

  This catalog is load-bearing, not descriptive. `reception.governor`
  derives all three of its op-dependent decisions from it — authorization,
  whether a slot must be checked, and whether the op escalates to a human.
  Removing an entry here refuses that operation; flipping :external-send?
  here changes what needs sign-off. There is no second list to keep in
  sync.

  Each entry:
    :requires-slot?  the proposal must carry a {:resource :start :end} slot,
                     and the governor checks it against the committed
                     calendar for double-booking.
    :external-send?  the operation is visible to a counterparty outside the
                     desk, so it always escalates for human sign-off.
    :description     what the desk is doing on the client's behalf.")

(def catalog
  "op keyword -> its authority record. See ns docstring: this is the
  authority list, and it is the only one."
  {:schedule-appointment
   {:requires-slot?  true
    :external-send?  false
    :description     "Book a slot on a client resource in the committed calendar."}

   :take-message
   {:requires-slot?  false
    :external-send?  false
    :description     "Record a message from a caller for the client to collect."}

   :send-confirmation
   {:requires-slot?  false
    :external-send?  true
    :description     "Send a confirmation to a counterparty on the client's behalf."}})

(def authorized-ops
  "The set of operations the desk may perform. Derived from `catalog` —
  do not maintain it by hand."
  (into #{} (keys catalog)))

(def escalating-ops
  "Operations that always need human sign-off because they are visible
  outside the desk. Derived from :external-send?."
  (into #{} (keep (fn [[op spec]] (when (:external-send? spec) op)) catalog)))

(defn spec
  "The authority record for `op`, or nil if the desk is not authorized
  to perform it."
  [op]
  (get catalog op))

(defn authorized?
  "Is `op` an operation this desk may perform at all? Deny-by-default:
  anything absent from the catalog — including nil and the `:unknown`
  that advisor/parse-proposal emits on a bad LLM response — is false."
  [op]
  (contains? catalog op))

(defn requires-slot?
  "Does `op` operate on the calendar, so that the governor must check the
  proposed slot against committed appointments? False for unauthorized
  ops — they are refused on authorization before any slot is considered."
  [op]
  (boolean (:requires-slot? (spec op))))

(defn external-send?
  "Is `op` visible to a counterparty outside the desk (and therefore
  always escalated to a human)?"
  [op]
  (boolean (:external-send? (spec op))))
