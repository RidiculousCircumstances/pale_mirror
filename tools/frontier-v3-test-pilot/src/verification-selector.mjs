import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';

export const VERIFICATION_PLAN_SCHEMA = 1;
const ALL_TIERS = Object.freeze(['T0', 'T1', 'T2', 'T3', 'T4']);

/** Loads the checked-in ownership map; a plan never guesses an unowned path is cheap. */
export async function loadDependencyManifest(path) {
  return validateDependencyManifest(JSON.parse(await readFile(path, 'utf8')));
}

export function validateDependencyManifest(value) {
  if (!value || value.schema !== VERIFICATION_PLAN_SCHEMA || value.kind !== 'frontier-v3-verification-dependencies'
      || !same(value.tiers, ALL_TIERS) || !Array.isArray(value.rules) || value.rules.length === 0 || value.rules.length > 128) {
    throw new Error('verification dependency manifest is malformed');
  }
  const ids = new Set(); const prefixes = new Set();
  const rules = value.rules.map((rule) => {
    if (!rule || !token(rule.id) || ids.has(rule.id) || !Array.isArray(rule.prefixes) || rule.prefixes.length === 0
        || !Array.isArray(rule.tiers) || rule.tiers.length === 0 || rule.tiers.some((tier) => !ALL_TIERS.includes(tier))) {
      throw new Error('verification dependency rule is malformed');
    }
    ids.add(rule.id);
    const checkedPrefixes = rule.prefixes.map((prefix) => {
      if (!safePathPrefix(prefix) || prefixes.has(prefix)) throw new Error('verification dependency prefix is malformed or duplicate');
      prefixes.add(prefix); return prefix;
    });
    return Object.freeze({ id: rule.id, prefixes: Object.freeze(checkedPrefixes.sort()), tiers: Object.freeze([...new Set(rule.tiers)].sort(tierOrder)),
      t1: validateT1(rule.t1) });
  });
  return Object.freeze({ schema: value.schema, kind: value.kind, tiers: ALL_TIERS, rules: Object.freeze(rules.sort((a, b) => a.id.localeCompare(b.id))) });
}

/** Exact T1 ownership is declarative: a narrow change cannot silently run an all-module suite. */
function validateT1(value) {
  const modules = ['frontier', 'neoforge', 'domain', 'api'];
  if (!value || !Array.isArray(value.nodeTests) || value.nodeTests.some((path) => !safePath(path) || !path.endsWith('.test.mjs'))
      || value.nodeTests.length > 32 || !Array.isArray(value.gradleTests) || value.gradleTests.length > 64
      || !value.allModules || typeof value.allModules !== 'object' || Array.isArray(value.allModules)) {
    throw new Error('verification dependency T1 descriptor is malformed');
  }
  if (new Set(value.nodeTests).size !== value.nodeTests.length) {
    throw new Error('verification dependency T1 Node selector is duplicate');
  }
  const nodeTests = [...value.nodeTests].sort();
  const seen = new Set();
  const gradleTests = value.gradleTests.map((entry) => {
    if (!entry || !modules.includes(entry.module) || typeof entry.className !== 'string'
        || !/^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+$/.test(entry.className)
        || seen.has(`${entry.module}:${entry.className}`)) {
      throw new Error('verification dependency T1 Gradle selector is malformed');
    }
    seen.add(`${entry.module}:${entry.className}`);
    return Object.freeze({ module: entry.module, className: entry.className });
  }).sort((left, right) => `${left.module}:${left.className}`.localeCompare(`${right.module}:${right.className}`));
  if (!same(Object.keys(value.allModules).sort(), modules.slice().sort())) {
    throw new Error('verification dependency T1 module policy has an unknown or missing module');
  }
  const allModules = Object.freeze(Object.fromEntries(modules.map((module) => {
    if (typeof value.allModules[module] !== 'boolean') throw new Error('verification dependency T1 module policy is malformed');
    return [module, value.allModules[module]];
  })));
  if (nodeTests.length === 0 && gradleTests.length === 0 && !Object.values(allModules).some(Boolean)) {
    throw new Error('verification dependency T1 descriptor has no executable owner');
  }
  return Object.freeze({ nodeTests: Object.freeze(nodeTests), gradleTests: Object.freeze(gradleTests), allModules });
}

/**
 * Computes an executable tier selection from working-content paths.  Ambiguity and absence are
 * intentionally not errors that let a caller continue narrowly: they widen to the full gate.
 */
export function selectVerificationPlan(manifest, { changedPaths, freshEvidence = false, finalEvidence = freshEvidence } = {}) {
  const checked = validateDependencyManifest(manifest);
  if (!Array.isArray(changedPaths) || changedPaths.length === 0 || changedPaths.some((path) => !safePath(path))) {
    throw new Error('verification selection needs one or more safe changed paths');
  }
  if (typeof freshEvidence !== 'boolean' || typeof finalEvidence !== 'boolean' || finalEvidence !== freshEvidence) {
    throw new Error('verification final evidence requires fresh-evidence semantics');
  }
  const selections = []; const requiredTiers = new Set(); let conservative = false;
  for (const path of [...new Set(changedPaths)].sort()) {
    const owners = checked.rules.filter((rule) => rule.prefixes.some((prefix) => path === prefix || path.startsWith(prefix)));
    if (owners.length !== 1) {
      conservative = true;
      selections.push(Object.freeze({ path, owner: null, reason: owners.length === 0 ? 'UNKNOWN_PATH' : 'MULTIPLE_OWNERS', tiers: ALL_TIERS }));
      ALL_TIERS.forEach((tier) => requiredTiers.add(tier));
    } else {
      owners[0].tiers.forEach((tier) => requiredTiers.add(tier));
      selections.push(Object.freeze({ path, owner: owners[0].id, reason: 'DECLARED_OWNER', tiers: owners[0].tiers }));
    }
  }
  if (finalEvidence) ALL_TIERS.forEach((tier) => requiredTiers.add(tier));
  // T4 is the explicit fresh, slice/commit boundary.  It remains visible in
  // `requiredTiers` during an iterative run, so no caller can misreport it as
  // irrelevant, but it is not silently charged to every focused edit.
  const tiers = finalEvidence ? requiredTiers : new Set([...requiredTiers].filter((tier) => tier !== 'T4'));
  const deferredFinalTiers = finalEvidence ? [] : [...requiredTiers].filter((tier) => tier === 'T4');
  const result = Object.freeze({ schema: VERIFICATION_PLAN_SCHEMA, kind: 'frontier-v3-verification-plan',
    manifestSha256: sha256(JSON.stringify(checked)), changedPaths: Object.freeze([...new Set(changedPaths)].sort()),
    selections: Object.freeze(selections), requiredTiers: Object.freeze(ALL_TIERS.filter((tier) => requiredTiers.has(tier))),
    tiers: Object.freeze(ALL_TIERS.filter((tier) => tiers.has(tier))), deferredFinalTiers: Object.freeze(deferredFinalTiers), conservative,
    cache: freshEvidence ? 'BYPASS_REQUIRED' : 'CANDIDATE_ONLY', fixtureImage: freshEvidence ? 'FORBIDDEN' : 'DEVELOPMENT_ONLY' });
  return result;
}

function same(left, right) { return Array.isArray(left) && left.length === right.length && left.every((value, index) => value === right[index]); }
function tierOrder(left, right) { return ALL_TIERS.indexOf(left) - ALL_TIERS.indexOf(right); }
function safePath(value) { return typeof value === 'string' && value.length > 0 && !value.startsWith('/') && !value.includes('\\') && !value.split('/').includes('..'); }
function safePathPrefix(value) { return safePath(value); }
function token(value) { return typeof value === 'string' && /^[a-z][a-z0-9_-]{0,63}$/.test(value); }
function sha256(value) { return createHash('sha256').update(value).digest('hex'); }
