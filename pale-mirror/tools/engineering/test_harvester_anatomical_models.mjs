import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const root = path.resolve(import.meta.dirname, '..', '..');
const harvesterRoot = path.join(root, 'pale-mirror-visuals', 'src', 'main', 'blockbench', 'harvester');
const assetRoot = path.join(root, 'pale-mirror-visuals', 'src', 'main', 'resources', 'assets', 'pale_mirror_visuals');
const creatures = {
    biomass_collector: {
        minimumMeshes: 15,
        landmarks: ['hifi_biomass_collector_body', 'hifi_biomass_collector_head', 'hifi_biomass_collector_tail']
    },
    crusher_stalker: {
        minimumMeshes: 9,
        landmarks: ['hifi_crusher_stalker_torso', 'hifi_crusher_stalker_head', 'hifi_crusher_stalker_arm_l']
    },
    scythe_stalker: {
        minimumMeshes: 12,
        landmarks: ['hifi_scythe_stalker_abdomen', 'hifi_scythe_stalker_head', 'hifi_scythe_stalker_foreground_blades']
    }
};

const readJson = file => JSON.parse(fs.readFileSync(file, 'utf8'));
const run = (script, argument) => spawnSync('node', [path.join(root, 'tools', script), argument], { encoding: 'utf8' });

for (const [creature, contract] of Object.entries(creatures)) {
    const project = readJson(path.join(harvesterRoot, `${creature}_reference_hifi.bbmodel`));
    const animation = readJson(path.join(assetRoot, 'animations', 'harvester', `${creature}.animation.json`));
    const groupNames = new Set(project.groups.map(group => group.name));
    const elementNames = new Set(project.elements.map(element => element.name));

    assert.equal(project.meta?.model_format, 'bedrock', `${creature}: canonical image model must retain the Bedrock animation skeleton`);
    assert.ok(project.name.endsWith('_reference_hifi'), `${creature}: canonical image-faithful project name is missing`);
    assert.ok(project.elements.length >= contract.minimumMeshes, `${creature}: expected at least ${contract.minimumMeshes} organic meshes, got ${project.elements.length}`);
    assert.ok(project.elements.every(element => element.type === 'mesh'), `${creature}: canonical image model contains a low-detail cube substitute`);
    for (const element of project.elements) {
        assert.ok(Object.keys(element.vertices ?? {}).length >= 12, `${creature}/${element.name}: mesh has too little volume`);
        assert.ok(Object.keys(element.faces ?? {}).length >= 8, `${creature}/${element.name}: mesh has too little surface`);
    }
    for (const landmark of contract.landmarks) assert.ok(elementNames.has(landmark), `${creature}: missing image-derived landmark ${landmark}`);
    for (const animationProject of project.animations ?? []) {
        for (const animator of Object.values(animationProject.animators ?? {})) {
            assert.ok(groupNames.has(animator.name), `${creature}: Blockbench animation targets unknown mesh skeleton group ${animator.name}`);
        }
    }
    // Legacy compatibility clips must stay attached to the same named skeleton
    // until the Visuals module gains its explicit mesh runtime renderer.
    const geo = readJson(path.join(assetRoot, 'geo', 'harvester', `${creature}.geo.json`));
    const legacyBoneNames = new Set(geo['minecraft:geometry'][0].bones.map(bone => bone.name));
    for (const definition of Object.values(animation.animations)) {
        for (const bone of Object.keys(definition.bones ?? {})) {
            assert.ok(legacyBoneNames.has(bone), `${creature}: legacy animation targets unknown skeleton bone ${bone}`);
            assert.ok(groupNames.has(bone), `${creature}: legacy animation bone ${bone} is absent from canonical mesh skeleton`);
        }
    }
}

const imageModelRebuild = run('build_harvester_hifi_blockbench_models.mjs', '--check');
assert.equal(imageModelRebuild.status, 0, `canonical image meshes are not reproducible:\n${imageModelRebuild.stderr || imageModelRebuild.stdout}`);

const legacyRebuild = run('rebuild_harvester_anatomical_models.mjs', '--check');
assert.equal(legacyRebuild.status, 0, `legacy compatibility geometry is not reproducible:\n${legacyRebuild.stderr || legacyRebuild.stdout}`);
console.log('Harvester image-faithful model contracts passed.');
