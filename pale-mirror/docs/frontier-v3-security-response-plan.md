# Settlement security response allocation

Scope accepted 2026-11-02: multiple observed threats, bounded response crews,
retained patrol coverage, explicit intercept/search/return. Existing perception
radius/LOS, hostility, equipment, body ownership and native hit protocol remain.
No new navigation engine, ambient combat simulation or communication simulation.

## Owners and decisions

- SecurityState owns independently keyed home/CombatTarget contacts, monotonically
  versioned native observations, and exact duty-to-contact response allocations.
  Hostility and pending non-replayable strikes remain independent.
- SecurityDispatch is a bounded pure settlement policy. Prefer retained responders;
  fill a first slot per fresh threat before reinforcement; rank unassigned guards
  by canonical distance with deterministic ties. At most half available armed guards
  respond (at least one when there is only one), at most two per threat. Others
  retain their patrol obligations. Stale sightings retain existing investigators
  only, never recruit guards who have not begun responding.
- Security review is one coalesced owner-addressed action per affected settlement.
  Observations/end/guard changes wake it. Active contact review supplies expiry and
  availability reconsideration; idle settlements have no per-tick security poll.
  Allocation never performs routing or acquires actor execution.
- Guard missions consume their exact allocation. Explicit stages are PATROL,
  WATCH, INTERCEPT, SEARCH, RETURN. Losing allocation or invalidating a protected
  post uses the existing safe UAE interruption checkpoint.
- Search visits the last known area with at most four short geometry-validated
  probes and pauses, bounded by the existing400tick last-observation deadline.
  Reacquisition returns to interception. Exhaustion returns to the retained valid
  post or selects a replacement. A search never follows an unseen current pose.
- Native sensors publish multiple bounded LOS contacts independently. Only assigned
  responders may prepare a hit; the actual hitter still needs native LOS/reach,
  exact body/weapon/target incarnation and the common operation journal.

## Integration

Change the disposable-world schema explicitly; no old-world compatibility path.
Persist/validate contact keys, allocation identities and mission stages. Retire a
single contact without touching other threats; stale end cannot remove a later
observation. Pending hits must still settle after allocation/contact loss. Duty
retirement removes its response relationship. Diagnostics expose per-target crews,
posted guards and actual lifecycle stage, keeping old single-contact UI fields only
as read-only summary projections if existing consumers need them.

## Verification and delivery

Static trace through sensor -> observation -> allocation -> interruption -> mission
-> movement -> hit/expiry -> return -> retirement. Focused cases: two threats,
response limit/reserve, stable allocations, unavailable/retired guard, stale/dead
contact, search completion/reacquisition, stale posts, pending hit and snapshot/WAL
recovery. Native one-player scenario must demonstrate bounded responders with
remaining patrol, real harm/return and restart; multiple-enemy allocation is covered
at canonical boundary unless a valid native fixture is available. Reuse applicable
unchanged operation/equipment gates. Build a clean detached candidate; normal stop,
install and verify through pack-owned scripts. Deploy to the authorized manual
server; record limits rather than infer full M2/M3 from endpoints.
