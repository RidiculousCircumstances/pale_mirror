---
name: pm-test-server-ops
description: Build, publish, install, reset, start, restart, diagnose, and validate the Pale Mirror disposable test server and client pack. Use for server updates, new worlds or seeds, service status, live logs or crashes, packwiz or installer failures, hosted artifacts, client installer changes, or confirming that the actual server is running. Use pm-release-verification first when new code must be verified.
---

# PM Test Server Ops

Operate the test environment from discovered paths and real process evidence.
Do not confuse a successful shell command with a running Minecraft server.

## Discover the environment

1. Read `CONTINUITY.md` and `references/operations-map.md` completely.
2. Resolve the code repo with `git rev-parse --show-toplevel`.
3. Verify the sibling pack by locating `pack.toml`; normally it is
   `/home/rd/proj/minecraft`.
4. Resolve the runtime directory, service name, `level-name`, and world path
   from current config/processes. Never assume them from an old transcript.

## Update safely

1. Inspect current git state, service state, logs, and disk space.
2. Verify new artifacts with `pm-release-verification` before publishing.
3. Publish or install through the repository scripts in the operations map.
   Validate the pack index and hosted URLs before restart.
4. Update client installers only when client-required mods, configs, pack
   metadata, or installer logic changed. Server-only code does not require it.
5. The standing delivery policy is immediate deployment: after a verified
   implementation, publish the affected artifacts and restart this disposable
   test server unless the user explicitly requests source-only work. A
   fresh-world Visuals version bump also authorizes resetting the exact resolved
   test-world paths without a backup; never generalize this to another server.

## Reset a disposable test world

The current product decision is that this server is disposable. An explicit
reset request or the standing fresh-world deployment policy above requires no
backup prompt. Still stop
the service first, resolve and print the exact world directories, reject broad
or unresolved targets, and never delete through `$HOME`, `~`, globs, or an
empty variable. Start only after the exact reset completes.

## Prove the outcome

After start or restart, verify the real process or service, listening port,
fresh log timestamps, NeoForge and Pale Mirror load, `Done` or ready state, and
absence of a new crash loop. For a new world, verify the new seed, catalog, and
genesis evidence. Report the running PID or service, artifact identity, world
identity, and any client action required.
