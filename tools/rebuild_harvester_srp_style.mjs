import fs from 'node:fs';
import path from 'node:path';

// Rebuild the runtime-friendly creatures from the project-owned hi-fi sources.
// Each visible surface is sampled into small overlapping cuboids, while the
// existing GeckoLib skeleton remains the source of all motion. This keeps the
// editor model visually close to the reference interpretation without relying
// on a runtime polygon-mesh renderer.

const root = path.resolve(import.meta.dirname, '..');
const sourceRoot = path.join(root, 'pale-mirror-visuals', 'src', 'main');
const blockbenchRoot = path.join(sourceRoot, 'blockbench', 'harvester');
const geoRoot = path.join(sourceRoot, 'resources', 'assets', 'pale_mirror_visuals', 'geo', 'harvester');
const animationRoot = path.join(sourceRoot, 'resources', 'assets', 'pale_mirror_visuals', 'animations', 'harvester');
const names = ['biomass_collector', 'crusher_stalker', 'scythe_stalker'];

const hash = value => {
    let result = 2166136261;
    for (const character of value) result = Math.imul(result ^ character.charCodeAt(0), 16777619);
    return result >>> 0;
};
const uuid = value => {
    const part = salt => hash(`${value}:${salt}`).toString(16).padStart(8, '0');
    return `${part('a')}-${part('b').slice(0, 4)}-${part('c').slice(0, 4)}-${part('d').slice(0, 4)}-${part('e')}${part('f')}`;
};
const readJson = file => JSON.parse(fs.readFileSync(file, 'utf8'));
const writeJson = (file, value) => fs.writeFileSync(file, `${JSON.stringify(value, null, 2)}\n`);
const clamp = (value, low, high) => Math.max(low, Math.min(high, value));

function dot(a, b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
function subtract(a, b) { return [a[0] - b[0], a[1] - b[1], a[2] - b[2]]; }
function add(a, b) { return [a[0] + b[0], a[1] + b[1], a[2] + b[2]]; }
function scale(a, amount) { return [a[0] * amount, a[1] * amount, a[2] * amount]; }

// Christer Ericson's closest-point test, expressed here to avoid a geometry
// dependency. It lets us sample the actual hi-fi shell rather than inventing a
// second silhouette from boxes.
function pointTriangleDistanceSquared(point, a, b, c) {
    const ab = subtract(b, a);
    const ac = subtract(c, a);
    const ap = subtract(point, a);
    const d1 = dot(ab, ap);
    const d2 = dot(ac, ap);
    if (d1 <= 0 && d2 <= 0) return dot(ap, ap);

    const bp = subtract(point, b);
    const d3 = dot(ab, bp);
    const d4 = dot(ac, bp);
    if (d3 >= 0 && d4 <= d3) return dot(bp, bp);

    const vc = d1 * d4 - d3 * d2;
    if (vc <= 0 && d1 >= 0 && d3 <= 0) {
        const factor = d1 / (d1 - d3);
        const closest = add(a, scale(ab, factor));
        const delta = subtract(point, closest);
        return dot(delta, delta);
    }

    const cp = subtract(point, c);
    const d5 = dot(ab, cp);
    const d6 = dot(ac, cp);
    if (d6 >= 0 && d5 <= d6) return dot(cp, cp);

    const vb = d5 * d2 - d1 * d6;
    if (vb <= 0 && d2 >= 0 && d6 <= 0) {
        const factor = d2 / (d2 - d6);
        const closest = add(a, scale(ac, factor));
        const delta = subtract(point, closest);
        return dot(delta, delta);
    }

    const va = d3 * d6 - d5 * d4;
    if (va <= 0 && (d4 - d3) >= 0 && (d5 - d6) >= 0) {
        const bc = subtract(c, b);
        const factor = (d4 - d3) / ((d4 - d3) + (d5 - d6));
        const closest = add(b, scale(bc, factor));
        const delta = subtract(point, closest);
        return dot(delta, delta);
    }

    const denominator = 1 / (va + vb + vc);
    const v = vb * denominator;
    const w = vc * denominator;
    const closest = add(a, add(scale(ab, v), scale(ac, w)));
    const delta = subtract(point, closest);
    return dot(delta, delta);
}

function meshTriangles(mesh) {
    const triangles = [];
    for (const face of Object.values(mesh.faces ?? {})) {
        const ids = face.vertices ?? [];
        for (let index = 1; index < ids.length - 1; index++) {
            const a = mesh.vertices[ids[0]];
            const b = mesh.vertices[ids[index]];
            const c = mesh.vertices[ids[index + 1]];
            if (a && b && c) triangles.push([a, b, c]);
        }
    }
    return triangles;
}

function surfaceVoxels(mesh, unit) {
    const voxels = new Map();
    const radius = unit * 0.73;
    const radiusSquared = radius * radius;
    for (const triangle of meshTriangles(mesh)) {
        const mins = [0, 1, 2].map(axis => Math.min(...triangle.map(point => point[axis])) - radius);
        const maxs = [0, 1, 2].map(axis => Math.max(...triangle.map(point => point[axis])) + radius);
        const lower = mins.map(value => Math.floor(value / unit));
        const upper = maxs.map(value => Math.floor(value / unit));
        for (let x = lower[0]; x <= upper[0]; x++) for (let y = lower[1]; y <= upper[1]; y++) for (let z = lower[2]; z <= upper[2]; z++) {
            const point = [(x + .5) * unit, (y + .5) * unit, (z + .5) * unit];
            if (pointTriangleDistanceSquared(point, ...triangle) <= radiusSquared) voxels.set(`${x},${y},${z}`, [x, y, z]);
        }
    }
    return [...voxels.values()].sort((a, b) => a[0] - b[0] || a[1] - b[1] || a[2] - b[2]);
}

function meshOwner(name, meshName, center) {
    const z = center[2];
    const side = z < 0 ? 'l' : 'r';
    if (name === 'biomass_collector') {
        if (meshName.endsWith('_body')) return 'carapace';
        if (meshName.endsWith('_head')) return 'head';
        if (meshName.endsWith('_tail')) return 'tail_base';
        const leg = Number(meshName.match(/leg_(\d+)_/)?.[1] ?? 0) + 1;
        return `leg_${leg}_${side}_upper`;
    }
    if (name === 'crusher_stalker') {
        if (meshName.endsWith('_torso')) return 'torso';
        if (meshName.endsWith('_head')) return 'head';
        if (meshName.includes('_arm_')) {
            const arm = meshName.endsWith('_l') ? 'l' : 'r';
            return `crusher_arm_${arm}`;
        }
        if (meshName.includes('_hind_')) {
            const leg = Number(meshName.match(/hind_(\d+)/)?.[1] ?? 0) + 1;
            return `hind_leg_${leg}_upper`;
        }
        if (meshName.endsWith('_tendrils')) return 'rear_tendril_1';
    }
    if (name === 'scythe_stalker') {
        if (meshName.endsWith('_abdomen')) return 'abdomen';
        if (meshName.endsWith('_head')) return 'head';
        if (meshName.endsWith('_foreground_blades')) return 'root';
        if (meshName.includes('_scythe_arm_')) {
            const arm = meshName.endsWith('_l') ? 'l' : 'r';
            return `scythe_arm_${arm}`;
        }
        if (meshName.includes('_leg_')) {
            const leg = Number(meshName.match(/leg_(\d+)/)?.[1] ?? 0) + 1;
            return `stalker_leg_${leg}_upper`;
        }
        if (meshName.endsWith('_whips')) return 'whip_l_1';
    }
    throw new Error(`${name}: no skeleton owner for ${meshName}`);
}

function addCollectorFeelers(cubes, unit) {
    for (const side of ['l', 'r']) for (let segment = 1; segment <= 5; segment++) {
        const sign = side === 'l' ? -1 : 1;
        // Thin multi-box feelers retain the draped front silhouette which is
        // visible in the collector reference but intentionally absent from the
        // hi-fi body shell.
        for (let bead = 0; bead < 3; bead++) cubes.push({
            owner: `feeler_${side}_${segment}`,
            from: [-24.5 - (segment - 1) * 3.1 - bead * .72, 15.1 - segment * .28 - bead * .18, sign * (5.0 + segment * 1.0) - .55],
            size: [1.25, .72, 1.1],
            name: `srp_feeler_${side}_${segment}_${bead}`
        });
    }
}

function buildCubes(name, hiFi) {
    // SRP's most elaborate creatures are measured in hundreds, not thousands,
    // of cuboids. These resolutions preserve the curved reference silhouettes
    // while staying in the same practical rendering envelope.
    const units = {biomass_collector: 2.72, crusher_stalker: 2.22, scythe_stalker: 2.12};
    const unit = units[name];
    const cubes = [];
    for (const mesh of hiFi.elements.filter(element => element.type === 'mesh')) {
        for (const coordinate of surfaceVoxels(mesh, unit)) {
            const from = coordinate.map(value => value * unit);
            const center = from.map(value => value + unit / 2);
            cubes.push({
                owner: meshOwner(name, mesh.name, center),
                from,
                size: [unit, unit, unit],
                name: `srp_${mesh.name.replace(/^hifi_/, '')}_${coordinate.join('_')}`
            });
        }
    }
    if (name === 'biomass_collector') addCollectorFeelers(cubes, unit);
    return cubes;
}

function addJointCuffs(name, cubes, groups) {
    const relevant = {
        biomass_collector: /^(leg_\d+_[lr]_(lower|foot)|tail_[2-5]|feeler_[lr]_\d+)$/,
        crusher_stalker: /^(crusher_arm_[lr]_(forearm|fist)|hind_leg_\d+_lower|rear_tendril_[2-5])$/,
        scythe_stalker: /^(scythe_arm_[lr]_(fore|blade)|stalker_leg_\d+_lower|whip_[lr]_[2-5])$/
    }[name];
    for (const group of groups) {
        if (!relevant.test(group.name)) continue;
        const [x, y, z] = group.origin;
        // A cuff is deliberately attached to the child joint, not the long
        // parent limb. It stays readable during a bend and supplies the
        // segmented SRP-style motion that a single continuous hi-fi mesh lacks.
        [[[-1.45, -.42, -1.45], [2.9, .68, 2.9]], [[-1.18, -.02, -1.18], [2.36, .62, 2.36]], [[-1.0, .4, -1.0], [2.0, .46, 2.0]]].forEach(([offset, size], index) => cubes.push({
            owner: group.name,
            from: [x + offset[0], y + offset[1], z + offset[2]],
            size,
            name: `srp_joint_cuff_${group.name}_${index}`
        }));
    }
}

function textureOffset(name, cube) {
    const ownerSeed = hash(`${name}:${cube.owner}`);
    // Adjacent cubes sample adjacent coordinates. Earlier random-per-cube UV
    // origins made the living surface read as a checkerboard instead of one
    // continuous material.
    const scale = name === 'biomass_collector' ? 3.1 : 3.8;
    const x = Math.floor((cube.from[0] + 48) * scale + ownerSeed % 37);
    const y = Math.floor((cube.from[1] * .72 + cube.from[2] * .48 + 30) * scale + Math.floor(ownerSeed / 37) % 41);
    return [16 + ((x % 192) + 192) % 192, 16 + ((y % 192) + 192) % 192];
}

function makeElement(name, cube, textureUuid, index) {
    const uv = textureOffset(name, cube);
    return {
        name: cube.name,
        from: cube.from,
        to: cube.from.map((value, axis) => value + cube.size[axis]),
        origin: [0, 0, 0],
        uv_offset: uv,
        color: hash(cube.name) % 8,
        autouv: 0,
        inflate: .085,
        faces: Object.fromEntries(['north', 'east', 'south', 'west', 'up', 'down'].map(face => [face, {
            uv: [0, 0, 1, 1], texture: textureUuid
        }])),
        type: 'cube',
        uuid: uuid(`${name}:srp-cube:${index}:${cube.name}`)
    };
}

function cycle(length, phase, amplitude, invert = false) {
    const sign = invert ? -1 : 1;
    const valueAt = progress => Number((sign * amplitude * Math.sin(progress * Math.PI * 2 + phase * .78)).toFixed(3));
    return Object.fromEntries([0, .25, .5, .75, 1].map(progress => [String(Number((length * progress).toFixed(3))), [0, 0, valueAt(progress)]]));
}

function enrichAnimations(name, animationFile) {
    const data = readJson(animationFile);
    const clips = data.animations;
    if (name === 'biomass_collector') {
        const idle = clips['animation.biomass_collector.idle'];
        for (let leg = 1; leg <= 6; leg++) for (const side of ['l', 'r']) {
            const phase = (leg + (side === 'r' ? 1 : 0)) % 2 === 0;
            idle.bones[`leg_${leg}_${side}_upper`].rotation = cycle(4, phase, 13, phase);
            idle.bones[`leg_${leg}_${side}_lower`].rotation = cycle(4, phase, 18, !phase);
        }
        for (const side of ['l', 'r']) for (let segment = 1; segment <= 5; segment++) {
            const amplitude = 3.8 + segment * .65;
            idle.bones[`feeler_${side}_${segment}`] = {rotation: cycle(4, segment, amplitude, side === 'r' ? segment % 2 === 0 : segment % 2 !== 0)};
        }
        for (let segment = 2; segment <= 5; segment++) idle.bones[`tail_${segment}`] = {rotation: cycle(4, segment, 4.0 + segment, segment % 2 === 0)};
    }
    if (name === 'crusher_stalker') {
        const walk = clips['animation.crusher_stalker.walk'];
        for (let leg = 1; leg <= 4; leg++) {
            const reverse = leg % 2 === 0;
            walk.bones[`hind_leg_${leg}_upper`].rotation = cycle(1.6, leg, 16, reverse);
            walk.bones[`hind_leg_${leg}_lower`] = {rotation: cycle(1.6, leg + 1, 22, !reverse)};
        }
        for (let segment = 1; segment <= 5; segment++) walk.bones[`rear_tendril_${segment}`] = {rotation: cycle(1.6, segment, 3.0 + segment * 1.2, segment % 2 === 0)};
        walk.bones.crusher_arm_l = {rotation: cycle(1.6, 0, 2.8, false)};
        walk.bones.crusher_arm_r = {rotation: cycle(1.6, 1, 2.8, true)};
        const crush = clips['animation.crusher_stalker.crush'];
        // The high-detail arm shell is owned by the shoulder bone. Rotate that
        // bone for the impact as well as the nested cuffs, so the full mass
        // commits to the slam instead of leaving the visible arm static.
        crush.bones.crusher_arm_l = {rotation: {0: [0, 0, 0], '0.35': [0, 0, 25], '1.1': [0, 0, 0]}};
        crush.bones.crusher_arm_r = {rotation: {0: [0, 0, 0], '0.35': [0, 0, -25], '1.1': [0, 0, 0]}};
    }
    if (name === 'scythe_stalker') {
        const walk = clips['animation.scythe_stalker.walk'];
        for (let leg = 1; leg <= 6; leg++) {
            const reverse = leg % 2 === 0;
            walk.bones[`stalker_leg_${leg}_upper`].rotation = cycle(2, leg, 15, reverse);
            walk.bones[`stalker_leg_${leg}_lower`] = {rotation: cycle(2, leg + 1, 19, !reverse)};
        }
        for (const side of ['l', 'r']) for (let segment = 1; segment <= 5; segment++) {
            walk.bones[`whip_${side}_${segment}`] = {rotation: cycle(2, segment, 3.2 + segment * .7, side === 'r' ? segment % 2 === 0 : segment % 2 !== 0)};
        }
        walk.bones.scythe_arm_l = {rotation: cycle(2, 0, 3.2, false)};
        walk.bones.scythe_arm_r = {rotation: cycle(2, 1, 3.2, true)};
        const slash = clips['animation.scythe_stalker.slash'];
        slash.bones.scythe_arm_l = {rotation: {0: [0, 0, 0], '0.42': [0, 0, 26], '0.95': [0, 0, 0]}};
        slash.bones.scythe_arm_r = {rotation: {0: [0, 0, 0], '0.42': [0, 0, -26], '0.95': [0, 0, 0]}};
    }
    writeJson(animationFile, data);
    return data;
}

function makeBlockbenchAnimations(name, animationData, groups) {
    const groupByName = new Map(groups.map(group => [group.name, group]));
    return Object.entries(animationData.animations).map(([name, clip]) => {
        const animators = {};
        for (const [bone, channels] of Object.entries(clip.bones ?? {})) {
            const group = groupByName.get(bone);
            if (!group) throw new Error(`${name}: animation refers to missing group ${bone}`);
            const animator = animators[group.uuid] = {name: bone, type: 'bone', keyframes: []};
            for (const [channel, keys] of Object.entries(channels)) for (const [time, value] of Object.entries(keys)) {
                const point = channel === 'rotation' ? [-value[0], -value[1], value[2]] : [-value[0], value[1], value[2]];
                animator.keyframes.push({
                    channel,
                    time: Number(time),
                    interpolation: 'linear',
                    data_points: [{x: String(point[0]), y: String(point[1]), z: String(point[2])}],
                    uuid: uuid(`${name}:${bone}:${channel}:${time}`)
                });
            }
        }
        return {
            uuid: uuid(`${name}:animation`),
            name,
            loop: clip.loop === true ? 'loop' : clip.loop === 'hold_on_last_frame' ? 'hold' : 'once',
            override: false,
            length: clip.animation_length ?? 0,
            snapping: 20,
            animators
        };
    });
}

function rebuild(name) {
    const projectFile = path.join(blockbenchRoot, `${name}.bbmodel`);
    const hiFiFile = path.join(blockbenchRoot, `${name}_reference_hifi.bbmodel`);
    const geoFile = path.join(geoRoot, `${name}.geo.json`);
    const animationFile = path.join(animationRoot, `${name}.animation.json`);
    const project = readJson(projectFile);
    const hiFi = readJson(hiFiFile);
    const geo = readJson(geoFile);
    const model = geo['minecraft:geometry'][0];
    const cubes = buildCubes(name, hiFi);
    addJointCuffs(name, cubes, project.groups);
    const bones = new Map(model.bones.map(bone => [bone.name, bone]));
    const groups = new Map(project.groups.map(group => [group.name, group]));
    for (const bone of bones.values()) bone.cubes = [];
    for (const cube of cubes) {
        const bone = bones.get(cube.owner);
        if (!bone) throw new Error(`${name}: missing bone ${cube.owner}`);
        const uv = textureOffset(name, cube);
        bone.cubes.push({
            origin: [-(cube.from[0] + cube.size[0]), cube.from[1], cube.from[2]],
            size: cube.size,
            uv,
            inflate: .085
        });
    }
    const textureUuid = project.textures[0].uuid;
    project.elements = cubes.map((cube, index) => makeElement(name, cube, textureUuid, index));
    const elementByOwner = new Map();
    cubes.forEach((cube, index) => {
        const entries = elementByOwner.get(cube.owner) ?? [];
        entries.push(project.elements[index].uuid);
        elementByOwner.set(cube.owner, entries);
    });
    const children = new Map();
    for (const bone of model.bones) if (bone.parent) {
        const entries = children.get(bone.parent) ?? [];
        entries.push(bone.name);
        children.set(bone.parent, entries);
    }
    const node = boneName => {
        const group = groups.get(boneName);
        if (!group) throw new Error(`${name}: missing group ${boneName}`);
        return {
            uuid: group.uuid,
            isOpen: false,
            children: [...(elementByOwner.get(boneName) ?? []), ...(children.get(boneName) ?? []).map(node)]
        };
    };
    project.outliner = model.bones.filter(bone => !bone.parent).map(bone => node(bone.name));
    const enriched = enrichAnimations(name, animationFile);
    project.animations = makeBlockbenchAnimations(name, enriched, project.groups);
    writeJson(geoFile, geo);
    writeJson(projectFile, project);
    const nonEmptyBones = model.bones.filter(bone => bone.cubes.length > 0).length;
    return {name, cubes: cubes.length, bones: model.bones.length, nonEmptyBones};
}

for (const name of names) {
    const result = rebuild(name);
    console.log(`${result.name}: ${result.cubes} cubes across ${result.nonEmptyBones}/${result.bones} animated skeleton bones`);
}
