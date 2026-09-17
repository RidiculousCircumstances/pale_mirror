# Pale Mirror repository context

`repository_context.py` is a local, read-only facade for the canonical
governance checkout and the fixed active implementation checkout.  It is not a
runtime dependency and graph output is advisory: verify returned source anchors
against the named canonical contracts.  Its graph remains scoped to the
declared implementation directory, while readiness fingerprints every tracked
or untracked Git status input from that directory's actual Git top level. The
status result reports both roots; no repository path is silently excluded.

Use `python3 tools/engineering/context/repository_context.py status`, `index`,
`task "..."`, `path <repository-path>`, `change <repository-path...>`,
`trace <qualified-symbol>`, `impact <repository-path...>`, or `search "..."`.
`path` and `change` resolve the compact declarative `cross_links.yml` index and
make unmapped or graph-stale results explicit. `mcp` serves the same bounded
allowlist over stdio for the named `pale-mirror-context` Codex server.  The pin,
vendor executable and cache are private user state outside either Git checkout;
no command accepts another repository root, uploads source, starts a watcher,
or exposes graph mutation.
