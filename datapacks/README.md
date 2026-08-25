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

`pale-mirror-graybox` owns the disposable `pale_mirror:frontier_graybox`
dimension for source-parity simulation tests. It must be installed before a
world is first created, because dimensions are level-stem data rather than a
runtime terrain edit. The normal server installer copies it into
`world/datapacks/`; the Pale Mirror mod rejects graybox activation if it is
absent instead of changing the ordinary overworld.
