# Runtime debug toolkit

The toolkit is an operator-only view and maintenance surface for real playthroughs. It does not bypass domain ownership:
manual binding uses the production registration command, infection produces normal domain events, and visual markers are
per-player particles/tooltips rather than blocks, entities, or item mutations.

## Safe setup workflow

1. Before approaching a candidate village, run:

   ```text
   /pale_mirror debug discovery manual
   /pale_mirror debug markers on
   ```

   Observation continues in MANUAL mode, but Pale Mirror cannot choose a village automatically.

2. Remain near a village with at least two villagers and a bell/bed for 400 continuously loaded ticks (20 seconds).

3. Inspect evidence:

   ```text
   /pale_mirror debug settlements list
   /pale_mirror debug settlements nearest
   /pale_mirror debug verify
   ```

   Bind only a candidate marked `STRONG/CURRENT` and `eligible=true`:

   ```text
   /pale_mirror debug settlements bind-nearest
   /pale_mirror debug settlements bind pale_mirror:<observed-id>
   ```

4. Inspect the resulting causal aggregate:

   ```text
   /pale_mirror debug region status
   /pale_mirror explain settlement pale_mirror:ironhill_community
   /pale_mirror timeline pale_mirror:mine17
   ```

5. For an operator-paced scenario, use the normal command pipeline:

   ```text
   /pale_mirror debug trigger infection
   /pale_mirror simulate step 1
   /pale_mirror scenario list
   ```

`AUTO` restores natural first-candidate binding. Discovery mode and marker subscriptions are runtime-only and return to
`AUTO`/off after restart; they are not world progression.

## Visual language

- Green particles: bounds of a read-only observed village.
- White particles: bounds of a PM-managed physical object.
- Flame column: the next planned campaign mine location, before materialization.
- `F3+H`: advanced item tooltips show the registry owner namespace. Excluded Crimson/Spore content is red; an item
  reserved by a PM atomic resource transfer is gold.

Rendering is sent only to operators who enabled markers, is distance-bounded to 256 blocks, and creates no persistent
world object.

## Safe reset after an accidental binding

Reset is intentionally narrow. It is available only while the campaign is wholly abstract and no materialization job,
test mine, depot, transfer, effect lease, combat actor, projectile, refugee camp, quarantine record, or development intent
could leave physical provenance behind.

```text
/pale_mirror debug reset preview
/pale_mirror debug reset confirm <one-time-token>
```

Preview does not change state. The token is tied to one operator and expires after 60 seconds. Confirm rechecks every
precondition. A successful reset clears PM canonical/projection state, touches no Minecraft or foreign-mod state, and
switches discovery to MANUAL so the same village is not immediately rebound. If reset is rejected, inspect the listed
blockers; after physical work starts, use the relevant reconciliation/cleanup workflow instead of deleting provenance.

## Command reference

```text
/pale_mirror debug discovery status|auto|manual
/pale_mirror debug settlements list|nearest|bind-nearest
/pale_mirror debug settlements bind <namespace:id>
/pale_mirror debug region status
/pale_mirror debug verify
/pale_mirror debug trigger infection
/pale_mirror debug markers status|on|off
/pale_mirror debug reset preview
/pale_mirror debug reset confirm <token>
```

World-object arguments now use Minecraft resource-location parsing, so identifiers such as
`pale_mirror:ironhill_community` do not need quotes.
