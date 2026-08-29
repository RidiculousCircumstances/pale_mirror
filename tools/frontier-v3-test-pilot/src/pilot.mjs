import mineflayer from 'mineflayer';
import { pathfinder, Movements, goals } from 'mineflayer-pathfinder';

const { GoalNear } = goals;

export async function connectPilot(config, onDiagnostic = () => {}) {
  const bot = mineflayer.createBot({
    host: config.host,
    port: config.port,
    username: config.username,
    version: config.version ?? '1.21.1',
    auth: config.auth ?? 'offline',
    hideErrors: true
  });
  bot.loadPlugin(pathfinder);
  bot.on('messagestr', (message) => {
    const marker = message.indexOf('PMV3_DIAG ');
    if (marker >= 0) onDiagnostic(message.slice(marker));
  });
  await once(bot, 'spawn', 30_000);
  bot.pathfinder.setMovements(new Movements(bot));
  return bot;
}

export async function perform(bot, action) {
  switch (action.type) {
    case 'command':
      bot.chat(action.command);
      return { type: action.type, command: action.command };
    case 'observe':
      bot.chat(`/spectate ${bot.username} ${action.viewer}`);
      return { type: action.type, viewer: action.viewer };
    case 'wait':
      await delay(action.ms);
      return { type: action.type, ms: action.ms };
    case 'look':
      await bot.lookAt(center(action.at ?? action.position), true);
      return { type: action.type, position: action.at ?? action.position };
    case 'walk':
      await withTimeout(bot.pathfinder.goto(new GoalNear(action.position.x, action.position.y, action.position.z, action.radius ?? 1)), action.timeoutMs ?? 30_000, 'walk');
      return { type: action.type, position: action.position };
    case 'break': {
      const position = action.position;
      await bot.lookAt(center(position), true);
      const block = bot.blockAt(position);
      if (!block || block.name === 'air') throw new Error(`break target is missing at ${position.x},${position.y},${position.z}`);
      await withTimeout(bot.dig(block, true), action.timeoutMs ?? 15_000, 'break');
      return { type: action.type, position, block: block.name };
    }
    case 'open_container': {
      const position = action.position;
      await bot.lookAt(center(position), true);
      const block = bot.blockAt(position);
      if (!block) throw new Error(`container target is missing at ${position.x},${position.y},${position.z}`);
      const window = await withTimeout(bot.openContainer(block), action.timeoutMs ?? 15_000, 'open_container');
      const slots = window.containerItems().map((item) => ({ name: item.name, count: item.count }));
      window.close();
      return { type: action.type, position, block: block.name, slots };
    }
    case 'withdraw':
    case 'deposit': {
      const position = action.position;
      const item = bot.registry.itemsByName[action.item];
      if (!item || !Number.isInteger(action.count) || action.count < 1 || action.count > 2304) {
        throw new Error(`${action.type} requires a known item name and count 1..2304`);
      }
      await bot.lookAt(center(position), true);
      const block = bot.blockAt(position);
      if (!block) throw new Error(`container target is missing at ${position.x},${position.y},${position.z}`);
      const window = await withTimeout(bot.openContainer(block), action.timeoutMs ?? 15_000, action.type);
      try {
        if (action.type === 'withdraw') await withTimeout(window.withdraw(item.id, null, action.count), action.timeoutMs ?? 15_000, action.type);
        else await withTimeout(window.deposit(item.id, null, action.count), action.timeoutMs ?? 15_000, action.type);
      } finally { window.close(); }
      return { type: action.type, position, item: action.item, count: action.count };
    }
    case 'die':
      await once(bot, 'death', action.timeoutMs ?? 30_000);
      return { type: action.type, observed: 'death' };
    default:
      throw new Error(`unsupported pilot action: ${action.type}`);
  }
}

export async function inspect(bot, view, id, timeoutMs = 10_000) {
  const result = await new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      bot.removeListener('messagestr', listener);
      reject(new Error(`diagnostic ${view} ${id} timed out after ${timeoutMs}ms`));
    }, timeoutMs);
    const listener = (message) => {
      const marker = message.indexOf('PMV3_DIAG ');
      if (marker < 0) return;
      const line = message.slice(marker);
      try {
        const value = JSON.parse(line.slice('PMV3_DIAG '.length));
        if (value.kind !== view || value.id !== id) return;
        clearTimeout(timer);
        bot.removeListener('messagestr', listener);
        resolve({ line, value });
      } catch { /* Ignore other chat text; the command's prefixed JSON is authoritative. */ }
    };
    bot.on('messagestr', listener);
    bot.chat(`/pale_mirror v3 inspect ${view}${id ? ` ${id}` : ''}`);
  });
  return result;
}

function center(position) { return { x: position.x + 0.5, y: position.y + 0.5, z: position.z + 0.5 }; }
function delay(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }
function once(emitter, event, timeoutMs) {
  return new Promise((resolve, reject) => {
    let timer;
    const cleanup = () => {
      clearTimeout(timer);
      emitter.removeListener(event, spawned);
      emitter.removeListener('error', failed);
      emitter.removeListener('end', ended);
    };
    const spawned = (...args) => { cleanup(); resolve(...args); };
    const failed = (error) => { cleanup(); reject(error); };
    const ended = () => { cleanup(); reject(new Error(`pilot ended before ${event}`)); };
    emitter.once(event, spawned);
    emitter.once('error', failed);
    emitter.once('end', ended);
    timer = setTimeout(() => failed(new Error(`waiting for ${event} timed out after ${timeoutMs}ms`)), timeoutMs);
  });
}
function withTimeout(promise, timeoutMs, label) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`${label} timed out after ${timeoutMs}ms`)), timeoutMs);
    promise.then((value) => { clearTimeout(timer); resolve(value); }, (error) => { clearTimeout(timer); reject(error); });
  });
}
