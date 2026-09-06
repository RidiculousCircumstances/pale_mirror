/**
 * Cancellable deadline for one awaited protocol transition.
 *
 * The deadline remains a hard failure if it wins, but an earlier state transition must close it
 * so a completed runner does not retain a hidden event-loop handle until its former timeout.
 */
export function deadlineWatchdog(milliseconds, message) {
  if (!Number.isInteger(milliseconds) || milliseconds < 1) throw new Error('deadline watchdog requires a positive whole-millisecond timeout');
  let closed = false;
  let timer;
  const wait = new Promise((_, reject) => {
    timer = setTimeout(() => {
      if (!closed) reject(new Error(message));
    }, milliseconds);
  });
  return Object.freeze({
    wait,
    close() {
      if (closed) return;
      closed = true;
      clearTimeout(timer);
    },
    closed: () => closed
  });
}

/**
 * Awaits one bounded protocol operation without retaining the losing timer.
 *
 * A plain Promise.race leaves its timeout alive after the operation resolves.
 * That is observably wrong for a verification runner: completed work then keeps
 * Node alive and can look like a stalled Minecraft process.  This helper owns
 * the watchdog for the entire race and always closes it before returning.
 */
export async function awaitWithin(operation, milliseconds, message) {
  const watchdog = deadlineWatchdog(milliseconds, message);
  try {
    return await Promise.race([Promise.resolve(operation), watchdog.wait]);
  } finally {
    watchdog.close();
  }
}

/**
 * Owns one child-process exit listener. Callers racing a lifecycle acknowledgement against exact
 * JVM termination need the exit status, but must release the losing listener when the
 * acknowledgement wins.
 */
export function childExitWatch(child) {
  if (child == null || typeof child.once !== 'function' || typeof child.removeListener !== 'function') {
    throw new Error('child-exit watch requires an event-emitting child process');
  }
  const status = (code) => code ?? child.exitCode ?? (child.signalCode === 'SIGINT' ? 130 : 1);
  let closed = false;
  let resolveWait;
  let onExit;
  const wait = new Promise((resolve) => { resolveWait = resolve; });
  const close = () => {
    if (closed) return;
    closed = true;
    child.removeListener('exit', onExit);
  };
  onExit = (code) => {
    close();
    resolveWait(status(code));
  };
  if (child.exitCode !== null || child.signalCode !== null) {
    closed = true;
    resolveWait(status(undefined));
  } else {
    child.once('exit', onExit);
  }
  return Object.freeze({ wait, close, closed: () => closed });
}

/**
 * Waits for one exact child under a cancellable deadline. Both the losing watchdog and the
 * losing event listener are closed, so completed native scenarios cannot remain alive due to
 * their own supervision machinery.
 */
export async function awaitChildExit(child, milliseconds, message) {
  const watch = childExitWatch(child);
  try {
    return await awaitWithin(watch.wait, milliseconds, message);
  } finally {
    watch.close();
  }
}

/**
 * Couples one bounded wait to a child exit without retaining an `exit` listener after the wait
 * completes. Native matrices perform many serial barriers against one persistent client; leaving
 * each listener attached both emits Node's leak warning and obscures a real runner regression.
 */
export function childExitCancellation(child) {
  if (child == null || typeof child.once !== 'function' || typeof child.removeListener !== 'function') {
    throw new Error('child-exit cancellation requires an event-emitting child process');
  }
  const controller = new AbortController();
  const status = () => child.exitCode ?? (child.signalCode === 'SIGINT' ? 130 : 1);
  if (child.exitCode !== null || child.signalCode !== null) {
    controller.abort(status());
    return Object.freeze({ signal: controller.signal, close() {}, closed: () => true });
  }
  let closed = false;
  const onExit = (code) => controller.abort(code ?? (child.signalCode === 'SIGINT' ? 130 : 1));
  child.once('exit', onExit);
  return Object.freeze({
    signal: controller.signal,
    close() {
      if (closed) return;
      closed = true;
      child.removeListener('exit', onExit);
    },
    closed: () => closed
  });
}

/**
 * Turns one already-owned abort signal into a bounded failure promise without retaining an
 * `abort` listener after the surrounding protocol wait completes.  This is deliberately kept
 * separate from `childExitCancellation`: a caller may have several different awaited protocol
 * sources, but it must share one exact child-exit authority for the current barrier.
 */
export function abortSignalFailure(signal, message) {
  if (signal == null || typeof signal.addEventListener !== 'function' || typeof signal.removeEventListener !== 'function') {
    throw new Error('abort-signal failure requires an AbortSignal');
  }
  if (typeof message !== 'string' || message.length === 0) throw new Error('abort-signal failure requires a message');
  let closed = false;
  let rejectWait;
  const close = () => {
    if (closed) return;
    closed = true;
    signal.removeEventListener('abort', onAbort);
  };
  const onAbort = () => {
    if (closed) return;
    close();
    rejectWait(new Error(`${message} (${String(signal.reason ?? 'unknown')})`));
  };
  const wait = new Promise((_, reject) => { rejectWait = reject; });
  if (signal.aborted) onAbort();
  else signal.addEventListener('abort', onAbort, { once: true });
  return Object.freeze({ wait, close, closed: () => closed });
}
