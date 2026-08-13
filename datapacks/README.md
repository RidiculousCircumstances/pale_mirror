# Built-in datapacks

This directory will contain versioned datapacks only where a mod's documented data
format is required. IDA structure-set overrides are intentionally separate from
Structurify. No datapack is added until its exact registry IDs and upstream JSON
have been extracted from the pinned JARs.

`far-frontier-spore-zones` owns Spore structure-set spacing. It is deliberately
separate from Structurify and requires the pinned Spore artifact.

`idas-optional-integration-quarantine` and
`integrated-villages-optional-integration-quarantine` prevent pinned structure
content from selecting entities or items supplied only by absent optional mods.
They change world generation directly; they do not hide errors with log filters.
