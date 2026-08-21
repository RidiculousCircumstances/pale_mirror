#!/usr/bin/env node

// Reference-first visual evidence for the editable Harvester Blockbench assets.
// This deliberately captures the normal desktop Blockbench display. It creates
// evidence and an incomplete independent-review scorecard; it never tries to infer likeness
// from cuboid count, JSON validity, or a hi-fi construction source.

import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { setTimeout as sleep } from 'node:timers/promises';

const root = path.resolve(import.meta.dirname, '..');
const sourceRoot = path.join(root, 'pale-mirror-visuals', 'src', 'main');
const harvesterRoot = path.join(sourceRoot, 'blockbench', 'harvester');
const assetRoot = path.join(sourceRoot, 'resources', 'assets', 'pale_mirror_visuals');
const briefs = readJson(path.join(harvesterRoot, 'review_briefs.json'));
const neutralViews = briefs.required_neutral_views;
const silhouetteViews = briefs.required_silhouette_frames;
const animationViews = briefs.required_animation_frames;
const supportedCreatures = Object.keys(briefs.creatures);
const scoreKeys = [
    'primary_silhouette_and_proportions',
    'primary_anatomical_landmarks',
    'diagnostic_view_volume',
    'limb_hierarchy_and_joints',
    'material_and_animation_readability'
];

function readJson(file) {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
}

function writeJson(file, value) {
    fs.writeFileSync(file, `${JSON.stringify(value, null, 2)}\n`);
}

function sha256(file) {
    return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
}

function relativeToRoot(file) {
    return path.relative(root, file).split(path.sep).join('/');
}

function parseArgs(argv) {
    const options = { capture: false, finalize: null, previous: null, iteration: 'iteration' };
    for (let index = 0; index < argv.length; index++) {
        const argument = argv[index];
        if (argument === '--capture') options.capture = true;
        else if (argument === '--creature') options.creature = argv[++index];
        else if (argument === '--iteration') options.iteration = argv[++index];
        else if (argument === '--references-root') options.referencesRoot = argv[++index];
        else if (argument === '--previous') options.previous = argv[++index];
        else if (argument === '--audit-dir') options.auditDir = argv[++index];
        else if (argument === '--blockbench-bin') options.blockbenchBin = argv[++index];
        else if (argument === '--display') options.display = argv[++index];
        else if (argument === '--port') options.port = Number(argv[++index]);
        else if (argument === '--finalize') options.finalize = argv[++index];
        else if (argument === '--help') options.help = true;
        else throw new Error(`Unknown argument: ${argument}`);
    }
    return options;
}

function usage() {
    return `Usage:
  node tools/harvester_visual_audit.mjs --creature <${supportedCreatures.join('|')}> --iteration <label> --references-root <directory> [--capture] [--display <X11-display>] [--previous <audit-dir>]
  node tools/harvester_visual_audit.mjs --finalize <audit-dir>

The first command creates a human-review package. --capture opens the runtime
.bbmodel in normal Blockbench display and writes one solid-silhouette, five
neutral, and four animation screenshots. The second command checks a
independent-reviewer-completed review.json.`;
}

function requireFile(file, message) {
    if (!file || !fs.existsSync(file)) throw new Error(`${message}: ${file ?? '(missing)'}`);
    return path.resolve(file);
}

function dateStamp() {
    return new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z');
}

function slug(value) {
    const result = String(value ?? '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
    if (!result) throw new Error('Iteration label must contain at least one latin letter or digit');
    return result;
}

function elementInspection(modelFile) {
    const project = readJson(modelFile);
    const elements = project.elements ?? [];
    const dimensions = elements.map(element => (element.to ?? []).map((value, index) => Math.abs(value - ((element.from ?? [])[index] ?? 0))));
    const keys = dimensions.map(size => size.map(value => value.toFixed(3)).join('×'));
    const frequency = new Map();
    for (const key of keys) frequency.set(key, (frequency.get(key) ?? 0) + 1);
    const [dominantSize = 'n/a', dominantCount = 0] = [...frequency.entries()]
        .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))[0] ?? [];
    const dominantRatio = elements.length ? dominantCount / elements.length : 0;
    return {
        elements: elements.length,
        distinct_dimensions: frequency.size,
        dominant_dimension: dominantSize,
        dominant_dimension_ratio: Number(dominantRatio.toFixed(3)),
        uniform_cuboid_distribution_warning: elements.length >= 250 && dominantRatio >= 0.65,
        warning_note: 'This is a technical prompt for the reviewer, not a visual score or acceptance decision.'
    };
}

function cameraDefinitions(brief) {
    const [x, y, z] = brief.camera_target;
    const distance = brief.camera_distance;
    const view = position => ({ position, target: [x, y, z], zoom: brief.orthographic_zoom });
    return {
        // The supplied baseline is primarily a side/front view: retaining
        // the horizontal body axis is more diagnostic than a symmetric
        // diagonal that visually collapses it.
        primary_reference: view([x + distance * .24, y + distance * .12, z + distance * .97]),
        opposite_three_quarter: view([x + distance * .72, y + distance * .25, z + distance * .72]),
        front: view([x, y + distance * .08, z - distance]),
        side: view([x - distance, y + distance * .08, z]),
        elevated_rear: view([x + distance * .25, y + distance * .72, z + distance * .72])
    };
}

function buildReview({ creature, brief, referenceFile, modelFile, geoFile, animationFile, previous }) {
    return {
        schema: 'pale_mirror_visuals.harvester_visual_review.v1',
        status: 'PENDING_REVIEW',
        creature,
        reviewer: null,
        created_at: new Date().toISOString(),
        baseline: {
            sole_likeness_authority: relativeToRoot(referenceFile),
            source_sha256: sha256(referenceFile),
            primary_view: brief.primary_view,
            required_landmarks: brief.landmarks
        },
        inputs: {
            runtime_blockbench_model: relativeToRoot(modelFile),
            runtime_blockbench_sha256: sha256(modelFile),
            runtime_geometry: relativeToRoot(geoFile),
            runtime_geometry_sha256: sha256(geoFile),
            runtime_animation: relativeToRoot(animationFile),
            runtime_animation_sha256: sha256(animationFile),
            previous_audit: previous ?? null,
            construction_source_is_not_an_acceptance_reference: true
        },
        required_evidence: {
            neutral_views: neutralViews,
            silhouette_frames: silhouetteViews,
            animation_frames: animationViews,
            display: 'normal Blockbench display; fixed orthographic scale; grid and selections hidden where the installed Blockbench version supports it'
        },
        evidence: {
            frames: Object.fromEntries([...silhouetteViews, ...neutralViews, ...animationViews].map(name => [name, null]))
        },
        inspection_prompt: elementInspection(modelFile),
        defects: {
            uniform_surface_lattice: null,
            primary_reference_mismatch: null,
            critical_diagnostic_view_defect: null,
            critical_animation_defect: null
        },
        scores: {
            primary_silhouette_and_proportions: null,
            primary_anatomical_landmarks: null,
            diagnostic_view_volume: null,
            limb_hierarchy_and_joints: null,
            material_and_animation_readability: null
        },
        total: null,
        three_largest_visible_mismatches: ['', '', ''],
        next_modelling_change: '',
        review_notes: ''
    };
}

function scorecardMarkdown(review) {
    const score = briefs.scorecard;
    const landmarkList = review.baseline.required_landmarks.map(item => `- [ ] ${item}`).join('\n');
    const frameList = [...silhouetteViews, ...neutralViews, ...animationViews].map(name => `- [ ] \`${name}\`: ${review.evidence.frames[name] ?? 'not captured'}`).join('\n');
    return `# ${review.creature} — visual review\n\n` +
        `Status: **${review.status}**  \n` +
        `Base reference (sole likeness authority): \`${review.baseline.sole_likeness_authority}\`  \n` +
        `Primary-view intent: ${review.baseline.primary_view}\n\n` +
        `## 1. Fixed reference and required landmarks\n\n${landmarkList}\n\n` +
        `## 2–4. Construction gate before detail\n\n` +
        `The runtime model must be built from deliberate, varied anatomical masses. \`silhouette_primary\` uses Blockbench solid mode to prove the large form before texture and micro-detail are considered. The hi-fi source is not scoring evidence.\n\n` +
        `## 5. Actual Blockbench evidence\n\n${frameList}\n\n` +
        `## 6. Independent visual review (after looking at the images side by side)\n\n` +
        `| Criterion | Maximum | Score | Evidence / mismatch |\n| --- | ---: | ---: | --- |\n` +
        `| Primary silhouette and proportions | ${score.primary_silhouette_and_proportions} |  |  |\n` +
        `| Primary anatomical landmarks | ${score.primary_anatomical_landmarks} |  |  |\n` +
        `| Volume in diagnostic views | ${score.diagnostic_view_volume} |  |  |\n` +
        `| Limb hierarchy and joints | ${score.limb_hierarchy_and_joints} |  |  |\n` +
        `| Material and animation readability | ${score.material_and_animation_readability} |  |  |\n` +
        `| **Total** | **100** |  |  |\n\n` +
        `Hard gates: a uniform surface lattice gives **0** for anatomical landmarks; any critical failure in a diagnostic or animation frame rejects the model; below ${score.acceptance_threshold}/100 is not accepted; ${score.excellent_threshold}/100 requires no material primary-view mismatch and no critical diagnostic or animation defect.\n\n` +
        `## 7. Corrective delta\n\n` +
        `1. Largest mismatch: \n2. Second mismatch: \n3. Third mismatch: \n\n` +
        `One next modelling change (not a broad rewrite): \n\n` +
        `## 8. Versioned hand-off\n\n` +
        `The independent visual reviewer fills \`review.json\`; the primary agent then runs \`node tools/harvester_visual_audit.mjs --finalize <this-audit-directory>\`. Retain this directory beside the next audit for before/after comparison.\n`;
}

function discoverXauthority() {
    if (process.env.XAUTHORITY) return process.env.XAUTHORITY;
    const runDirectory = `/run/user/${process.getuid?.() ?? 1000}`;
    if (!fs.existsSync(runDirectory)) return undefined;
    return fs.readdirSync(runDirectory)
        .find(name => name.startsWith('.mutter-Xwaylandauth.'))
        ? path.join(runDirectory, fs.readdirSync(runDirectory).find(name => name.startsWith('.mutter-Xwaylandauth.')))
        : undefined;
}

function discoverDisplay(requestedDisplay) {
    if (requestedDisplay) return requestedDisplay;
    if (process.env.DISPLAY) return process.env.DISPLAY;
    return fs.existsSync('/tmp/.X11-unix/X0') ? ':0' : undefined;
}

async function waitForPage(port) {
    const deadline = Date.now() + 30000;
    let lastError = 'DevTools endpoint did not start';
    while (Date.now() < deadline) {
        try {
            const pages = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
            const page = pages.find(candidate => candidate.type === 'page' && candidate.webSocketDebuggerUrl);
            if (page) return page;
            lastError = 'DevTools endpoint has no inspectable page';
        } catch (error) {
            lastError = error.message;
        }
        await sleep(250);
    }
    throw new Error(`Could not connect to Blockbench DevTools on port ${port}: ${lastError}`);
}

class CdpClient {
    constructor(url) {
        this.url = url;
        this.nextId = 1;
        this.pending = new Map();
    }

    async connect() {
        await new Promise((resolve, reject) => {
            this.socket = new WebSocket(this.url);
            this.socket.addEventListener('open', resolve, { once: true });
            this.socket.addEventListener('error', event => reject(event.error ?? new Error('Could not open DevTools WebSocket')), { once: true });
            this.socket.addEventListener('message', event => {
                const message = JSON.parse(event.data);
                const request = this.pending.get(message.id);
                if (!request) return;
                this.pending.delete(message.id);
                message.error ? request.reject(new Error(message.error.message)) : request.resolve(message.result);
            });
        });
    }

    call(method, params = {}) {
        const id = this.nextId++;
        return new Promise((resolve, reject) => {
            this.pending.set(id, { resolve, reject });
            this.socket.send(JSON.stringify({ id, method, params }));
        });
    }

    close() {
        this.socket?.close();
    }
}

function editorScript(camera, animation, viewMode) {
    const position = JSON.stringify(camera.position);
    const target = JSON.stringify(camera.target);
    const clip = JSON.stringify(animation?.clip ?? null);
    const time = JSON.stringify(animation?.time ?? 0);
    const mode = JSON.stringify(viewMode ?? null);
    return `(() => {
        const preview = Preview.selected;
        if (!preview) throw new Error('No selected Blockbench preview');
        if ('isOrtho' in preview) preview.isOrtho = true;
        preview.camOrtho.zoom = ${JSON.stringify(camera.zoom)};
        preview.camOrtho.updateProjectionMatrix();
        preview.camera.position.set(...${position});
        preview.controls.target.set(...${target});
        preview.controls.update();
        preview.camera.lookAt(...${target});
        if (typeof unselectAll === 'function') unselectAll();
        if (${mode} !== null && typeof BarItems !== 'undefined' && BarItems.view_mode && BarItems.view_mode.value !== ${mode}) {
            BarItems.view_mode.value = ${mode};
            BarItems.view_mode.onChange();
        }
        if (typeof BarItems !== 'undefined' && BarItems.toggle_all_grids?.value) BarItems.toggle_all_grids.click();
        else if (typeof Settings !== 'undefined' && Settings.stored?.grids) Settings.stored.grids.value = false;
        if (typeof Canvas !== 'undefined' && typeof Canvas.buildGrid === 'function') Canvas.buildGrid();
        if (${clip} !== null) {
            const animation = Animator.animations.find(candidate => candidate.name === ${clip});
            if (!animation) throw new Error('Missing animation clip ' + ${clip});
            animation.select();
            // Timeline.setTime only moves the playhead. A separate preview
            // pass is required to apply that pose to the actual Blockbench
            // viewport.  Pausing first is equally important: otherwise the
            // regular playback loop can advance between capture requests.
            if (Timeline.playing && typeof Timeline.pause === 'function') Timeline.pause();
            Timeline.setTime(${time});
            Animator.preview();
        }
        // Canvas.updateAll rebuilds bone transforms from their rest pose. It
        // must not run after Animator.preview or the captured action frame is
        // silently reset to neutral. Neutral/silhouette captures still need
        // the full refresh.
        if (${clip} === null && typeof Canvas !== 'undefined' && typeof Canvas.updateAll === 'function') Canvas.updateAll();
        const animated_groups = ['scythe_arm_l', 'scythe_arm_l_fore', 'scythe_arm_l_blade'].map(name => {
            const group = Group.all.find(candidate => candidate.name === name);
            return {
                name,
                mesh_rotation: group?.mesh?.rotation?.toArray?.() ?? null,
                mesh_position: group?.mesh?.position?.toArray?.() ?? null
            };
        });
        return {
            is_orthographic: Boolean(preview.isOrtho),
            camera: preview.camera.position.toArray(),
            target: preview.controls.target.toArray(),
            selected_animation: typeof Animation !== 'undefined' && Animation.selected ? Animation.selected.name : null,
            timeline_time: typeof Timeline !== 'undefined' ? Timeline.time : null,
            timeline_playing: typeof Timeline !== 'undefined' ? Boolean(Timeline.playing) : null,
            animated_groups
        };
    })()`;
}

async function captureBlockbench({ modelFile, brief, outputDirectory, binary, requestedPort, requestedDisplay }) {
    const display = discoverDisplay(requestedDisplay);
    if (!display) throw new Error('Blockbench capture requires a normal X11 display; pass --display when the graphical desktop is not inherited');
    const blockbench = requireFile(binary ?? path.join(os.homedir(), 'Applications', 'blockbench'), 'Blockbench executable not found');
    const port = requestedPort ?? 9700 + Math.floor(Math.random() * 200);
    const environment = { ...process.env, DISPLAY: display, XAUTHORITY: discoverXauthority() ?? process.env.XAUTHORITY };
    const child = spawn('setsid', [blockbench, `--remote-debugging-port=${port}`, modelFile], {
        detached: false,
        env: environment,
        stdio: 'ignore'
    });
    child.unref();
    const page = await waitForPage(port);
    const client = new CdpClient(page.webSocketDebuggerUrl);
    await client.connect();
    const cameras = cameraDefinitions(brief);
    const frames = {};
    const gridState = await client.call('Runtime.evaluate', {
        expression: `(() => typeof BarItems !== 'undefined' && BarItems.toggle_all_grids ? BarItems.toggle_all_grids.value : (typeof Settings !== 'undefined' && Settings.stored?.grids ? Settings.stored.grids.value : null))()`,
        returnByValue: true
    });
    const originalGrid = gridState.result?.value;
    const viewModeState = await client.call('Runtime.evaluate', {
        expression: `(() => typeof Project !== 'undefined' ? Project.view_mode : null)()`,
        returnByValue: true
    });
    const originalViewMode = viewModeState.result?.value;
    const prepareEditor = async (name, camera, animation, viewMode) => {
        const deadline = Date.now() + 30000;
        let detail = 'Blockbench editor is not ready';
        while (Date.now() < deadline) {
            const evaluation = await client.call('Runtime.evaluate', {
                expression: editorScript(camera, animation, viewMode),
                awaitPromise: true,
                returnByValue: true
            });
            if (!evaluation.exceptionDetails) return evaluation.result?.value ?? null;
            detail = evaluation.exceptionDetails.exception?.description ?? evaluation.exceptionDetails.text;
            await sleep(250);
        }
        throw new Error(`Blockbench could not prepare ${name}: ${detail}`);
    };
    const capture = async (name, camera, animation, viewMode = 'textured') => {
        const state = await prepareEditor(name, camera, animation, viewMode);
        await sleep(350);
        const image = await client.call('Page.captureScreenshot', { format: 'png', fromSurface: true });
        const file = path.join(outputDirectory, 'frames', `${name}.png`);
        fs.writeFileSync(file, Buffer.from(image.data, 'base64'));
        frames[name] = path.relative(outputDirectory, file).split(path.sep).join('/');
        captureStates[name] = state;
    };
    const captureStates = {};
    fs.mkdirSync(path.join(outputDirectory, 'frames'), { recursive: true });
    try {
        await capture('silhouette_primary', cameras.primary_reference, null, 'solid');
        for (const name of neutralViews) await capture(name, cameras[name], null);
        const primary = cameras.primary_reference;
        await capture('idle_neutral', primary, null);
        const [anticipation, peak, recovery] = brief.action.frames;
        await capture('action_anticipation', primary, { clip: brief.action.clip, time: anticipation });
        await capture('action_peak', primary, { clip: brief.action.clip, time: peak });
        await capture('action_recovery', primary, { clip: brief.action.clip, time: recovery });
    } finally {
        if (typeof originalGrid === 'boolean') {
            await client.call('Runtime.evaluate', {
                expression: `(() => { if (typeof BarItems !== 'undefined' && BarItems.toggle_all_grids && BarItems.toggle_all_grids.value !== ${JSON.stringify(originalGrid)}) BarItems.toggle_all_grids.click(); else if (Settings.stored?.grids) Settings.stored.grids.value = ${JSON.stringify(originalGrid)}; if (typeof Canvas !== 'undefined' && typeof Canvas.buildGrid === 'function') Canvas.buildGrid(); if (typeof Canvas !== 'undefined' && typeof Canvas.updateAll === 'function') Canvas.updateAll(); })()`,
                returnByValue: true
            });
        }
        if (typeof originalViewMode === 'string') {
            await client.call('Runtime.evaluate', {
                expression: `(() => { if (typeof BarItems !== 'undefined' && BarItems.view_mode && BarItems.view_mode.value !== ${JSON.stringify(originalViewMode)}) { BarItems.view_mode.value = ${JSON.stringify(originalViewMode)}; BarItems.view_mode.onChange(); } })()`,
                returnByValue: true
            });
        }
        client.close();
        // The capture opens a dedicated normal Blockbench process.  Do not
        // leave it holding the single-instance lock and accidentally make the
        // next audit inspect the already-open, stale project.
        try {
            process.kill(-child.pid, 'SIGTERM');
        } catch {
            child.kill('SIGTERM');
        }
    }
    return {
        frames,
        frame_states: captureStates,
        blockbench: { pid: child.pid, port, executable: blockbench, normal_display: environment.DISPLAY }
    };
}

function requireBoolean(value, key, missing) {
    if (typeof value !== 'boolean') missing.push(key);
}

function finalise(auditDirectory) {
    const directory = requireFile(auditDirectory, 'Audit directory not found');
    const reviewFile = path.join(directory, 'review.json');
    const review = readJson(requireFile(reviewFile, 'review.json not found'));
    const missing = [];
    for (const [name, relative] of Object.entries(review.evidence?.frames ?? {})) {
        if (typeof relative !== 'string' || !fs.existsSync(path.join(directory, relative))) missing.push(`evidence.frames.${name}`);
    }
    for (const key of scoreKeys) {
        const maximum = briefs.scorecard[key];
        const score = review.scores?.[key];
        if (!Number.isInteger(score) || score < 0 || score > maximum) missing.push(`scores.${key}`);
    }
    requireBoolean(review.defects?.uniform_surface_lattice, 'defects.uniform_surface_lattice', missing);
    requireBoolean(review.defects?.primary_reference_mismatch, 'defects.primary_reference_mismatch', missing);
    requireBoolean(review.defects?.critical_diagnostic_view_defect, 'defects.critical_diagnostic_view_defect', missing);
    requireBoolean(review.defects?.critical_animation_defect, 'defects.critical_animation_defect', missing);
    if (!Array.isArray(review.three_largest_visible_mismatches) || review.three_largest_visible_mismatches.some(value => !String(value).trim())) missing.push('three_largest_visible_mismatches');
    if (!String(review.next_modelling_change ?? '').trim()) missing.push('next_modelling_change');
    if (missing.length) {
        review.status = 'INCOMPLETE_REVIEW';
        review.finalization_errors = missing;
        writeJson(reviewFile, review);
        throw new Error(`Review is incomplete: ${missing.join(', ')}`);
    }
    const total = Object.values(review.scores).reduce((sum, value) => sum + value, 0);
    const critical = review.defects.uniform_surface_lattice || review.defects.primary_reference_mismatch || review.defects.critical_diagnostic_view_defect || review.defects.critical_animation_defect;
    review.total = total;
    review.reviewed_at = new Date().toISOString();
    review.finalization_errors = [];
    review.status = critical ? 'REJECTED_HARD_GATE' : total < briefs.scorecard.acceptance_threshold ? 'REJECTED_SCORE' : total === briefs.scorecard.excellent_threshold ? 'ACCEPTED_10_OF_10' : 'ACCEPTED';
    writeJson(reviewFile, review);
    console.log(`${review.creature}: ${review.status}, ${total}/100`);
    return review.status;
}

async function main() {
    const options = parseArgs(process.argv.slice(2));
    if (options.help) return console.log(usage());
    if (options.finalize) return finalise(options.finalize);
    if (!supportedCreatures.includes(options.creature)) throw new Error(`--creature must be one of: ${supportedCreatures.join(', ')}`);
    const brief = briefs.creatures[options.creature];
    const referencesRoot = requireFile(options.referencesRoot, '--references-root directory not found');
    const referenceFile = requireFile(path.join(referencesRoot, brief.base_reference), 'Base reference image not found');
    const modelFile = requireFile(path.join(harvesterRoot, `${options.creature}.bbmodel`), 'Runtime Blockbench model not found');
    const geoFile = requireFile(path.join(assetRoot, 'geo', 'harvester', `${options.creature}.geo.json`), 'Runtime geometry not found');
    const animationFile = requireFile(path.join(assetRoot, 'animations', 'harvester', `${options.creature}.animation.json`), 'Runtime animation not found');
    const auditDirectory = options.auditDir
        ? path.resolve(options.auditDir)
        : path.join(root, 'build', 'harvester-visual-audits', `${dateStamp()}-${options.creature}-${slug(options.iteration)}`);
    if (fs.existsSync(auditDirectory)) throw new Error(`Audit directory already exists: ${auditDirectory}`);
    if (options.previous) requireFile(options.previous, 'Previous audit directory not found');
    fs.mkdirSync(auditDirectory, { recursive: true });
    const review = buildReview({ creature: options.creature, brief, referenceFile, modelFile, geoFile, animationFile, previous: options.previous ? path.resolve(options.previous) : null });
    let capture = null;
    if (options.capture) {
        capture = await captureBlockbench({ modelFile, brief, outputDirectory: auditDirectory, binary: options.blockbenchBin, requestedPort: options.port, requestedDisplay: options.display });
        review.evidence.frames = capture.frames;
    }
    const manifest = {
        schema: 'pale_mirror_visuals.harvester_visual_audit_manifest.v1',
        created_at: new Date().toISOString(),
        audit_directory: relativeToRoot(auditDirectory),
        capture: capture ?? { performed: false, reason: 'Run again with --capture to obtain actual normal-display Blockbench evidence.' },
        review_file: 'review.json',
        scorecard_file: 'scorecard.md'
    };
    writeJson(path.join(auditDirectory, 'review.json'), review);
    fs.writeFileSync(path.join(auditDirectory, 'scorecard.md'), scorecardMarkdown(review));
    writeJson(path.join(auditDirectory, 'manifest.json'), manifest);
    console.log(`Created ${auditDirectory}`);
    console.log(options.capture ? 'Captured actual Blockbench frames. Review them before editing review.json.' : 'Evidence not captured yet; this audit cannot be accepted.');
}

main().catch(error => {
    console.error(`harvester visual audit: ${error.message}`);
    process.exitCode = 1;
});
