/** Recognizes NeoForge's terminal early-display event before semantic startup. */
export function createEarlyDisplayFailureDetector() {
  return Object.freeze({
    observe(line) {
      return line.includes('[EARLYDISPLAY/]: ERROR DISPLAY')
        ? 'native pilot early display initialization failed'
        : null;
    }
  });
}
