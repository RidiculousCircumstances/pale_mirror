# Harvester visual-tool family

These scripts and Node programs are the sole checked-in generator and
review-tool family for the Harvester assets under
`pale-mirror-visuals/src/main/{blockbench,blender,resources}/harvester/`.
They produce or validate asset-facing, noncanonical evidence only. Captures,
external model output and review packages remain ignored build/task-private
artifacts; they are not committed beside source tools.

The root Gradle guardrails execute the deterministic contour, compatibility,
bakeoff, overlay and blind-review contracts through these paths.
