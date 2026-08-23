import fs from 'node:fs';
import path from 'node:path';

// Deliberately small, named anatomical masses for the runtime cube models.
// This is a production source generator, not a mesh/surface sampler: every
// cuboid here has a visible job in the primary silhouette, a joint, or a
// readable secondary plane. The editor skeleton and GeckoLib animation IDs
// are retained verbatim so this remains a resource-only art change.

const root = path.resolve(import.meta.dirname, '..');
const sourceRoot = path.join(root, 'pale-mirror-visuals', 'src', 'main');
const projectRoot = path.join(sourceRoot, 'blockbench', 'harvester');
const geoRoot = path.join(sourceRoot, 'resources', 'assets', 'pale_mirror_visuals', 'geo', 'harvester');
const animationRoot = path.join(sourceRoot, 'resources', 'assets', 'pale_mirror_visuals', 'animations', 'harvester');
const checkOnly = process.argv.includes('--check');

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
const clone = value => JSON.parse(JSON.stringify(value));

function textureOffset(creature, cube) {
    // Keep related forms within nearby organic fields of the authored sheet.
    // A deterministic but bounded variation avoids one tiled-pixel look while
    // retaining a coherent material family per creature.
    const seed = hash(`${creature}:${cube.owner}:${cube.name}`);
    const palette = creature === 'biomass_collector'
        ? [[16, 16], [84, 18], [148, 22], [32, 108], [128, 112]]
        : creature === 'crusher_stalker'
            ? [[20, 20], [92, 26], [152, 36], [42, 128], [126, 126]]
            : [[18, 18], [82, 24], [146, 28], [38, 126], [134, 132]];
    const [baseU, baseV] = palette[seed % palette.length];
    return [baseU + (seed >>> 4) % 18, baseV + (seed >>> 11) % 18];
}

function faceUvs(offset, size) {
    // Blockbench does not infer per-face UVs from `uv_offset` when explicit
    // faces are present. The former 1×1 placeholder made the authored 256px
    // skin read as flat colour in every visual audit. Map each visible plane
    // to a bounded patch of the existing organism texture instead.
    const [baseU, baseV] = offset;
    const [width, height, depth] = size.map(value => Math.max(2, Math.min(28, Math.ceil(value))));
    const patch = (u, v, patchWidth, patchHeight) => {
        const boundedU = Math.max(0, Math.min(256 - patchWidth, u % 228));
        const boundedV = Math.max(0, Math.min(256 - patchHeight, v % 228));
        return [boundedU, boundedV, boundedU + patchWidth, boundedV + patchHeight];
    };
    return {
        north: patch(baseU, baseV, width, height),
        south: patch(baseU + 31, baseV, width, height),
        east: patch(baseU + 62, baseV, depth, height),
        west: patch(baseU + 88, baseV, depth, height),
        up: patch(baseU + 114, baseV, width, depth),
        down: patch(baseU + 145, baseV, width, depth)
    };
}

function box(cubes, owner, name, from, size, inflate = .08) {
    cubes.push({ owner, name, from, size, inflate });
}

function centred(cubes, owner, name, center, size, inflate = .08) {
    box(cubes, owner, name, center.map((value, axis) => value - size[axis] / 2), size, inflate);
}

function rotatedXY(cubes, owner, name, center, length, thickness, depth, degrees, inflate = .08) {
    cubes.push({
        owner,
        name,
        from: [center[0] - length / 2, center[1] - thickness / 2, center[2] - depth / 2],
        size: [length, thickness, depth],
        pivot: center,
        rotation: [0, 0, degrees],
        inflate
    });
}

function linkXY(cubes, owner, name, from, to, thickness, depth, inflate = .08) {
    const dx = to[0] - from[0];
    const dy = to[1] - from[1];
    const center = [(from[0] + to[0]) / 2, (from[1] + to[1]) / 2, (from[2] + to[2]) / 2];
    rotatedXY(cubes, owner, name, center, Math.hypot(dx, dy) + .36, thickness, depth, Math.atan2(dy, dx) * 180 / Math.PI, inflate);
}

function ring(cubes, owner, name, center, size) {
    centred(cubes, owner, `${name}_upper`, [center[0], center[1] + .28, center[2]], [size[0], .38, size[2]], .06);
    centred(cubes, owner, `${name}_lower`, [center[0], center[1] - .28, center[2]], [size[0] * .78, .28, size[2] * .78], .05);
}

function collectorCubes() {
    return collectorProductionCubes();
}

function collectorProductionCubes() {
    const cubes = [];

    // One low continuous trunk supports six separately readable pressure
    // sacs. The body is deliberately subordinate to the heavier dorsal and
    // leading masses, so it cannot become another flat roof.
    rotatedXY(cubes, 'underbody', 'collector_low_belly', [2.4, 8.8, 0], 33.0, 5.2, 11.6, -2, .36);
    rotatedXY(cubes, 'underbody', 'collector_belly_keel', [3.5, 6.9, 0], 27.0, 1.8, 8.4, -3, .24);
    centred(cubes, 'underbody', 'collector_near_rib', [1.5, 10.5, 5.4], [24.0, 2.6, 2.4], .18);
    centred(cubes, 'underbody', 'collector_far_rib', [1.5, 10.5, -5.4], [24.0, 2.6, 2.4], .18);

    // Each load has four intentional masses: a low rear lobe, two overlapping
    // flanks and a short cap. Their decreasing peaks form the reference's
    // broad forward-heavy cascade without a uniform cube lattice.
    const sacs = [
        { x: -14.5, peak: 17.0, width: 16.0, height: 10.8, depth: 17.0 },
        { x: -.7, peak: 15.5, width: 14.0, height: 9.6, depth: 16.0 },
        { x: 10.4, peak: 13.8, width: 11.5, height: 8.0, depth: 13.6 },
        { x: 19.8, peak: 12.0, width: 8.8, height: 6.3, depth: 10.8 },
        { x: 27.1, peak: 10.4, width: 6.3, height: 5.1, depth: 8.6 },
        { x: 32.4, peak: 8.9, width: 4.3, height: 4.0, depth: 6.8 }
    ];
    sacs.forEach((sac, index) => {
        const name = `collector_sac_${index + 1}`;
        const flankHeight = sac.height * .42;
        if (index === 0) {
            // The first sac is the reference's dominant high dome.  A broad
            // lower core supports two short, steeply crossing flanks and a
            // tiny recessed crown.  The three short upper forms produce a
            // convex lobe rather than a long canopy; the rear lobe hands the
            // weight into sac two below the crest.
            centred(cubes, 'carapace', `${name}_under_core`, [-18.3, 10.0, 0], [14.6, 7.4, 17.8], .58);
            rotatedXY(cubes, 'carapace', `${name}_fore_flank`, [-23.6, 13.6, 0], 6.3, 5.8, 17.4, 34, .54);
            rotatedXY(cubes, 'carapace', `${name}_rear_flank`, [-13.0, 13.9, 0], 6.6, 6.0, 17.0, -34, .53);
            centred(cubes, 'carapace', `${name}_inset_cap`, [-18.3, 16.2, 0], [3.4, 1.9, 14.6], .46);
            rotatedXY(cubes, 'carapace', `${name}_rear_lobe`, [-8.2, 10.3, 0], 5.8, 4.5, 15.4, -14, .44);
            return;
        }
        if (index === 1) {
            // Re-author the second pressure sac as one distinct low dome:
            // broad core, two short crossing flanks, a recessed crown and a
            // rear hand-off. It must read separately from the lead load,
            // never as another section of one continuous roof.
            centred(cubes, 'carapace', `${name}_under_core`, [2.0, 8.7, 0], [13.0, 6.0, 15.4], .52);
            rotatedXY(cubes, 'carapace', `${name}_fore_flank`, [-4.0, 10.8, 0], 5.6, 5.0, 15.0, 22, .48);
            centred(cubes, 'carapace', `${name}_inset_cap`, [1.0, 13.0, 0], [3.6, 1.8, 12.6], .43);
            rotatedXY(cubes, 'carapace', `${name}_rear_flank`, [6.0, 10.5, 0], 5.6, 4.8, 14.8, -20, .46);
            rotatedXY(cubes, 'carapace', `${name}_rear_lobe`, [9.6, 8.5, 0], 4.2, 3.2, 13.4, -14, .39);
            return;
        }
        if (index === 2) {
            // Sac three is a separate compact pressure dome, not a shorter
            // run of the same dorsal roof. Its broad low core carries two
            // crossing flanks, a cap set one full block beneath sac two, and
            // a short rear lobe that deliberately breaks before sac four.
            centred(cubes, 'carapace', `${name}_under_core`, [16.0, 7.7, 0], [9.8, 5.2, 13.4], .50);
            rotatedXY(cubes, 'carapace', `${name}_fore_flank`, [10.6, 9.5, 0], 6.8, 4.6, 13.2, 21, .46);
            centred(cubes, 'carapace', `${name}_inset_cap`, [15.6, 11.7, 0], [4.2, 2.0, 11.4], .43);
            rotatedXY(cubes, 'carapace', `${name}_rear_flank`, [20.5, 8.8, 0], 6.4, 4.1, 12.8, -20, .44);
            rotatedXY(cubes, 'carapace', `${name}_rear_lobe`, [23.6, 7.1, 0], 4.4, 2.9, 11.6, -14, .39);
            return;
        }
        if (index === 3) {
            // Sac four begins the compact rear cascade as another complete
            // five-mass dome. Its cap sits a full level under sac three and
            // the short rear lobe leaves an intentional shoulder break for
            // the next pressure load, rather than continuing one roof plane.
            centred(cubes, 'carapace', `${name}_under_core`, [28.2, 6.8, 0], [8.2, 4.6, 10.8], .48);
            rotatedXY(cubes, 'carapace', `${name}_fore_flank`, [23.8, 8.5, 0], 6.1, 4.1, 10.6, 20, .44);
            centred(cubes, 'carapace', `${name}_inset_cap`, [27.8, 10.5, 0], [3.6, 1.8, 9.4], .41);
            rotatedXY(cubes, 'carapace', `${name}_rear_flank`, [31.7, 7.9, 0], 5.8, 3.7, 10.2, -20, .42);
            rotatedXY(cubes, 'carapace', `${name}_rear_lobe`, [34.5, 6.4, 0], 3.8, 2.5, 9.4, -14, .37);
            return;
        }
        if (index === 4) {
            // Sac five remains a complete small dome. Its broad low core
            // and two crossing flanks make the rear cascade fall in clear
            // organic beats; the inset cap is one level below sac four and
            // the rear lobe gives the terminal sac room to read separately.
            centred(cubes, 'carapace', `${name}_under_core`, [38.0, 5.6, 0], [6.8, 4.0, 8.6], .45);
            rotatedXY(cubes, 'carapace', `${name}_fore_flank`, [34.4, 7.0, 0], 5.1, 3.5, 8.4, 19, .41);
            centred(cubes, 'carapace', `${name}_inset_cap`, [37.6, 8.7, 0], [3.0, 1.5, 7.4], .38);
            rotatedXY(cubes, 'carapace', `${name}_rear_flank`, [40.8, 6.4, 0], 4.8, 3.1, 8.0, -19, .39);
            rotatedXY(cubes, 'carapace', `${name}_rear_lobe`, [43.0, 5.2, 0], 3.2, 2.2, 7.2, -13, .35);
            return;
        }
        if (index === 5) {
            // The terminal sac is still a complete five-mass organic dome.
            // Its narrow core and flanks descend one level below sac five;
            // immediately after the rear lobe the silhouette releases into
            // the deliberately subordinate tail taper.
            centred(cubes, 'carapace', `${name}_under_core`, [46.6, 4.3, 0], [4.8, 3.2, 6.4], .42);
            rotatedXY(cubes, 'carapace', `${name}_fore_flank`, [44.0, 5.5, 0], 3.9, 2.9, 6.2, 18, .38);
            centred(cubes, 'carapace', `${name}_inset_cap`, [46.3, 6.9, 0], [2.2, 1.3, 5.4], .35);
            rotatedXY(cubes, 'carapace', `${name}_rear_flank`, [48.6, 5.0, 0], 3.6, 2.5, 6.0, -18, .36);
            rotatedXY(cubes, 'carapace', `${name}_rear_lobe`, [50.2, 4.0, 0], 2.5, 1.8, 5.3, -12, .32);
            return;
        }
        centred(cubes, 'carapace', `${name}_under_core`, [sac.x + sac.width * .26, sac.peak - sac.height * .56, 0], [sac.width * .54, sac.height * .48, sac.depth * .82], .44);
        rotatedXY(cubes, 'carapace', `${name}_fore_shoulder`, [sac.x - sac.width * .22, sac.peak - sac.height * .34, 0], sac.width * .54, flankHeight, sac.depth * .78, 21, .43);
        rotatedXY(cubes, 'carapace', `${name}_rear_shoulder`, [sac.x + sac.width * .24, sac.peak - sac.height * .37, 0], sac.width * .50, flankHeight * .92, sac.depth * .76, -20, .41);
        centred(cubes, 'carapace', `${name}_inset_cap`, [sac.x, sac.peak - sac.height * .12, 0], [sac.width * .22, sac.height * .22, sac.depth * .68], .40);
    });

    // The head is a heavy two-part curtain: an upper, broad organic mass
    // falls from the body, while a narrower overlapping lower mass reaches
    // the ground. This creates a real hanging weight rather than a single
    // architectural diagonal wedge.
    linkXY(cubes, 'head', 'collector_head_drape', [-20.7, 13.2, 0], [-25.1, 7.1, 0], 8.4, 17.2, .56);
    linkXY(cubes, 'head', 'collector_head_drape_lower', [-24.5, 8.9, 0], [-30.1, .2, 0], 5.9, 15.2, .50);
    centred(cubes, 'head', 'collector_head_drape_cheek', [-22.4, 9.4, 0], [8.8, 7.2, 15.8], .44);

    // Six near-side arch legs establish the primary rhythm. Each is a thick
    // thigh, a compact joint, a tapered shin and a grounded pad; no picket
    // fence of duplicate far-side braces competes with their negative spaces.
    const legs = [
        // The leading pair own the near-side stance: each folds downward and
        // returns to a distinct broad foot, leaving a clean walk-through gap
        // rather than joining the other pads into a decorative ground rail.
        [[-11.0, 11.5, 5.6], [-14.5, 6.0, 8.4], [-11.5, .7, 9.4]],
        [[-2.0, 11.5, 6.0], [-5.4, 6.2, 8.8], [-2.8, .7, 9.8]],
        [[6.5, 11.1, 6.0], [3.1, 5.9, 8.6], [6.2, .7, 10.0]],
        [[14.2, 10.7, 5.8], [10.8, 5.7, 8.2], [13.7, .7, 9.5]],
        [[20.3, 10.2, 5.5], [17.2, 5.3, 7.8], [20.0, .7, 8.9]],
        [[26.0, 9.5, 5.1], [23.5, 5.0, 7.2], [26.0, .7, 8.3]]
    ];
    legs.forEach(([hip, knee, foot], index) => {
        const number = index + 1;
        const upper = `leg_${number}_r_upper`;
        const lower = `leg_${number}_r_lower`;
        const footBone = `leg_${number}_r_foot`;
        linkXY(cubes, upper, `collector_leg_${number}_thigh`, hip, knee, 5.0 - index * .18, 5.6 - index * .20, .30);
        centred(cubes, upper, `collector_leg_${number}_knee`, knee, [5.5 - index * .18, 3.0, 5.2 - index * .16], .34);
        linkXY(cubes, lower, `collector_leg_${number}_shin`, knee, foot, 3.8 - index * .14, 4.3 - index * .16, .24);
        const separatedArch = index < 6;
        centred(cubes, footBone, `collector_leg_${number}_pad`, [foot[0], .42, foot[2]], [separatedArch ? 5.0 : 6.4 - index * .18, 1.4, separatedArch ? 5.0 : 5.2 - index * .16], .34);
    });

    // A small subordinate tail and six feeler links finish the silhouette
    // without widening the aspect ratio past the reference subject bounds.
    const tail = [[53.0, 5.6], [59.0, 4.6], [64.0, 3.7]];
    tail.forEach(([x, y], index) => {
        const from = index === 0 ? [50.2, 5.7, 0] : [tail[index - 1][0], tail[index - 1][1], 0];
        linkXY(cubes, index === 0 ? 'tail_base' : `tail_${index + 1}`, `collector_tail_${index + 1}`, from, [x, y, 0], 3.2 - index * .45, 6.4 - index * .8, .18);
    });
    for (const side of [-1, 1]) {
        const tag = side < 0 ? 'l' : 'r';
        const points = [[-20.0, 10.5], [-24.0, 6.0], [-27.0, 1.4]];
        points.forEach(([x, y], index) => {
            const from = index === 0 ? [-18.6, 12.0, side * 5.2] : [points[index - 1][0], points[index - 1][1], side * (5.6 + index * .7)];
            linkXY(cubes, `feeler_${tag}_${index + 1}`, `collector_feeler_${tag}_${index + 1}`, from, [x, y, side * (6.0 + index * .7)], .95 - index * .14, 1.35 - index * .18, .06);
        });
    }

    for (const cube of cubes) {
        cube.from[0] *= .86;
        cube.size[0] *= .86;
        if (cube.pivot) cube.pivot[0] *= .86;
    }
    return cubes;
}

function legacyCollectorCubes() {
    const cubes = [];

    // The Collector is a slow six-legged hauler: a low draped front, six
    // individually readable pressure sacs, and a tail narrower than its body.
    rotatedXY(cubes, 'underbody', 'collector_low_belly', [2.8, 10.25, 0], 29.5, 4.3, 10.8, -1, .34);
    rotatedXY(cubes, 'underbody', 'collector_belly_keel', [2.2, 8.45, 0], 23.2, 1.3, 7.6, -2, .18);
    centred(cubes, 'underbody', 'collector_near_rib', [-1.5, 11.2, 5.0], [18.0, 2.3, 2.3], .15);
    centred(cubes, 'underbody', 'collector_far_rib', [-1.5, 11.2, -5.0], [18.0, 2.3, 2.3], .15);

    const sacs = [
        // The front pair are deliberately broad but low. Their outer lobes
        // own the silhouette while the under-core stays below it; this is how
        // the reference reads as separate weighted bags, never as a canopy.
        [-14.1, 11.1, 17.0, 15.5, 17.0],
        [-.6, 11.85, 16.5, 16.0, 18.0],
        [10.9, 10.6, 13.0, 9.5, 12.8],
        [20.2, 10.9, 9.0, 8.0, 10.1],
        [26.8, 9.4, 6.5, 5.8, 8.4],
        [31.7, 8.45, 4.4, 4.0, 6.7]
    ];
    // Six discrete load sacs use the same anatomy grammar. Explicit cap tops
    // establish a continuous, descending dorsal rhythm regardless of each
    // sac's physical width; narrow gaps retain their individual silhouettes.
    const capTops = [15.6, 14.8, 13.8, 12.7, 10.8, 9.5];
    sacs.forEach(([x, _y, width, height, depth], index) => {
        const name = `collector_sac_${index + 1}`;
        const baseHeight = Math.max(2.2, height * .38);
        const capHeight = Math.max(1.8, height * .26);
        const capCenterY = capTops[index] - capHeight / 2;
        const baseCenterY = capCenterY - baseHeight / 2 + .72;
        if (index === 0) {
            // The foremost load is one wide low organic mass: two rounded
            // flanks overlap under a short inset cap, while a lower rear lobe
            // hands its weight into sac two instead of forming a flat roof.
            centred(cubes, 'carapace', `${name}_under_core`, [-9.2, 9.4, 0], [8.8, 5.6, 15.0], .48);
            rotatedXY(cubes, 'carapace', `${name}_fore_shoulder`, [-19.0, 12.2, 0], 8.6, 5.0, 14.8, 21, .47);
            rotatedXY(cubes, 'carapace', `${name}_rear_shoulder`, [-11.0, 11.9, 0], 8.0, 4.8, 14.6, -20, .46);
            centred(cubes, 'carapace', `${name}_inset_cap`, [-15.1, 14.2, 0], [3.0, 2.4, 12.4], .44);
        } else if (index === 1) {
            // Sac two follows the dominant first lobe as a slightly lower
            // four-part dome: two flanks, a short inset cap and a lower rear
            // lobe hand the weight into the third sac without a flat shelf.
            centred(cubes, 'carapace', `${name}_under_core`, [3.0, 8.7, 0], [8.0, 5.2, 15.2], .46);
            rotatedXY(cubes, 'carapace', `${name}_fore_shoulder`, [-6.1, 11.5, 0], 7.8, 4.5, 15.0, 20, .45);
            rotatedXY(cubes, 'carapace', `${name}_rear_shoulder`, [1.4, 11.2, 0], 7.4, 4.4, 14.8, -20, .44);
            centred(cubes, 'carapace', `${name}_inset_cap`, [-2.4, 13.0, 0], [2.6, 2.0, 12.8], .42);
        } else if (index === 2) {
            // Sac three continues the cascade as a full lower lobe: two
            // flanks overlap below a compact cap and a rear lobe hands the
            // load to sac four without re-forming a continuous roof.
            centred(cubes, 'carapace', `${name}_under_core`, [15.0, 8.2, 0], [8.0, 5.2, 13.2], .45);
            rotatedXY(cubes, 'carapace', `${name}_fore_shoulder`, [4.2, 10.5, 0], 8.5, 5.0, 13.6, 20, .44);
            rotatedXY(cubes, 'carapace', `${name}_rear_shoulder`, [12.0, 10.2, 0], 8.0, 4.8, 13.4, -20, .43);
            centred(cubes, 'carapace', `${name}_inset_cap`, [8.0, 12.0, 0], [3.0, 2.1, 11.6], .40);
        } else if (index === 3) {
            // Sac four compresses the broad front loads into the rear chain.
            // Four short overlapping masses retain a low round lobe and an
            // intentionally recessed cap, rather than prolonging a roofline.
            rotatedXY(cubes, 'carapace', `${name}_under_core`, [x - width * .90, capCenterY - height * .15, 0], width * .55, height * .37, depth * .82, 25, .44);
            rotatedXY(cubes, 'carapace', `${name}_fore_shoulder`, [x - width * .95, capCenterY - height * .03, 0], width * .38, height * .28, depth * .74, 26, .41);
            centred(cubes, 'carapace', `${name}_inset_cap`, [x - width * .72, capCenterY + height * .04, 0], [width * .20, height * .22, depth * .68], .42);
            rotatedXY(cubes, 'carapace', `${name}_rear_shoulder`, [x - width * .42, capCenterY - height * .12, 0], width * .42, height * .34, depth * .78, -22, .43);
        } else if (index === 4) {
            // Sac five is a compact penultimate dome. Its four short masses
            // keep a rounded step between sac four and the terminal load.
            rotatedXY(cubes, 'carapace', `${name}_under_core`, [x - width * 1.58, capCenterY - height * .15, 0], width * .55, Math.max(2.0, height * .37), depth * .82, 25, .42);
            rotatedXY(cubes, 'carapace', `${name}_fore_shoulder`, [x - width * 1.68, capCenterY - height * .03, 0], width * .38, Math.max(1.6, height * .28), depth * .74, 26, .39);
            centred(cubes, 'carapace', `${name}_inset_cap`, [x - width * 1.47, capCenterY + height * .04, 0], [width * .20, Math.max(1.5, height * .22), depth * .68], .40);
            rotatedXY(cubes, 'carapace', `${name}_rear_shoulder`, [x - width * 1.22, capCenterY - height * .12, 0], width * .42, Math.max(1.5, height * .34), depth * .78, -22, .41);
        } else if (index === 5) {
            // The final sac is the smallest low dome before the tail. Its
            // four short masses make a subordinate rounded hand-off, instead
            // of extending the dorsal chain as a flat terminal strip.
            rotatedXY(cubes, 'carapace', `${name}_under_core`, [x - width * 3.04, capCenterY - height * .15, 0], width * .55, Math.max(2.0, height * .37), depth * .80, 25, .40);
            rotatedXY(cubes, 'carapace', `${name}_fore_shoulder`, [x - width * 3.12, capCenterY - height * .03, 0], width * .38, Math.max(1.5, height * .28), depth * .72, 26, .37);
            centred(cubes, 'carapace', `${name}_inset_cap`, [x - width * 2.94, capCenterY + height * .04, 0], [width * .20, Math.max(1.4, height * .22), depth * .66], .38);
            rotatedXY(cubes, 'carapace', `${name}_rear_shoulder`, [x - width * 2.68, capCenterY - height * .12, 0], width * .42, Math.max(1.4, height * .34), depth * .76, -21, .39);
        } else {
            centred(cubes, 'carapace', `${name}_under_core`, [x - width * .04, baseCenterY, 0], [width * .74, baseHeight, depth * .84], .48);
        }
        if (index !== 0 && index !== 1 && index !== 2 && index !== 3 && index !== 4 && index !== 5) {
            centred(cubes, 'carapace', `${name}_cap`, [x - width * .08, capCenterY, 0], [width * .44, capHeight, depth * .72], .42);
        }
    });

    // Three broad overlapping curtain masses descend from sac one to the
    // ground line before the first leg arch: a high inner attachment, a wide
    // central curtain and a grounded lower weight. Their overlap makes the
    // front a continuous heavy drape rather than a narrow diagonal support.
    // The first load grows directly out of the ground drape. Its outer face
    // is a single broad incline from sac one into the curtain, avoiding the
    // former high square head block while retaining a heavy leading mass.
    linkXY(cubes, 'head', 'collector_head_rear_sac', [-16.8, 13.2, 0], [-25.4, 5.2, 0], 5.6, 15.0, .48);
    centred(cubes, 'head', 'collector_head_rear_sac_rear_flank', [-16.8, 9.5, 0], [9.0, 6.3, 14.4], .46);
    centred(cubes, 'head', 'collector_head_rear_sac_cap', [-20.0, 13.05, 0], [6.2, 3.5, 12.4], .42);
    linkXY(cubes, 'head', 'collector_head_drape', [-18.7, 12.0, 0], [-22.9, 7.2, 0], 7.4, 16.6, .52);
    linkXY(cubes, 'head', 'collector_head_drape_mid', [-22.3, 9.0, 0], [-26.0, 3.8, 0], 6.5, 15.6, .50);
    linkXY(cubes, 'head', 'collector_head_drape_lower', [-25.2, 5.5, 0], [-30.6, -.6, 0], 4.9, 14.4, .46);

    const legX = [-10.8, -4.5, 2.1, 8.4, 14.4, 19.5];
    for (const [index, x] of legX.entries()) for (const side of [-1, 1]) {
        const tag = `${index + 1}_${side < 0 ? 'l' : 'r'}`;
        const nearestFrontLeg = index === 0 && side > 0;
        const nearestSecondLeg = index === 1 && side > 0;
        const nearestThirdLeg = index === 2 && side > 0;
        const nearestFourthLeg = index === 3 && side > 0;
        const prominentNearArch = nearestFrontLeg || nearestSecondLeg || nearestThirdLeg || nearestFourthLeg;
        const upper = `leg_${tag}_upper`;
        const lower = `leg_${tag}_lower`;
        const foot = `leg_${tag}_foot`;
        const z = side * 5.4;
        const hip = nearestFourthLeg
            ? [x + 16.5, 12.35 + (index % 2) * .25, z]
            : nearestThirdLeg
                ? [x + 6.6, 12.35 + (index % 2) * .25, z]
            : [x, 12.35 + (index % 2) * .25, z];
        const knee = nearestFrontLeg
            ? [x - 5.8, 7.8, side * 8.25]
            : nearestSecondLeg
                ? [x - 3.8, 7.45, side * 8.2]
                : nearestThirdLeg
                    ? [x - 3.5, 7.35, side * 8.15]
                    : nearestFourthLeg
                        ? [x + 17.3, 7.5, side * 8.1]
            : [x + [-3.3, -2.45, -1.15, 1.15, 2.45, 3.25][index], 6.35 + (index % 3) * .35, side * 7.35];
        const toe = nearestFrontLeg
            ? [x + 1.3, .55, side * 9.5]
            : nearestSecondLeg
                ? [x + 11.6, .55, side * 9.45]
                : nearestThirdLeg
                    ? [x + 11.9, .55, side * 9.4]
                    : nearestFourthLeg
                        ? [x + 18.8, .55, side * 9.35]
            : [x + [-.95, -.25, .85, 2.6, 4.05, 5.1][index], .66, side * 8.1];
        // Deliberately wide two-segment load arches: each thigh bends well
        // away from its planted foot, then the taper returns into a broad
        // pad. Their overlap under the belly replaces the old picket fence.
        linkXY(cubes, upper, `collector_leg_${tag}_thigh`, hip, knee, prominentNearArch ? 5.15 : 4.15, prominentNearArch ? 5.55 : 4.55, .25);
        ring(cubes, upper, `collector_leg_${tag}_knee`, knee, [prominentNearArch ? 6.0 : 4.85, 0, prominentNearArch ? 5.8 : 5.0]);
        linkXY(cubes, lower, `collector_leg_${tag}_shin`, knee, toe, prominentNearArch ? 3.95 : 3.35, prominentNearArch ? 4.35 : 3.85, .20);
        const shinMid = [(knee[0] + toe[0]) / 2, (knee[1] + toe[1]) / 2, (knee[2] + toe[2]) / 2];
        ring(cubes, lower, `collector_leg_${tag}_shin_collar`, shinMid, [prominentNearArch ? 4.25 : 3.65, 0, prominentNearArch ? 4.45 : 3.95]);
        centred(cubes, foot, `collector_leg_${tag}_pad`, [toe[0] - .20, .40, toe[2]], [prominentNearArch ? 6.6 : 5.65, 1.4, prominentNearArch ? 5.4 : 4.85], .32);
        centred(cubes, foot, `collector_leg_${tag}_toe_a`, [toe[0] - 1.45, .13, toe[2] + side * 1.1], [2.45, .52, 1.35], .10);
        centred(cubes, foot, `collector_leg_${tag}_toe_b`, [toe[0] + 1.45, .13, toe[2] - side * 1.1], [2.45, .52, 1.35], .10);
    }

    // A narrow, three-stage taper continues directly under the sixth sac. It
    // owns the rear contour but cannot compete with the dorsal body mass.
    const tail = [[31.2, 11.6], [37.6, 9.9], [43.2, 8.2]];
    for (let index = 0; index < tail.length; index++) {
        const owner = index === 0 ? 'tail_base' : `tail_${index + 1}`;
        const from = index === 0 ? [28.0, 12.6, 0] : [tail[index - 1][0], tail[index - 1][1], 0];
        const to = [tail[index][0], tail[index][1], 0];
        linkXY(cubes, owner, `collector_tail_${index + 1}`, from, to, 3.0 - index * .4, 6.4 - index * .78, .16);
    }
    for (const side of [-1, 1]) {
        const tag = side < 0 ? 'l' : 'r';
        const chain = [[-19.5, 11.4], [-22.0, 8.1], [-23.9, 4.9], [-25.3, 1.8], [-26.1, .2]];
        for (let index = 0; index < chain.length; index++) {
            const from = index === 0 ? [-18.0, 12.5, side * 4.9] : [chain[index - 1][0], chain[index - 1][1], side * (5.0 + index * .55)];
            const to = [chain[index][0], chain[index][1], side * (5.5 + index * .65)];
            linkXY(cubes, `feeler_${tag}_${index + 1}`, `collector_feeler_${tag}_${index + 1}`, from, to, 1.05 - index * .12, 1.30 - index * .14, .05);
        }
    }
    // The direct reference contour is compact. Scale the entire authored
    // projection once, rather than leaving a correctly shaped creature with
    // a tail/feeler envelope that makes the primary aspect ratio too wide.
    for (const cube of cubes) {
        cube.from[0] *= .86;
        cube.size[0] *= .86;
        if (cube.pivot) cube.pivot[0] *= .86;
    }
    return cubes;
}

function crusherCubes() {
    const cubes = [];

    // The Crusher is deliberately front-heavy. Its dorsal arc spills into a
    // compact downward head and two oversized, low crushing knuckles.
    rotatedXY(cubes, 'torso', 'crusher_low_belly', [1.8, 11.2, 0], 22.5, 4.1, 10.0, 2, .30);
    // One deep, continuous bridge carries the accepted fore-carapace into
    // the rear hips. It replaces the former thin dorsal beam with the long
    // armoured torso mass that makes this creature shoulder-heavy.
    linkXY(cubes, 'torso', 'crusher_shoulder_cap', [-8.8, 14.0, 0], [.1, 11.85, 0], 5.8, 14.6, .64);
    // The dense rear taper resolves the bridge into the hip instead of
    // carrying its full slab thickness all the way above the hind legs.
    linkXY(cubes, 'torso', 'crusher_shoulder_cap_rear_taper', [-.15, 11.78, 0], [8.0, 10.55, 0], 2.65, 8.1, .38);
    // A shared sloped overlap removes the hard bridge-to-hip step on both
    // contours while preserving the fast rear taper underneath it.
    linkXY(cubes, 'torso', 'crusher_shoulder_cap_hip_transition', [-1.1, 12.0, 0], [4.1, 11.08, 0], 4.15, 11.4, .48);
    // One logical crown is built from three deeply overlapping facets: a
    // shoulder-high front, rounded central brow and lower rear run. Together
    // their exterior rises once and falls once—an actual convex carapace,
    // not a thin ramp, detached tooth or vertical rear wall.
    rotatedXY(cubes, 'torso', 'crusher_shoulder_crown_front', [-10.1, 14.4, 0], 10.0, 2.8, 14.4, 17, .58);
    centred(cubes, 'torso', 'crusher_shoulder_cap_near_fibre', [-.4, 12.15, 5.8], [13.4, .76, 3.6], .26);
    centred(cubes, 'torso', 'crusher_shoulder_cap_far_fibre', [-.4, 12.15, -5.8], [13.4, .76, 3.6], .26);
    // The exposed forequarter is one deep armoured drop from the shoulder
    // crown into the compact head, rather than a stack of separate panels.
    linkXY(cubes, 'head', 'crusher_head_wedge', [-11.45, 14.15, 6.0], [-16.0, 6.15, 6.7], 5.45, 9.1, .45);
    rotatedXY(cubes, 'head', 'crusher_head_beak', [-14.7, 7.6, 6.7], 5.3, 2.75, 6.2, -58, .32);
    // A foreground, downward nested head fills the V between the planted
    // crushers. These three tapered volumes are deliberately smaller than
    // either arm, so it reads as anatomy rather than another terminal pad.
    rotatedXY(cubes, 'head', 'crusher_head_visible_drop', [-12.8, 9.45, 8.7], 6.9, 3.75, 6.35, -51, .38);
    rotatedXY(cubes, 'head', 'crusher_head_visible_nose', [-15.25, 6.0, 9.1], 4.8, 2.45, 4.85, -62, .28);
    rotatedXY(cubes, 'head', 'crusher_head_pendant_tip', [-16.85, 3.55, 9.25], 2.9, 1.35, 3.05, -44, .16);
    centred(cubes, 'head', 'crusher_head_near_plate', [-12.6, 12.25, 5.5], [4.8, 3.9, 1.65], .24);
    centred(cubes, 'head', 'crusher_head_far_plate', [-12.6, 12.25, -4.3], [4.8, 3.9, 1.65], .24);

    for (const side of [-1, 1]) {
        const tag = side < 0 ? 'l' : 'r';
        // The primary reference camera sits on positive Z. The left rig is
        // deliberately presented beside (not hidden behind) the right rig so
        // the two low crusher pads read as an intentional near-side clamp.
        const visiblePartner = side < 0;
        const nearCrusher = side > 0;
        const projectedSide = visiblePartner ? 1 : side;
        const profileOffset = visiblePartner ? 3.2 : 0;
        const z = projectedSide * (visiblePartner ? 5.9 : 6.8);
        // The near arm is deliberately a second, independently grounded
        // crusher.  Keeping its shoulder closer to the chest and its terminal
        // below/right of the head leaves the head in a real V between the two
        // crushing limbs instead of merging both arms into one shovel shape.
        const shoulder = nearCrusher ? [-7.3, 13.2, z] : [-8.6 + profileOffset, 13.7, z];
        const elbow = nearCrusher
            ? [-9.7, 9.2, projectedSide * 8.1]
            : [-11.1 + profileOffset, 9.1, projectedSide * 7.45];
        const knuckle = nearCrusher
            ? [-11.6, 4.8, projectedSide * 8.65]
            : [-14.8 + profileOffset, 4.3, projectedSide * 8.25];
        // Two low, overlapping plates carry the silhouette. The smaller
        // cords are recessed so the arm reads as one crushing shield rather
        // than a tall chevron made of independent bars.
        linkXY(cubes, `crusher_arm_${tag}`, `crusher_${tag}_upper_mass`, shoulder, elbow, 4.85, 4.65, .34);
        rotatedXY(cubes, `crusher_arm_${tag}`, `crusher_${tag}_upper_plate`,
            nearCrusher ? [-8.5, 11.25, projectedSide * 7.8] : [-9.9 + profileOffset, 11.55, projectedSide * 7.45],
            5.9, 2.30, 5.25, 62, .25);
        rotatedXY(cubes, `crusher_arm_${tag}`, `crusher_${tag}_upper_cord`,
            nearCrusher ? [-8.25, 12.1, projectedSide * 8.9] : [-9.7 + profileOffset, 12.5, projectedSide * 8.45],
            4.65, .48, 1.12, 62, .06);
        ring(cubes, `crusher_arm_${tag}`, `crusher_${tag}_elbow`, elbow, [4.25, 0, 4.35]);
        linkXY(cubes, `crusher_arm_${tag}_forearm`, `crusher_${tag}_fore_mass`, elbow, knuckle, 5.1, 4.95, .36);
        rotatedXY(cubes, `crusher_arm_${tag}_forearm`, `crusher_${tag}_fore_plate`,
            nearCrusher ? [-10.75, 6.9, projectedSide * 8.45] : [-13.0 + profileOffset, 6.7, projectedSide * 8.3],
            6.3, 2.10, 5.45, 52, .22);
        rotatedXY(cubes, `crusher_arm_${tag}_forearm`, `crusher_${tag}_fore_cord`,
            nearCrusher ? [-10.55, 7.85, projectedSide * 9.4] : [-12.8 + profileOffset, 7.7, projectedSide * 9.25],
            5.15, .48, 1.08, 52, .06);
        const isRearClamp = side < 0;
        // Two independent armoured crusher pods replace the former horizontal
        // floor hammer.  Each is an inward angled shell connected to its own
        // bent forearm, ending in a smaller rounded contact mass.
        const pod = isRearClamp
            ? [-11.3, 2.55, projectedSide * 13.4]
            // Pull the near terminal forward/down and away from the head.
            // Its slimmer connecting neck leaves a full visual air channel
            // between the compact face and the armoured crusher pod.
            : [-20.1, 2.15, projectedSide * 13.2];
        const contact = isRearClamp
            ? [-11.85, .78, projectedSide * 13.85]
            : [-20.95, .62, projectedSide * 13.55];
        const angle = isRearClamp ? 14 : -22;
        linkXY(cubes, `crusher_arm_${tag}_fist`, `crusher_${tag}_pod_upper`, knuckle, pod, isRearClamp ? 4.75 : 3.25, 6.25, .36);
        if (!isRearClamp) {
            // Visible load-bearing link for the separated foreground pod. It
            // ends above its contact mass, preserving the required air gap
            // beside the compact head rather than rejoining the floor block.
            linkXY(cubes, `crusher_arm_${tag}_forearm`, `crusher_${tag}_pod_proximal_arm`,
                [-10.6, 10.0, projectedSide * 8.5], [-17.55, 4.35, projectedSide * 12.2], 4.45, 6.35, .34);
        }
        rotatedXY(cubes, `crusher_arm_${tag}_fist`, `crusher_${tag}_fist_block`, pod, 6.25, 3.55, 6.7, angle, .48);
        centred(cubes, `crusher_arm_${tag}_fist`, `crusher_${tag}_pod_contact`, contact, [4.65, 1.6, 5.15], .42);
        centred(cubes, `crusher_arm_${tag}_fist`, `crusher_${tag}_pod_knuckle`, [pod[0] + .45, pod[1] + 1.45, pod[2]], [3.85, 1.15, 4.2], .24);
    }

    const legs = [[4.6, -1], [4.6, 1], [10.7, -1], [10.7, 1]];
    legs.forEach(([x, side], index) => {
        const number = index + 1;
        const upper = `hind_leg_${number}_upper`;
        const lower = `hind_leg_${number}_lower`;
        const hip = [x, 13.0, side * 4.8];
        const knee = [x + 4.5, 9.0, side * 6.4];
        const foot = [x + 7.0, .7, side * 7.2];
        linkXY(cubes, upper, `crusher_hind_${number}_thigh`, hip, knee, 3.2, 3.2, .18);
        ring(cubes, upper, `crusher_hind_${number}_knee`, knee, [3.0, 0, 3.4]);
        linkXY(cubes, lower, `crusher_hind_${number}_shin`, knee, foot, 2.1, 2.45, .12);
        centred(cubes, lower, `crusher_hind_${number}_foot`, [foot[0] + .6, .42, foot[2]], [3.6, 1.0, 3.0], .20);
    });
    for (const side of [-1, 1]) {
        const tag = side < 0 ? 'l' : 'r';
        const points = [[11.0, 17.0], [15.5, 18.4], [20.0, 17.5], [24.4, 19.0], [29.0, 17.3]];
        for (let index = 0; index < points.length; index++) {
            const from = index === 0 ? [9.0, 16.0, side * 2.4] : [points[index - 1][0], points[index - 1][1], side * (2.2 + index * .55)];
            const to = [points[index][0], points[index][1], side * (2.7 + index * .65)];
            linkXY(cubes, `rear_tendril_${index + 1}`, `crusher_tendril_${tag}_${index + 1}`, from, to, .70 - index * .08, .72 - index * .08, .035);
        }
    }
    return cubes;
}

function scytheCubes() {
    const cubes = [];

    // Three overlapping dorsal volumes only: compact high front hump, lower
    // middle, then a narrow rear. The small belly stays beneath this train.
    rotatedXY(cubes, 'abdomen', 'scythe_low_thorax', [-2.8, 8.1, 0], 7.6, 1.85, 7.3, -4, .20);
    // The reference has a single deep lower volume under the crest rather
    // than a thin empty waist.  This deliberately broad, gently tapering
    // belly joins both scythe roots without raising the dorsal silhouette.
    rotatedXY(cubes, 'abdomen', 'scythe_lower_belly_mass', [-3.1, 9.0, 0], 10.0, 2.75, 9.0, -6, .28);
    // A compact three-volume dorsal arch: the broad front wedge is highest,
    // then two smaller lower sections flow continuously to the pelvis. This
    // keeps the reference's convex front-heavy hunch without a flat roof.
    rotatedXY(cubes, 'abdomen', 'scythe_front_biased_crest', [-6.85, 10.7, 0], 5.8, 2.3, 8.6, 21, .29);
    // Both rear pieces overlap the leading crest and each other by a full
    // shoulder, so their lowered upper edges form one descending arc rather
    // than exposing V-notches between three separate blocks.
    rotatedXY(cubes, 'abdomen', 'scythe_crest_mid', [-2.6, 9.3, 0], 8.2, 1.45, 7.7, -11, .24);
    rotatedXY(cubes, 'abdomen', 'scythe_crest_rear', [2.25, 8.5, 0], 5.8, .90, 6.0, -22, .17);
    // A shallow outer cap bridges their upper edges from the front peak to
    // rear thorax. The three underlying pieces retain volume, but primary
    // silhouette sees one clean descending crest rather than three steps.
    linkXY(cubes, 'abdomen', 'scythe_crest_outer_cap', [-7.35, 12.35, 0], [3.25, 9.8, 0], 1.65, 8.35, .20);
    // The rear terminus tapers into the thorax instead of stopping in a
    // vertical wall at the end of the outer cap.
    linkXY(cubes, 'abdomen', 'scythe_crest_cap_rear_taper', [3.05, 9.72, 0], [6.1, 8.78, 0], .82, 5.9, .10);
    rotatedXY(cubes, 'abdomen', 'scythe_tail_pelvis', [7.0, 8.55, 0], 1.7, .82, 4.6, -28, .10);
    // This broad diagonal carapace is the load-bearing bridge from the front
    // crest into both scythe roots. It visually absorbs the isolated square
    // head-cap and gives the animal one dense sloping fore-volume.
    linkXY(cubes, 'head', 'scythe_front_carapace', [-6.9, 11.9, 0], [-12.1, 8.9, 0], 4.5, 8.9, .36);
    // Compact downward-facing head, fully nested below the new front
    // carapace. This replaces the old tall rectangular cap in primary view.
    rotatedXY(cubes, 'head', 'scythe_head_core', [-12.35, 10.55, 0], 6.15, 3.15, 6.65, -38, .28);
    rotatedXY(cubes, 'head', 'scythe_head_brow', [-13.8, 9.35, 0], 3.95, 1.65, 6.9, -49, .15);
    centred(cubes, 'head', 'scythe_head_near_plate', [-12.2, 10.55, 3.45], [3.25, 2.15, 1.1], .13);
    centred(cubes, 'head', 'scythe_head_far_plate', [-12.2, 10.55, -3.45], [3.25, 2.15, 1.1], .13);

    for (const side of [-1, 1]) {
        const tag = side < 0 ? 'l' : 'r';
        const inner = side < 0;
        const farRoot = !inner;
        // Two explicitly independent hooks. Both begin in front of the
        // thorax, descend and then turn inward; their inner edges never meet
        // in primary view, preserving the reference's open paired silhouette.
        // Different root heights/fore-aft depths make this a paired scythe
        // gesture rather than two parallel walking legs. Tips below remain
        // unchanged; only the roots and their broad arcs are re-positioned.
        // Primary projection needs two independently readable hooks.  The
        // second root is deliberately carried forward/inward and to the
        // right, not hidden beneath the longer outside scythe.
        // The farther hook is materially farther back and slightly raised,
        // so primary view sees an unequal, nested pair rather than two
        // equally loud C-loops. The near hook remains low and forward.
        const yLift = farRoot ? .85 : 0;
        const zSetback = farRoot ? -4.1 : 0;
        const shoulder = inner ? [-.2, 12.9, 12.6] : [-11.2, 13.0 + yLift, 7.3 + zSetback];
        const elbow = inner ? [-1.9, 8.85, 12.2] : [-14.4, 9.2 + yLift, 7.8 + zSetback];
        const wrist = inner ? [-4.0, 5.25, 11.9] : [-17.0, 5.45 + yLift, 8.2 + zSetback];
        const arc = inner
            ? [wrist, [-5.3, 2.05, 11.3], [-5.4, -.65, 10.9], [-4.15, -2.7, 10.4], [-1.75, -3.75, 10.0], [.45, -3.55, 9.7]]
            : [wrist, [-18.3, 2.15 + yLift, 8.2 + zSetback], [-18.2, -.65 + yLift, 8.0 + zSetback], [-16.35, -2.85 + yLift, 7.8 + zSetback], [-13.55, -3.85 + yLift, 7.6 + zSetback], [-11.35, -3.65 + yLift, 7.4 + zSetback]];
        linkXY(cubes, `scythe_arm_${tag}`, `scythe_${tag}_upper_mass`, shoulder, elbow, 3.25, 3.8, .22);
        linkXY(cubes, `scythe_arm_${tag}`, `scythe_${tag}_upper_plate`, shoulder, elbow, .82, 4.25, .08);
        ring(cubes, `scythe_arm_${tag}`, `scythe_${tag}_elbow`, elbow, [3.1, 0, 3.75]);
        linkXY(cubes, `scythe_arm_${tag}_fore`, `scythe_${tag}_fore_mass`, elbow, wrist, 2.75, 3.4, .20);
        linkXY(cubes, `scythe_arm_${tag}_fore`, `scythe_${tag}_fore_plate`, elbow, wrist, .68, 3.8, .07);
        ring(cubes, `scythe_arm_${tag}_fore`, `scythe_${tag}_wrist`, wrist, [2.5, 0, 3.1]);
        // Only the inner return and tip taper: the root remains a broad
        // blade, while both ends sweep inward to a deliberately sharp point.
        // The two broad middle plates are the actual downward-and-inward
        // cutting sweep.  They must read as blades, not as a second pair of
        // walking shins; only the final short link resolves to a sharp tip.
        // The rear/upper hook recedes in depth, so its whole cutting return
        // tapers sooner. It must read as a blade behind the foreground hook,
        // never as an equally heavy second C-ring.
        const widths = inner ? [4.15, 3.25, 2.25, 1.12, .28] : [2.85, 2.10, 1.38, .68, .20];
        const depths = inner ? [4.25, 3.10, 2.10, 1.12, .44] : [3.25, 2.25, 1.48, .76, .32];
        for (let index = 0; index < arc.length - 1; index++) {
            linkXY(cubes, `scythe_arm_${tag}_blade`, `scythe_${tag}_blade_plate_${index + 1}`, arc[index], arc[index + 1], widths[index], depths[index], index < 2 ? .10 : .03);
        }
    }
    const legs = [
        [-3.0, -5.8, -7.1, -8.0], [-3.0, 5.8, 7.1, 8.0],
        [2.8, -3.9, -5.4, -6.5], [2.8, 3.9, 5.4, 6.5],
        [7.6, -2.5, -3.5, -4.3], [7.6, 2.5, 3.5, 4.3]
    ];
    legs.forEach(([x, hipZ, kneeZ, footZ], index) => {
        const number = index + 1;
        const upper = `stalker_leg_${number}_upper`;
        const lower = `stalker_leg_${number}_lower`;
        const footX = [-11.0, -5.0, .8, 6.8, 11.8, 15.0][index];
        const hip = [x, 12.4, hipZ];
        const knee = [x + (footX - x) * .50, 6.6 + (index % 3) * .45, kneeZ];
        const foot = [footX, .55, footZ];
        linkXY(cubes, upper, `scythe_leg_${number}_thigh`, hip, knee, 1.45, 1.55, .10);
        ring(cubes, upper, `scythe_leg_${number}_knee`, knee, [1.55, 0, 1.75]);
        linkXY(cubes, lower, `scythe_leg_${number}_shin`, knee, foot, 1.15, 1.20, .07);
        centred(cubes, lower, `scythe_leg_${number}_toe`, [foot[0] - .45, .18, foot[2]], [2.25, .42, 1.10], .04);
    });
    for (const side of [-1, 1]) {
        const tag = side < 0 ? 'l' : 'r';
        const points = [[7.8, 14.8], [10.7, 15.6], [13.6, 15.0], [16.6, 16.0], [19.4, 15.2]];
        for (let index = 0; index < points.length; index++) {
            const from = index === 0 ? [6.6, 14.0, side * 2.1] : [points[index - 1][0], points[index - 1][1], side * (2.0 + index * .5)];
            const to = [points[index][0], points[index][1], side * (2.5 + index * .6)];
            linkXY(cubes, `whip_${tag}_${index + 1}`, `scythe_whip_${tag}_${index + 1}`, from, to, .60 - index * .07, .65 - index * .08, .03);
        }
    }
    return cubes;
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

function tuneAnimations(name, animationData) {
    if (name === 'crusher_stalker') {
        const crush = animationData.animations['animation.crusher_stalker.crush'];
        if (!crush) throw new Error('crusher_stalker: missing crush animation');
        // Animate only the shoulder groups: their nested forearms/fists carry
        // the finished articulated chain. This prevents a double transform
        // from shearing a fist away from a crushing arm at the impact frame.
        crush.bones = {
            root: { position: { 0: [0, 0, 0], '0.35': [0, -.72, 0], '1.1': [0, 0, 0] } },
            crusher_arm_l: { rotation: { 0: [0, 0, 0], '0.18': [0, 0, -10], '0.35': [0, 0, 20], '1.1': [0, 0, 0] } },
            crusher_arm_r: { rotation: { 0: [0, 0, 0], '0.18': [0, 0, 10], '0.35': [0, 0, -20], '1.1': [0, 0, 0] } }
        };
        return animationData;
    }
    if (name === 'scythe_stalker') {
        const slash = animationData.animations['animation.scythe_stalker.slash'];
        if (!slash) throw new Error('scythe_stalker: missing slash animation');
        // The former clip rotated every nested arm bone independently. With
        // the finished articulated silhouette that double-transform detached
        // the blade in the peak frame. Rotate each whole arm chain once.
        slash.bones = {
            root: { position: { 0: [0, 0, 0], '0.20': [.45, .18, 0], '0.42': [-.85, -.34, 0], '0.95': [0, 0, 0] } },
            scythe_arm_l: { rotation: { 0: [0, 0, 0], '0.20': [0, 0, -13], '0.42': [0, 0, 27], '0.95': [0, 0, 0] } },
            scythe_arm_r: { rotation: { 0: [0, 0, 0], '0.20': [0, 0, 13], '0.42': [0, 0, -27], '0.95': [0, 0, 0] } }
        };
    }
    return animationData;
}

function rebuild(name, cubes) {
    const projectFile = path.join(projectRoot, `${name}.bbmodel`);
    const geoFile = path.join(geoRoot, `${name}.geo.json`);
    const animationFile = path.join(animationRoot, `${name}.animation.json`);
    const project = readJson(projectFile);
    const geo = readJson(geoFile);
    const animation = tuneAnimations(name, readJson(animationFile));
    const model = geo['minecraft:geometry'][0];
    const bones = new Map(model.bones.map(bone => [bone.name, bone]));
    const groups = new Map(project.groups.map(group => [group.name, group]));
    const textureUuid = project.textures[0]?.uuid;
    if (!textureUuid) throw new Error(`${name}: Blockbench project has no texture UUID`);
    for (const cube of cubes) if (!bones.has(cube.owner) || !groups.has(cube.owner)) throw new Error(`${name}: unknown owner ${cube.owner} for ${cube.name}`);

    // Models are authored in a coherent finished rest-pose layout. The old
    // imported per-bone rotations belonged to the discarded generated forms
    // and folded fresh anatomy into a lump, so only animation supplies motion.
    for (const bone of bones.values()) {
        bone.cubes = [];
        if (bone.rotation) bone.rotation = [0, 0, 0];
    }
    for (const group of groups.values()) if (group.rotation) group.rotation = [0, 0, 0];

    for (const cube of cubes) {
        const geometryCube = {
            origin: [-(cube.from[0] + cube.size[0]), cube.from[1], cube.from[2]],
            size: cube.size,
            uv: textureOffset(name, cube),
            inflate: cube.inflate
        };
        if (cube.rotation) {
            geometryCube.pivot = [-cube.pivot[0], cube.pivot[1], cube.pivot[2]];
            geometryCube.rotation = [-cube.rotation[0], cube.rotation[1], cube.rotation[2]];
        }
        bones.get(cube.owner).cubes.push(geometryCube);
    }

    project.elements = cubes.map((cube, index) => {
        const element = {
            name: cube.name,
            from: cube.from,
            to: cube.from.map((value, axis) => value + cube.size[axis]),
            origin: cube.pivot ?? [0, 0, 0],
            uv_offset: textureOffset(name, cube),
            color: hash(cube.name) % 8,
            autouv: 0,
            inflate: cube.inflate,
            faces: Object.fromEntries(Object.entries(faceUvs(textureOffset(name, cube), cube.size)).map(([face, uv]) => [face, {
                uv, texture: textureUuid
            }])),
            type: 'cube',
            uuid: uuid(`${name}:anatomical-cube:${index}:${cube.name}`)
        };
        if (cube.rotation) element.rotation = cube.rotation;
        return element;
    });
    const elementByOwner = new Map();
    cubes.forEach((cube, index) => {
        const ids = elementByOwner.get(cube.owner) ?? [];
        ids.push(project.elements[index].uuid);
        elementByOwner.set(cube.owner, ids);
    });
    const children = new Map();
    for (const bone of model.bones) if (bone.parent) {
        const names = children.get(bone.parent) ?? [];
        names.push(bone.name);
        children.set(bone.parent, names);
    }
    const node = boneName => {
        const group = groups.get(boneName);
        return { uuid: group.uuid, isOpen: false, children: [...(elementByOwner.get(boneName) ?? []), ...(children.get(boneName) ?? []).map(node)] };
    };
    project.outliner = model.bones.filter(bone => !bone.parent).map(bone => node(bone.name));
    project.animations = makeBlockbenchAnimations(animation, project.groups);
    const outputs = [[geoFile, geo], [animationFile, animation], [projectFile, project]];
    for (const [file, value] of outputs) {
        const next = `${JSON.stringify(value, null, 2)}\n`;
        if (checkOnly) {
            if (fs.readFileSync(file, 'utf8') !== next) {
                throw new Error(`${name}: generated source is stale: ${path.relative(root, file)}; run node tools/rebuild_harvester_anatomical_models.mjs`);
            }
        } else {
            writeJson(file, value);
        }
    }
    const usedBones = model.bones.filter(bone => bone.cubes.length).length;
    console.log(`${name}: ${cubes.length} anatomical cuboids across ${usedBones}/${model.bones.length} bones${checkOnly ? ' (current)' : ''}`);
}

rebuild('biomass_collector', collectorCubes());
rebuild('crusher_stalker', crusherCubes());
rebuild('scythe_stalker', scytheCubes());
