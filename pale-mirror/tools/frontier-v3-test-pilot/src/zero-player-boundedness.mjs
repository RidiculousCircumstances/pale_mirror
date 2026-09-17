/**
 * Reads only the exact server-output interval already owned by a zero-player
 * canonical request.  It neither changes the request nor treats a later
 * ordinary client join as proof that the interval was responsive.
 */
export function zeroPlayerBoundedness(output) {
  if (typeof output !== 'string') throw new Error('zero-player boundedness output must be text');
  const stalls = [];
  const pattern = /Can't keep up!.*?(\d+)ms or (\d+) ticks behind/g;
  for (const match of output.matchAll(pattern)) {
    stalls.push(Object.freeze({ behindMillis: Number(match[1]), behindTicks: Number(match[2]) }));
  }
  const maxBehindMillis = stalls.reduce((maximum, value) => Math.max(maximum, value.behindMillis), 0);
  const maxBehindTicks = stalls.reduce((maximum, value) => Math.max(maximum, value.behindTicks), 0);
  return Object.freeze({ status: stalls.length === 0 ? 'NO_STALL' : 'STALL', noServerTickStall: stalls.length === 0,
    stallCount: stalls.length, maxBehindMillis, maxBehindTicks });
}
