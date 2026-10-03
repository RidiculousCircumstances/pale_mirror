# Single-authority simulation calendar

User-authorized scope: main alone implements an isolated calendar and scoped
Minecraft presentation so canonical fast-forward and visible day/night agree.
No unrelated farmer repair, new activities, world migration or broad test campaign.

## Source-proven discrepancy

SettlementDailySchedule reads canonical tick modulo the ruleset day length.
Queued fast-forward advances that tick but does not advance visible daylight.
Graybox's DerivedLevelData reads Overworld day time; its setDayTime is a no-op.
Writing to graybox or copying a requested fast-forward target therefore cannot
provide correct ownership or actual-progress synchronization.

## Responsibilities and implementation

- Pure SimulationCalendar: stateless day/phase arithmetic over canonical time;
  no Minecraft, persistence, ticking, hunger or resident activity decisions.
- SettlementDailySchedule: work/free windows using that calendar. World state
  rejects a schedule whose day length disagrees with the persisted world policy.
- MinecraftCalendarPresentation: explicit dimension + calendar + read-only
  instant source. Both Level/ServerLevel data aliases share a read-only calendar
  view. Unrelated properties delegate unchanged; gameTime stays native.
- CalendarPresentationEvents: host-only post-turn refresh/client synchronization
  and stopped-server cleanup. It is not a physical executor or process dispatcher.
- FrontierV3CalendarBinding: composition glue selecting the v3 policy/world;
  the reusable adapter does not know jobs, residents or Frontier runtime types.
- Runtime calendarInstant: cheap read-only last authoritative instant, retained
  on quarantine; no snapshot encoding or new disk writes.

The graybox calendar ignores vanilla time writes, sleep skips and native daylight
speed. Unbound worlds are unaffected. Minecraft phase uses its eight-day lunar
cycle; operator status also exposes the full canonical day index. In-flight
work still observes existing safe-yield policy, not instant midnight cancellation.

## Verification and claim boundaries

Focused calendar/schedule tests cover work/free boundaries, scaling, overflow,
recovery derivation and incompatible world schedules. Runtime tests cover reached
time, quarantine, durable restart and diagnostic partial-fast-forward receipts.
One native calendar slice checks actual mixed-in data aliases, rejected external
writes, held/partial phase, native gameTime, another dimension and detach/reattach.
Relevant existing fast-forward/schedule checks and static/package gates apply.
This does not establish full farmer lifecycle or graphical client acceptance.
No schema change or backwards-compatibility path is introduced.

## Implemented result — 2026-10-01

Wired in `/home/rd/proj/pm-f06r3-facility-lane-recovery` over `812db5a6`;
source WIP, not committed/deployed. Calendar module and adapter match the
responsibilities above. Explicit runtime stop also detaches its calendar;
quarantine retains and freezes the authoritative last instant instead.

Verification receipts:

- Focused frontier test command selected SimulationCalendarTest,
  ResidentActivityCoordinatorTest and ResidentLifeScheduleRetirementTest;
  with the module's automatic FrontierArchitectureTest, 14/14 pass.
- Related NeoForge diagnostics/absolute-fast-forward/safety/slice checks:
  58 unchanged tests pass. New runtime/diagnostic calendar tests 3/3 pass after
  correcting test parsing of the pre-existing PMV3_DIAG envelope. These cover
  graceful durable restart, actual advancement/quarantine and partial receipts.
- `-PfrontierV3GameTestSlice=calendar
  :pale-mirror-neoforge:runFrontierV3SceneGameTestServer`: 1/1 required native
  GameTest passes at 08:39:09 local; normal shutdown. It proves the actual
  Minecraft attachment boundary, not a graphical client or farmer scenario.
- `guardrails verifyJavaStyle verifyLargeFiles verifyFrontierV3ArchitectureDebt
  :pale-mirror-neoforge:assemble :pale-mirror-neoforge:verifyPackagedJar`:
  final PASS in 22s. Canonical governance architecture contract validates;
  source diff has no whitespace errors. No ceiling waivers.

Live R9 still runs `812db5a6`. Client sky/fast-forward visual acceptance and
full farmer cycle remain open; no HUMAN_CANDIDATE or F0.6R3 completion claim.

Deployment follow-up, 2026-10-01 ~09:35 local: locally checkpointed `c4d32fe1`,
no push. Clean detached build installed through pack scripts, preserving R9
world/seed. Preflight and fresh post-start verification pass; service PID853984,
Java854008, port25565. RCON status OK, 366 residents/12 sites. Actual graybox
daytime744 matches the bracketed calendar743→744 at canonical120743→120744.
No test world reset, vanilla time write or canonical fast-forward was used for
the read-only check. Exact SHA-512 and release path are in the active ledger.
Client graphical and full farmer lifecycle acceptance remain open.
