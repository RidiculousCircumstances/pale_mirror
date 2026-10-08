import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import test from 'node:test';

const project = resolve(import.meta.dirname, '../../..');

test('the pack pilot binds one materialized pack source and records the loader inventory before accepting a scenario', async () => {
  const [runner, build, client] = await Promise.all([
    readFile(resolve(project, 'tools/frontier-v3-test-pilot/src/run-scenario.mjs'), 'utf8'),
    readFile(resolve(project, 'pale-mirror-neoforge/build.gradle'), 'utf8'),
    readFile(resolve(project, 'pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/frontier/v3/client/FrontierV3FullPackPreflight.java'), 'utf8')
  ]);
  assert.match(runner, /FRONTIER_V3_PILOT_PACK_DIRECTORY must name an absolute materialized full-pack directory/);
  assert.match(runner, /-PfrontierV3PilotPackSource=\$\{packPilotDirectory\}/);
  assert.match(runner, /-PfrontierV3PilotRequiredMods=\$\{PACK_PILOT_REQUIRED_MOD_IDS\.join\(','\)\}/);
  assert.match(runner, /PMV3_PILOT_LOADED_MODS\\s\+\(\\\{\.\*\\\}\)/);
  assert.match(runner, /\(!packPilot \|\| loadedModInventory !== null\)/);
  assert.match(build, /frontierV3PilotPackSource/);
  assert.match(build, /include 'mods\/\*\*', 'config\/\*\*', 'defaultconfigs\/\*\*', 'resourcepacks\/\*\*', 'shaderpacks\/\*\*'/);
  assert.match(build, /pack source must be a materialized directory, not a symbolic link/);
  assert.match(build, /pale_mirror\.frontier_v3\.test_pilot\.required_mods/);
  assert.match(build, /materialized pack is immutable release input/);
  assert.match(build, /onboardAccessibility:b:true\\n/);
  assert.match(build, /skipMultiplayerWarning:b:true\\n/);
  assert.match(client, /verifyRequiredModsBeforeQuickPlay\(FMLClientSetupEvent event\)/);
  assert.match(client, /PMV3_PILOT_LOADED_MODS/);
});
