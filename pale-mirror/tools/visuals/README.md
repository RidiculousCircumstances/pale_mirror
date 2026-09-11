# Visual asset tools

This directory owns checked-in tools whose only authority is reproducible
Pale Mirror Visuals asset generation or review. They never mutate canonical
Frontier state, Minecraft worlds or pack payload metadata.

`harvester/` contains the complete Harvester family: source-sheet conversion,
trace and overlay evidence, blinded review packaging, Blockbench compatibility
exports and visual-audit orchestration. Its commands resolve the Pale Mirror
project root internally, so they are invoked from `pale-mirror/` using their
repository-relative paths.
