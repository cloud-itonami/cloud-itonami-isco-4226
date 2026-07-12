# cloud-itonami-isco-4226

**Community Reception Desk** — the ISCO-08 4226 (Receptionists,
general) actor, an ISCO **Wave 0 (cognitive substrate)** occupation
per ADR-2607121000: pure-cognitive work, the LLM-first wave, no
robotics gate.

**Maturity: `:implemented`** — ReceptionAdvisor ⊣ ReceptionGovernor as
a langgraph StateGraph (`intake → advise → govern → decide →
commit/hold`, human-approval interrupt), modeled on
cloud-itonami-isco-4311's bookkeeping actor. 12 tests / 25 assertions
green.

The reception-specific HARD invariant: **no double-booking** — the
governor checks the proposed slot against the COMMITTED calendar
deterministically (adjacent slots at the boundary are fine, overlap is
not) and holds any collision at any confidence; the advisor's claim
that a slot is free is never trusted. Bad time arithmetic
(start ≥ end) is equally unapprovable. Also HARD: unregistered
business, `:effect` other than `:propose`. Escalations (always human
sign-off): `:send-confirmation` (external-send), low confidence (< 0.6).

AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
