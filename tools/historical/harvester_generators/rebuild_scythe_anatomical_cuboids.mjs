import fs from 'node:fs';
import path from 'node:path';

// A deliberately anatomical replacement for the rejected surface-voxel pass.
// Every part below is a named mass, plate, joint, or tendon. The existing
// GeckoLib skeleton and animation identifiers remain unchanged.

const root = path.resolve(import.meta.dirname, '..');
const sourceRoot = path.join(root, 'pale-mirror-visuals', 'src', 'main');
const projectFile = path.join(sourceRoot, 'blockbench', 'harvester', 'scythe_stalker.bbmodel');
const geometryFile = path.join(sourceRoot, 'resources', 'assets', 'pale_mirror_visuals', 'geo', 'harvester', 'scythe_stalker.geo.json');
const animationFile = path.join(sourceRoot, 'resources', 'assets', 'pale_mirror_visuals', 'animations', 'harvester', 'scythe_stalker.animation.json');

const readJson = file => JSON.parse(fs.readFileSync(file, 'utf8'));
const writeJson = (file, value) => fs.writeFileSync(file, `${JSON.stringify(value, null, 2)}\n`);

const hash = value => {
    let result = 2166136261;
    for (const character of value) result = Math.imul(result ^ character.charCodeAt(0), 16777619);
    return result >>> 0;
};
const uuid = value => {
    const part = salt => hash(`${value}:${salt}`).toString(16).padStart(8, '0');
    return `${part('a')}-${part('b').slice(0, 4)}-${part('c').slice(0, 4)}-${part('d').slice(0, 4)}-${part('e')}${part('f')}`;
};

function textureOffset(cube) {
    const seed = hash(`scythe:${cube.owner}`);
    const x = Math.floor((cube.from[0] + 48) * 3.8 + seed % 37);
    const y = Math.floor((cube.from[1] * .72 + cube.from[2] * .48 + 30) * 3.8 + Math.floor(seed / 37) % 41);
    return [16 + ((x % 192) + 192) % 192, 16 + ((y % 192) + 192) % 192];
}

function box(cubes, owner, name, from, size, inflate = .12) {
    cubes.push({ owner, name, from, size, inflate });
}

function centred(cubes, owner, name, center, size, inflate = .12) {
    box(cubes, owner, name, center.map((value, axis) => value - size[axis] / 2), size, inflate);
}

function rotatedBoxXY(cubes, owner, name, center, length, thickness, depth, rotationZ, inflate = .12) {
    // A sparse set of rotated structural cuboids gives a deliberate curved
    // gesture.  It is still entirely Minecraft-cube geometry, rather than a
    // dense voxel trace masquerading as a silhouette.
    cubes.push({
        owner,
        name,
        from: [center[0] - length / 2, center[1] - thickness / 2, center[2] - depth / 2],
        size: [length, thickness, depth],
        inflate,
        pivot: center,
        rotation: [0, 0, rotationZ]
    });
}

function linkedBoxXY(cubes, owner, name, from, to, thickness, depth, inflate = .12) {
    // Profile-contour construction: neighbouring plates share their authored
    // endpoints and overlap only by a small seam allowance. This yields one
    // readable curved limb rather than a staircase of separate blocks.
    const dx = to[0] - from[0];
    const dy = to[1] - from[1];
    const length = Math.hypot(dx, dy) + .42;
    const center = [(from[0] + to[0]) / 2, (from[1] + to[1]) / 2, (from[2] + to[2]) / 2];
    rotatedBoxXY(cubes, owner, name, center, length, thickness, depth, Math.atan2(dy, dx) * 180 / Math.PI, inflate);
}

function shellMass(cubes, owner, name, center, size) {
    const [w, h, d] = size;
    centred(cubes, owner, `${name}_core`, center, [w, h * .66, d], .34);
    centred(cubes, owner, `${name}_crown`, [center[0] - w * .04, center[1] + h * .28, center[2]], [w * .76, h * .46, d * .80], .40);
    centred(cubes, owner, `${name}_left_lobe`, [center[0] - w * .04, center[1] + h * .02, center[2] - d * .31], [w * .68, h * .58, d * .44], .24);
    centred(cubes, owner, `${name}_right_lobe`, [center[0] - w * .04, center[1] + h * .02, center[2] + d * .31], [w * .68, h * .58, d * .44], .24);
    centred(cubes, owner, `${name}_spine`, [center[0] - w * .10, center[1] + h * .58, center[2]], [w * .50, h * .18, d * .34], .12);
}

function jointRings(cubes, owner, prefix, pivot, radius) {
    for (const [index, scale] of [.98, .76].entries()) {
        centred(cubes, owner, `${prefix}_ring_${index + 1}`, [pivot[0], pivot[1] + index * .52, pivot[2]], [radius * 2 * scale, .58, radius * 2 * scale], .12);
    }
}

function addScytheArm(cubes, side) {
    const tag = side < 0 ? 'l' : 'r';
    const arm = `scythe_arm_${tag}`;
    const fore = `${arm}_fore`;
    const blade = `${arm}_blade`;
    const z = value => side * value;
    const zCenter = (value, thickness) => z(value) - thickness / 2;
    // The far scythe deliberately sits higher and nearer the body in the
    // profile.  Without this parallax offset, two real scythes collapse into
    // one silhouette even though both exist in 3-D.
    // Place the rear blade under the thorax rather than directly behind the
    // foreground blade; the reference has two separately legible crescents.
    const profileOffsetX = side < 0 ? 8.5 : 0;
    const profileOffsetY = side < 0 ? .35 : 0;
    const px = value => value + profileOffsetX;
    const py = value => value + profileOffsetY;

    // Shoulder and forearm are broad, low chitin plates. Both sides use the
    // same broad mass language: a pair of scythes, not a thin near hook plus
    // a hidden secondary appendage.
    rotatedBoxXY(cubes, arm, `${arm}_shoulder`, [px(-11.8), py(13.05), z(4.8)], 5.9, 3.05, 3.5, -30, .26);
    rotatedBoxXY(cubes, arm, `${arm}_shoulder_crown`, [px(-11.9), py(14.45), z(4.9)], 4.5, .82, 3.9, -30, .20);
    rotatedBoxXY(cubes, arm, `${arm}_shoulder_plate`, [px(-13.0), py(11.60), z(4.9)], 4.4, 2.30, 4.0, -31, .18);
    jointRings(cubes, arm, `${arm}_elbow`, [px(-13.8), py(10.65), z(5.8)], 1.52);

    rotatedBoxXY(cubes, fore, `${fore}_long_mass`, [px(-16.35), py(8.75), z(5.7)], 8.25, 3.18, 3.20, 34, .24);
    rotatedBoxXY(cubes, fore, `${fore}_long_ridge`, [px(-16.35), py(9.92), z(5.8)], 6.30, .70, 3.62, 34, .18);
    rotatedBoxXY(cubes, fore, `${fore}_inner_tendon`, [px(-16.90), py(7.65), z(5.35)], 6.85, .62, .92, 34, .08);
    jointRings(cubes, fore, `${fore}_wrist`, [px(-19.70), py(6.40), z(6.15)], 1.46);

    // Four plates trace a single broad crescent: outward from the wrist,
    // vertically down, then deliberately back toward the body at the point.
    // This is the primary reference landmark and must read without texture.
    // Four progressively tapered plates make one blade silhouette.  Their
    // broad outer wall and deliberately narrow final point preserve the
    // crescent's negative space instead of resembling another walking leg.
    // Six linked plates sample the silhouette contour of a broad organic
    // scythe: heavy root, outer belly, then a continuous inward curl to the
    // point. The count is anatomical, not surface voxelization.
    const arc = [
        [-19.85, 6.25],
        [-22.25, 5.95],
        [-24.45, 4.30],
        [-25.72, 1.55],
        [-25.82, -1.50],
        [-24.68, -4.15],
        [-22.88, -5.60]
    ];
    const thicknesses = side < 0 ? [2.48, 2.28, 2.02, 1.72, 1.02, .34] : [2.82, 2.58, 2.30, 1.95, 1.18, .40];
    const depths = [3.18, 2.92, 2.58, 2.18, 1.38, .68];
    for (let segment = 0; segment < arc.length - 1; segment++) {
        const from = [px(arc[segment][0]), py(arc[segment][1]), z(6.16)];
        const to = [px(arc[segment + 1][0]), py(arc[segment + 1][1]), z(6.16)];
        linkedBoxXY(cubes, blade, `${blade}_contour_plate_${segment + 1}`, from, to, thicknesses[segment], depths[segment], segment < 4 ? .11 : .035);
    }
}

function addLeg(cubes, index, x, z) {
    const side = z < 0 ? -1 : 1;
    // Offset the far-side anchors just enough to count all six legs without
    // putting an artificial second body length behind the torso.
    x += side < 0 ? 1.25 : 0;
    const upper = `stalker_leg_${index}_upper`;
    const lower = `stalker_leg_${index}_lower`;
    // Two actual diagonal bones and a knee joint replace the earlier stack
    // of upright blocks. Each pair has a different foot spread, keeping the
    // crouched spider stance legible in the reference profile.
    const footTarget = [-13.0, -5.8, 2.2, 12.0, 20.0, 27.0][index - 1];
    const kneeBlend = [.60, .48, .54, .48, .46, .41][index - 1];
    const kneeY = [6.45, 7.25, 7.70, 7.15, 6.65, 6.10][index - 1];
    const hip = [x, 13.0, z];
    const knee = [x + (footTarget - x) * kneeBlend, kneeY, z + side * .55];
    const foot = [footTarget, .55, z + side * 1.05];
    const segment = (owner, name, from, to, thickness, depth, inflate) => {
        const dx = to[0] - from[0];
        const dy = to[1] - from[1];
        const length = Math.hypot(dx, dy) + .55;
        const center = [(from[0] + to[0]) / 2, (from[1] + to[1]) / 2, (from[2] + to[2]) / 2];
        const angle = Math.atan2(dy, dx) * 180 / Math.PI;
        rotatedBoxXY(cubes, owner, name, center, length, thickness, depth, angle, inflate);
        return { center, angle, length };
    };
    const thigh = segment(upper, `${upper}_thigh`, hip, knee, 1.26, 1.38, .12);
    rotatedBoxXY(cubes, upper, `${upper}_thigh_ridge`, [thigh.center[0], thigh.center[1] + .38, thigh.center[2]], thigh.length * .76, .25, 1.62, thigh.angle, .05);
    jointRings(cubes, upper, `${upper}_knee`, knee, .68);
    const shin = segment(lower, `${lower}_shin`, knee, foot, 1.04, 1.18, .08);
    rotatedBoxXY(cubes, lower, `${lower}_shin_tendon`, [shin.center[0], shin.center[1] + .20, shin.center[2]], shin.length * .82, .18, .46, shin.angle, .03);
    box(cubes, lower, `${lower}_ankle`, [foot[0] - .78, .04, foot[2] - .68], [1.56, .72, 1.36], .07);
    box(cubes, lower, `${lower}_toe_outer`, [foot[0] - 1.28, -.12, foot[2] + side * .36 - .18], [1.96, .34, .36], .02);
}

function addWhips(cubes, side) {
    const tag = side < 0 ? 'l' : 'r';
    const z = value => side * value;
    const wave = side < 0 ? [0, .65, .10, -.72, -.20] : [0, -.58, .18, .72, .10];
    const pitch = side < 0 ? [8, -7, -12, 10, 6] : [-7, 9, 12, -8, -5];
    const baseY = side < 0 ? 14.15 : 15.70;
    // The reference ends in a fan of fine whips, not two heavy antennae.
    // Three continuous strands per side share the existing five animated
    // segment bones, so the runtime rig stays stable while the rear silhouette
    // gains the required organic density.
    const strands = [
        { x: 0, y: 0, z: 0, angle: 0, length: 1 },
        { x: -.72, y: 1.10, z: .95, angle: 8, length: .94 },
        { x: .38, y: -1.05, z: -1.10, angle: -8, length: 1.06 }
    ];
    for (let segment = 1; segment <= 5; segment++) {
        const owner = `whip_${tag}_${segment}`;
        for (const [strandIndex, strand] of strands.entries()) {
            const x = 14.3 + (segment - 1) * 4.55 + strand.x;
            const y = baseY + wave[segment - 1] + strand.y;
            const centerZ = z(4.8 + segment * 1.02 + strand.z);
            rotatedBoxXY(cubes, owner, `${owner}_strand_${strandIndex + 1}`, [x, y, centerZ], 5.35 * strand.length, .42, .56, pitch[segment - 1] + strand.angle, .03);
        }
    }
}

function buildCubes() {
    const cubes = [];

    // Primary silhouette constraint from the supplied profile: build one
    // compact, heavy thorax and a high front-biased hump before leg detail.
    // The former five equal dorsal slabs made a long rectangular bridge.
    rotatedBoxXY(cubes, 'abdomen', 'underbelly', [-.55, 10.55, 0], 16.8, 3.15, 8.2, 0, .34);
    centred(cubes, 'abdomen', 'thorax_bulk', [-4.20, 13.45, 0], [11.8, 5.75, 9.45], .36);
    rotatedBoxXY(cubes, 'abdomen', 'thorax_slope', [-5.15, 15.35, 0], 11.0, 3.25, 9.65, 20, .30);
    rotatedBoxXY(cubes, 'abdomen', 'dorsal_hump', [-3.95, 18.20, 0], 8.65, 2.65, 8.45, 16, .30);
    rotatedBoxXY(cubes, 'abdomen', 'dorsal_keel', [-4.15, 20.05, 0], 6.10, .72, 3.10, 12, .12);
    rotatedBoxXY(cubes, 'abdomen', 'rear_carapace', [5.55, 14.40, 0], 10.4, 4.35, 8.75, -23, .32);
    rotatedBoxXY(cubes, 'abdomen', 'rear_crown', [6.45, 16.05, 0], 7.8, 1.05, 7.05, -23, .16);
    rotatedBoxXY(cubes, 'abdomen', 'rear_pelvis', [10.65, 12.45, 0], 4.55, 2.35, 7.95, -28, .22);

    rotatedBoxXY(cubes, 'head', 'head_carapace', [-12.2, 12.85, 0], 7.8, 3.25, 7.5, -12, .30);
    rotatedBoxXY(cubes, 'head', 'head_crown', [-12.7, 14.75, 0], 6.0, 1.05, 7.7, -12, .18);
    rotatedBoxXY(cubes, 'head', 'head_brow', [-15.0, 13.55, 0], 3.5, 1.35, 7.1, -22, .15);
    box(cubes, 'head', 'head_mandible_l', [-17.7, 10.4, -3.9], [4.6, 1.85, 2.5], .10);
    box(cubes, 'head', 'head_mandible_r', [-17.7, 10.4, 1.4], [4.6, 1.85, 2.5], .10);
    rotatedBoxXY(cubes, 'head', 'head_throat', [-14.5, 10.4, 0], 5.2, 1.8, 6.0, -10, .14);
    centred(cubes, 'head', 'head_jaw_core', [-16.5, 10.15, 0], [2.5, 1.4, 5.6], .10);
    centred(cubes, 'head', 'head_near_cheek', [-13.3, 12.1, 3.8], [2.8, 2.2, 1.1], .12);
    centred(cubes, 'head', 'head_far_cheek', [-13.3, 12.1, -3.8], [2.8, 2.2, 1.1], .12);

    for (const side of [-1, 1]) addScytheArm(cubes, side);
    [[-2, -5], [-2, 5], [5, -5.2], [5, 5.2], [10, -4.5], [10, 4.5]].forEach(([x, z], index) => addLeg(cubes, index + 1, x, z));
    for (const side of [-1, 1]) addWhips(cubes, side);
    return cubes;
}

function makeElement(cube, textureUuid, index) {
    const element = {
        name: cube.name,
        from: cube.from,
        to: cube.from.map((value, axis) => value + cube.size[axis]),
        origin: cube.pivot ?? [0, 0, 0],
        uv_offset: textureOffset(cube),
        color: hash(cube.name) % 8,
        autouv: 0,
        inflate: cube.inflate,
        faces: Object.fromEntries(['north', 'east', 'south', 'west', 'up', 'down'].map(face => [face, {
            uv: [0, 0, 1, 1], texture: textureUuid
        }])),
        type: 'cube',
        uuid: uuid(`scythe-anatomical-cube:${index}:${cube.name}`)
    };
    if (cube.rotation) element.rotation = cube.rotation;
    return element;
}

function strengthenSlash(animationData) {
    const slash = animationData.animations['animation.scythe_stalker.slash'];
    if (!slash) throw new Error('Missing animation.scythe_stalker.slash');
    slash.bones.root = { position: { 0: [0, 0, 0], '0.20': [1.0, .35, 0], '0.42': [-2.8, -1.1, 0], '0.95': [0, 0, 0] } };
    for (const side of ['l', 'r']) {
        const sign = side === 'l' ? 1 : -1;
        slash.bones[`scythe_arm_${side}`] = { rotation: { 0: [0, 0, 0], '0.20': [0, 0, sign * -26], '0.42': [0, 0, sign * 48], '0.95': [0, 0, 0] } };
        slash.bones[`scythe_arm_${side}_fore`] = { rotation: { 0: [0, 0, 0], '0.20': [0, 0, sign * -42], '0.42': [0, 0, sign * 78], '0.95': [0, 0, 0] } };
        slash.bones[`scythe_arm_${side}_blade`] = { rotation: { 0: [0, 0, 0], '0.20': [0, 0, sign * -30], '0.42': [0, 0, sign * 58], '0.95': [0, 0, 0] } };
    }
    return animationData;
}

function makeBlockbenchAnimations(animationData, groups) {
    const groupByName = new Map(groups.map(group => [group.name, group]));
    return Object.entries(animationData.animations).map(([name, clip]) => {
        const animators = {};
        for (const [bone, channels] of Object.entries(clip.bones ?? {})) {
            const group = groupByName.get(bone);
            if (!group) throw new Error(`${name}: animation refers to missing group ${bone}`);
            const animator = animators[group.uuid] = { name: bone, type: 'bone', keyframes: [] };
            for (const [channel, keys] of Object.entries(channels)) for (const [time, value] of Object.entries(keys)) {
                const point = channel === 'rotation' ? [-value[0], -value[1], value[2]] : [-value[0], value[1], value[2]];
                animator.keyframes.push({
                    channel,
                    time: Number(time),
                    interpolation: 'linear',
                    data_points: [{ x: String(point[0]), y: String(point[1]), z: String(point[2]) }],
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

function rebuild() {
    const project = readJson(projectFile);
    const geometry = readJson(geometryFile);
    const animation = strengthenSlash(readJson(animationFile));
    const model = geometry['minecraft:geometry'][0];
    const cubes = buildCubes();
    const bones = new Map(model.bones.map(bone => [bone.name, bone]));
    const groups = new Map(project.groups.map(group => [group.name, group]));
    // Cubes in this generator are authored in the finished rest-pose world
    // layout.  The source skeleton's presentation rotations are only valid
    // for its former local cubes and were folding our world-space anatomy into
    // a lump.  Keep each existing pivot/name/parent for GeckoLib animation,
    // but make its rest pose neutral in both representations.
    for (const bone of bones.values()) if (bone.rotation) bone.rotation = [0, 0, 0];
    for (const group of groups.values()) if (group.rotation) group.rotation = [0, 0, 0];
    for (const bone of bones.values()) bone.cubes = [];
    for (const cube of cubes) {
        const bone = bones.get(cube.owner);
        if (!bone) throw new Error(`Missing GeckoLib bone ${cube.owner}`);
        const geometryCube = {
            origin: [-(cube.from[0] + cube.size[0]), cube.from[1], cube.from[2]],
            size: cube.size,
            uv: textureOffset(cube),
            inflate: cube.inflate
        };
        if (cube.rotation) {
            geometryCube.pivot = [-cube.pivot[0], cube.pivot[1], cube.pivot[2]];
            geometryCube.rotation = [-cube.rotation[0], cube.rotation[1], cube.rotation[2]];
        }
        bone.cubes.push(geometryCube);
    }
    const textureUuid = project.textures[0]?.uuid;
    if (!textureUuid) throw new Error('Blockbench project has no texture UUID');
    project.elements = cubes.map((cube, index) => makeElement(cube, textureUuid, index));
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
        if (!group) throw new Error(`Missing Blockbench group ${boneName}`);
        return { uuid: group.uuid, isOpen: false, children: [...(elementByOwner.get(boneName) ?? []), ...(children.get(boneName) ?? []).map(node)] };
    };
    project.outliner = model.bones.filter(bone => !bone.parent).map(bone => node(bone.name));
    project.animations = makeBlockbenchAnimations(animation, project.groups);
    writeJson(geometryFile, geometry);
    writeJson(animationFile, animation);
    writeJson(projectFile, project);
    const nonEmpty = model.bones.filter(bone => bone.cubes.length).length;
    console.log(`scythe_stalker: ${cubes.length} anatomical cuboids across ${nonEmpty}/${model.bones.length} bones`);
}

rebuild();
