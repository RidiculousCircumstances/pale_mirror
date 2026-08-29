import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname } from 'node:path';

const SCHEMA = 1;
const EVIDENCE_ACTIONS = new Set(['walk', 'look', 'break', 'open_container', 'withdraw', 'deposit', 'die', 'wait', 'wait_until_block', 'inspect']);
const SETUP_ACTIONS = new Set(['command', 'observe']);

export async function loadScenario(path) {
  const source = await readFile(path, 'utf8');
  const scenario = JSON.parse(source);
  validateScenario(scenario);
  return { scenario, sha256: createHash('sha256').update(source).digest('hex') };
}

export function validateScenario(scenario) {
  if (!scenario || scenario.schema !== SCHEMA || typeof scenario.id !== 'string' || !scenario.id) {
    throw new Error(`scenario must contain schema=${SCHEMA} and nonempty id`);
  }
  if (!scenario.server || typeof scenario.server.host !== 'string' || !Number.isInteger(scenario.server.port)) {
    throw new Error('scenario server must contain host and integer port');
  }
  if (!scenario.pilot || typeof scenario.pilot.username !== 'string' || !scenario.pilot.username) {
    throw new Error('scenario pilot must contain username');
  }
  for (const [phase, allowed] of [['setup', SETUP_ACTIONS], ['actions', EVIDENCE_ACTIONS]]) {
    const actions = scenario[phase] ?? [];
    if (!Array.isArray(actions)) throw new Error(`scenario ${phase} must be an array`);
    for (const action of actions) {
      if (!action || !allowed.has(action.type)) throw new Error(`unsupported ${phase} action: ${action?.type}`);
      if (action.type === 'command' && !String(action.command).startsWith('/')) throw new Error('setup command must start with /');
      if (['walk', 'look', 'break', 'open_container', 'wait_until_block'].includes(action.type)) validatePosition(action.position ?? action.at);
      if (action.type === 'wait' && (!Number.isInteger(action.ms) || action.ms < 0 || action.ms > 120_000)) {
        throw new Error('wait duration must be 0..120000 milliseconds');
      }
      if (action.type === 'wait_until_block' && (typeof action.block !== 'string' || !action.block || !Number.isInteger(action.timeoutMs)
          || action.timeoutMs < 0 || action.timeoutMs > 120_000)) {
        throw new Error('wait_until_block needs block and timeoutMs 0..120000');
      }
      if (action.type === 'inspect' && (!['summary', 'site', 'actor', 'item', 'operation', 'intent', 'trace'].includes(action.view)
          || typeof action.id !== 'string')) throw new Error('inspect needs a read-only v3 view and id');
    }
  }
  const assertions = scenario.assertions ?? [];
  if (!Array.isArray(assertions)) throw new Error('scenario assertions must be an array');
  for (const assertion of assertions) {
    if (!assertion || !Number.isInteger(assertion.after) || assertion.after < 0 || assertion.after > (scenario.actions ?? []).length
        || !['summary', 'site', 'actor', 'item', 'operation', 'intent', 'trace'].includes(assertion.view)
        || typeof assertion.id !== 'string' || (assertion.view !== 'summary' && !assertion.id)
        || !assertion.expect || typeof assertion.expect !== 'object') {
      throw new Error('invalid diagnostic assertion');
    }
  }
  const frames = scenario.frames ?? [];
  if (!Array.isArray(frames)) throw new Error('scenario frames must be an array');
  for (const frame of frames) {
    if (!frame || !Number.isInteger(frame.after) || frame.after < 0 || frame.after > (scenario.actions ?? []).length
        || typeof frame.name !== 'string' || !/^[a-z0-9][a-z0-9_-]*$/.test(frame.name)) {
      throw new Error('invalid frame declaration');
    }
  }
}

function validatePosition(value) {
  if (!value || !Number.isInteger(value.x) || !Number.isInteger(value.y) || !Number.isInteger(value.z)) {
    throw new Error('block position must contain integer x, y and z');
  }
}

export function correlation(runId, step) {
  return `scenario:${runId}:${step}`;
}

export function newManifest({ scenario, sha256, runId }) {
  return {
    schema: 1,
    scenarioId: scenario.id,
    scenarioSha256: sha256,
    runId,
    pilot: scenario.pilot.username,
    startedAt: new Date().toISOString(),
    setup: [],
    actions: [],
    diagnostics: [],
    frames: []
  };
}

export async function saveManifest(path, manifest) {
  await mkdir(dirname(path), { recursive: true });
  await writeFile(path, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
}
