/** Recognizes the exact NeoForge early-display failure before semantic startup. */
export function createEarlyDisplayFailureDetector() {
  let earlyDisplayReported = false;
  return Object.freeze({
    observe(line) {
      if (line.includes('[EARLYDISPLAY/]: ERROR DISPLAY')) earlyDisplayReported = true;
      return earlyDisplayReported && line.includes('glfwInit failed')
        ? 'native pilot early display initialization failed (glfwInit failed)'
        : null;
    }
  });
}
