# cloud-itonami-isco-4226

**Community Reception Desk** — the ISCO-08 4226 (Receptionists,
general) actor, an ISCO **Wave 0 (cognitive substrate)** occupation
per ADR-2607121000: pure-cognitive work, the LLM-first wave, no
robotics gate.

**Maturity: `:implemented`** — ReceptionAdvisor ⊣ ReceptionGovernor as
a langgraph StateGraph (`intake → advise → govern → decide →
commit/hold`, human-approval interrupt), modeled on
cloud-itonami-isco-4311's bookkeeping actor. 22 tests / 55 assertions
green.

The reception-specific HARD invariant: **no double-booking** — the
governor checks the proposed slot against the COMMITTED calendar
deterministically (adjacent slots at the boundary are fine, overlap is
not) and holds any collision at any confidence; the advisor's claim
that a slot is free is never trusted. Bad time arithmetic
(start ≥ end) is equally unapprovable. Also HARD: unregistered
business, `:effect` other than `:propose`.

**What the desk may do at all** is `src/reception/operations.cljk` — the
authority catalog, and the only list of operations in the repo. `:op` is
deny-by-default: an operation absent from the catalog is refused HARD, so
neither an advisor naming a new one nor a human approver can widen the
desk's authority. This closed a real hole — before the catalog existed the
governor enumerated only which ops need a slot and which ops escalate, so
any other keyword fell through every rule and committed a record. Measured
on the code as it stood: a proposal of `:op :issue-refund` at confidence
0.95 reached `:disposition :commit` and wrote a refund record to the store,
with `:violations []`. The same path is how `:op :unknown` — what
`advisor/parse-proposal` emits on an unparseable LLM response — arrived as
a low-confidence *escalation* a human could sign off. Both now hold.

The catalog is load-bearing rather than descriptive: the governor derives
authorization, the slot requirement, and escalation from it. Flipping
`:external-send?` on `:send-confirmation` turns off that operation's
human sign-off and reddens the pre-existing escalation tests
(`scripts/maturity-loop/mutations.edn`, `:reception/external-send-flipped`).

Escalations (always human sign-off): any `:external-send?` operation —
today `:send-confirmation` — and low confidence (< 0.6).

AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
