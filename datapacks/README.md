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
dimension for Frontier v3 tests. It must be installed before a world is first
created, because dimensions are level-stem data rather than a runtime terrain
edit. Select the world through `scripts/install-server.sh --level-name NAME`;
the installer then copies the pack into `NAME/datapacks/` before first boot. A
v3-enabled launcher rejects a selected world that lacks this definition rather
than changing the ordinary overworld.
