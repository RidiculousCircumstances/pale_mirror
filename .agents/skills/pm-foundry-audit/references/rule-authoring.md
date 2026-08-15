# Foundry rule contract

Use this checklist for every new or materially changed rule.

## Identity and scope

- Use a stable dotted `ruleId` describing the invariant, not the current bug.
- Declare the valid phases and the semantic target kind.
- Derive findings from immutable catalog/plan data plus already loaded chunks.
- Never ticket, load, mutate, or repair a chunk during an audit.
- Bound findings per rule and total work per report.

## Evidence

Every finding must include target ID, dimension, exact block position, concise
message, and actionable remediation. Every rule must emit a numeric metric even
when it passes. Preserve deterministic ordering in exports.

## Severity

- `BLOCKER`: catalog cannot safely publish or the authored causal route is
  impossible.
- `ERROR`: loaded materialization violates a required invariant or gameplay is
  blocked.
- `WARNING`: suspicious, incomplete, unloaded, or visually reviewable state
  that does not prove failure.
- `INFO`: bounded diagnostic context only.

Do not promote taste or style preferences to structural errors.

## Verification

- Add a positive case and a negative or recovery GameTest.
- Test unloaded-chunk behavior when runtime observation is involved.
- Confirm a player edit becomes a conflict/finding rather than repair authority.
- Verify the report remains stable across repeated runs and restart where the
  rule claims `RELOADED` coverage.
