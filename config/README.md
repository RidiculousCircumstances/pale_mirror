# Configuration policy

Only generated-and-reviewed configuration files belong here. Do not create guessed
schemas. The first successful dedicated-server boot is used to discover exact keys;
every later override records its mod version, observed default and test evidence.

Spore `2.2.0j` is the exception by necessity: its generated startup schema is
committed in full because NeoForge reads it before a world exists. Only documented
global-containment values differ from the generated default; the exact policy and
test evidence are in `docs/spore-integration.md`.
