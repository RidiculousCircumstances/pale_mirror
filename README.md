# Far Frontier

Reproducible NeoForge 1.21.1 exploration, engineering and survival pack. Its core
loop is preparation at a Create base, a long expedition, a meaningful discovery,
return, and better transport for the next trip. Structures are intentionally made
rare; difficulty comes from composition, AI and occasional events—not global health
or damage multipliers.

## Versions and installation

- Minecraft `1.21.1`
- NeoForge `21.1.248`
- Java 21 for clients and mod bytecode; pinned Java 22 for the dedicated server runtime
- packwiz manifest format `1.1.0`

Use a launcher that can import Packwiz metadata, or install through the Packwiz
installer URL exposed by the repository. On a dedicated server, run
[`scripts/server-smoke-test.sh`](scripts/server-smoke-test.sh) after setting
`JAVA_HOME` to the pinned Java 22 runtime. The script creates a disposable server in `.work/`;
it never writes JARs into the repository.

Recommended allocation: 6–8 GB client RAM; 6 GB server RAM for initial testing and
8–12 GB for a populated long-running server.

## Profiles and known risks

Alex's Caves and Citadel are not part of Far Frontier. Their unofficial ports
are neither distributed nor supported by the client, server, or Pale Mirror
development profiles. Create: Caliber is excluded from the default manifest and
remains an evaluation-only candidate. Spore is a core but deliberately
constrained local-contamination layer; it is not an additional global progression
system.

The default dedicated-server profile pins Lithium, ModernFix and Fast Noise.
C2ME is an explicit experimental Java 22 profile, not a default. Millénaire is
disabled in the current profile after repeatable server-thread stalls during village
chunk loading; Pale Mirror's adapter remains optional.

## Progression and campaign journal

Pale Mirror is the authoritative world-state and story director. Ravents remains
installed only as a dormant future materialization adapter: the default profile has
no Ravents raids or events. Enhanced Celestials and its Blood Moon events are
excluded, so ambient global waves cannot be mistaken for PM-authored consequences.
Create is intended to unlock practical long-range travel, not early-game toys.

FTB Quests is adopted as the client/server campaign journal: it will display
chapters, operation briefings and ordinary progress, while the planned Far Frontier
core remains authoritative for remote targets, boss activation and unique rewards.
The FTB Quests artifact and FTB Library are awaiting their reproducible Packwiz pin
and full-profile boot test; no quest chain is enabled yet.

## Pack layers

- World: Terralith, Tectonic, Structurify
- Rendering: vanilla Minecraft renderer
- Engineering: Create
- Exploration: IDA, IDAS, YUNG's structures, Cataclysm
- Ecology/threats: Naturalist, vanilla + Hostile Tactics + Born in Chaos + Mowzie's
- Infection: Crimson Curse (global strategic state) + Spore (rare local contamination sites)
- Combat: Better Combat, Simply Swords
- Campaign journal: FTB Quests (adopted; materialisation/boot gate pending)
- Director: Pale Mirror; Ravents is dormant with no configured raids/events
- Civilization: PM-authored settlements + Villager Overhaul; Integrated Villages
  remains an installed private asset source with both settlement sets disabled

See [architecture](docs/architecture.md), the [compatibility matrix](docs/mod-compatibility.md),
the [Crimson Curse policy](docs/crimson-curse.md), [Spore policy](docs/spore-integration.md),
the [expedition and chapter-boss design](docs/expedition-system.md), the
[quest-system decision](docs/quest-system.md), and the
[test matrix](docs/testing.md)
before changing the pack.

## Installation and LAN

Use Java 21 on clients and the pinned Java 22 runtime on the dedicated server with
NeoForge `21.1.248`; do not copy arbitrary
JARs between client and server. The installer synchronises exact pinned artifacts
from `pack.toml` and therefore requires a reachable URL to that file.

On the server, from this checkout:

```bash
scripts/install-server-java22.sh
export JAVA_BIN="$HOME/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java"
scripts/install-server.sh --target /srv/far-frontier --java "$JAVA_BIN" --accept-eula
/srv/far-frontier/scripts/run-server-java22.sh
```

The installer sets `view-distance=8`, `simulation-distance=6`, preserves synchronous
chunk writes and installs a managed `-Xms4G -Xmx12G` heap block. Enable the isolated
C2ME candidate only for compatibility testing with `--enable-c2me`, or switch an
installed server with `scripts/set-server-performance-profile.sh`.
The launch wrapper verifies the pinned Java 22 runtime instead of relying on the
shell's default Java; clients and Pale Mirror artifacts remain on Java 21.

The omitted `--pack-url` is only suitable for materialising that server from the
local checkout. To let LAN clients install or update, keep a separate pack source
reachable, for example in another terminal on the server host:

```bash
packwiz serve --port 8091
```

Then create a client launcher instance with NeoForge `21.1.248` and synchronise it:

The private host exposes a stable latest-installer endpoint. Download the tiny
launcher **once into the root of the Minecraft instance**. On Linux:

```bash
curl -fsSL http://rd-EliteMini-Series.local:8092/scripts/bootstrap-client.sh \
  -o /path/to/NeoForge-instance/update-far-frontier.sh
chmod +x /path/to/NeoForge-instance/update-far-frontier.sh
```

Every later update is simply:

```bash
/path/to/NeoForge-instance/update-far-frontier.sh
```

The launcher uses its own directory as the target. It downloads the current
installer and its helper libraries,
verifies them through `pack.toml -> index.toml -> SHA-256`, obtains the current
PM/Railway SHA-512 pins, and then synchronises the pack. Override the private
host with `--source-base` or `FAR_FRONTIER_SOURCE_URL` when necessary.

On Windows, download one `.cmd` file into the instance once:

```powershell
Invoke-WebRequest `
  "http://rd-EliteMini-Series.local:8092/scripts/update-client.cmd" `
  -OutFile "C:\path\to\NeoForge-instance\update-far-frontier.cmd"
```

After that, double-click `update-far-frontier.cmd`. It determines the instance
directory from its own location and downloads/runs the current PowerShell
installer automatically.

On macOS, download one `.command` file into the root of the NeoForge instance:

```bash
curl -fsSL http://rd-EliteMini-Series.local:8092/scripts/update-client-macos.command \
  -o "/path/to/NeoForge-instance/update-far-frontier.command"
chmod +x "/path/to/NeoForge-instance/update-far-frontier.command"
```

After that, double-click `update-far-frontier.command` in Finder for every update.
It uses its own directory as the instance root, finds Java 21 from an installed JDK,
`PATH`, or the official Minecraft Launcher's managed runtime, and downloads the
current checksum-pinned installer automatically. The default host uses the
development machine's mDNS name, so a normal DHCP address change does not leave a
client on an old Pale Mirror JAR; override it with `FAR_FRONTIER_SOURCE_URL` only
when using another host. For the official launcher the
usual instance root is `~/Library/Application Support/minecraft`. If macOS blocks a
file downloaded through a browser, use Finder's **Open** context-menu once. A custom
Java executable can be selected by setting `JAVA_BIN` before running the script.

The longer explicit commands below remain available for testing another pack
or artifact release.

```bash
scripts/install-client.sh \
  --target /path/to/NeoForge-instance \
  --pack-url http://SERVER_LAN_IP:8091/pack.toml \
  --pale-mirror-url https://ARTIFACT_HOST/pale-mirror/0.2.0/pale_mirror-0.2.0.jar \
  --pale-mirror-sha512 <PM-SHA-512> \
  --railway-untold-url https://ARTIFACT_HOST/pale-mirror/0.2.0/railwaysuntold-neoforge-1.2.1-pm.1.jar \
  --railway-untold-sha512 2993b2274cbdc8775d7ab960feee2a2e50aa23d22486c811b426c0eb309d43fa9225ed5f69470caae87b2b09a7658216c974f95cdf0de792946f96cf066f2f73
```

For Windows 10 PowerShell, from a checkout of this repository:

```powershell
.\scripts\install-client.ps1 `
  -Target "C:\\path\\to\\NeoForge-instance" `
  -PackUrl "http://SERVER_LAN_IP:8091/pack.toml" `
  -PaleMirrorUrl "https://ARTIFACT_HOST/pale-mirror/0.2.0/pale_mirror-0.2.0.jar" `
  -PaleMirrorSha512 "<PM-SHA-512>" `
  -RailwayUntoldUrl "https://ARTIFACT_HOST/pale-mirror/0.2.0/railwaysuntold-neoforge-1.2.1-pm.1.jar" `
  -RailwayUntoldSha512 "2993b2274cbdc8775d7ab960feee2a2e50aa23d22486c811b426c0eb309d43fa9225ed5f69470caae87b2b09a7658216c974f95cdf0de792946f96cf066f2f73"
```

The target is the instance directory, not the Minecraft launcher executable. The
client scripts require Java 21 and verify the bootstrap SHA-256. Create: Caliber is
not downloaded by the default profile. Allow TCP `25565` for Minecraft and TCP
`8091` only while clients need pack updates. Re-run the client installer after each
pack update; stop `packwiz serve` when distribution is no longer needed.

Pale Mirror is a private, separately built artifact. The bootstrap client updater
and the server installer resolve Pale Mirror, Pale Mirror Visuals and Railway
Untold from the same hosted pack source, then verify each SHA-512 before activating
it. Explicit URL/SHA-512 pairs (or the corresponding environment variables) remain
available for an alternative trusted artifact source and must always be supplied
together. Old Pale Mirror JARs are retired under the installer cache during an
update, so an obsolete network channel cannot remain active beside the pinned JAR.
The graybox client profile no longer installs JourneyMap, EZ Actions, Simply
Tooltips, EMF/ETF or Polytone; existing copies are moved to a recoverable local
archive. To use an explicit artifact source, give the pairs to the dedicated-server
installer:

```bash
scripts/install-server.sh \
  --target /srv/far-frontier \
  --level-name frontier-v3-live \
  --accept-eula \
  --pale-mirror-url https://ARTIFACT_HOST/pale-mirror/0.2.0/pale_mirror-0.2.0.jar \
  --pale-mirror-sha512 <PM-SHA-512> \
  --railway-untold-url https://ARTIFACT_HOST/pale-mirror/0.2.0/railwaysuntold-neoforge-1.2.1-pm.1.jar \
  --railway-untold-sha512 2993b2274cbdc8775d7ab960feee2a2e50aa23d22486c811b426c0eb309d43fa9225ed5f69470caae87b2b09a7658216c974f95cdf0de792946f96cf066f2f73
```

Generate the pin from the exact published artifact:

```bash
sha512sum pale_mirror-0.2.0.jar railwaysuntold-neoforge-1.2.1-pm.1.jar
```

To publish newly built private artifacts to the stable `current` URLs, run:

```bash
scripts/publish-client-artifacts.sh
```

Before a Frontier v3 disposable-server deployment, make a clean detached Pale
Mirror worktree for the exact commit, then run the read-only stop gate. It
rejects a dirty tree, a different source commit, a non-Java-22 runtime, a
checksum mismatch or a different `level-name` before any server/world mutation
occurs:

```bash
git -C pale-mirror worktree add --detach /tmp/pale-mirror-release <commit>
cd /tmp/pale-mirror-release
./gradlew -PfrontierV3GrayboxDisabledModsCatalog=/home/rd/proj/minecraft/config/graybox-disabled-mods.txt \
  :pale-mirror-neoforge:build :pale-mirror-neoforge:verifyPackagedJar
scripts/frontier-v3-deploy-preflight.sh \
  --source-repo /tmp/pale-mirror-release \
  --source-ref '<commit>' \
  --artifact /tmp/pale-mirror-release/pale-mirror-neoforge/build/libs/pale_mirror-0.3.0-SNAPSHOT.jar \
  --sha512 "$(sha512sum /tmp/pale-mirror-release/pale-mirror-neoforge/build/libs/pale_mirror-0.3.0-SNAPSHOT.jar | awk '{print $1}')" \
  --target /home/rd/far-frontier-server \
  --level-name frontier-v3-live-rNN \
  --java /home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java \
  --service far-frontier-v3-live.service
```

It is deliberately not a deployment command: publish/install and exact
fresh-world reset remain explicit follow-up operations. Immediately after a
restart, prove the *new* process rather than relying on an older `Done` line:

```bash
started_at=$(date +%s) # capture immediately before restarting the service
# restart the verified service here
scripts/frontier-v3-deploy-verify.sh \
  --target /home/rd/far-frontier-server \
  --level-name frontier-v3-live-rNN \
  --sha512 '<same pinned SHA-512>' \
  --service far-frontier-v3-live.service \
  --not-before "$started_at" \
  --wait-seconds 30
```

`far-frontier-client-host.service` serves the repository and ignored `hosted/`
artifact directory on the LAN/Tailscale interfaces at port `8092`; it is independent
of the development-only `packwiz serve` process.
