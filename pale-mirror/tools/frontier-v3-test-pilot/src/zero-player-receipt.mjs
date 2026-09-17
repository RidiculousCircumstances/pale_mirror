const MARKER = 'PMV3_DIAG ';

/** Parses the one durable operator receipt without assuming its terminal outcome. */
export function zeroPlayerRequestReceipt(response) {
  const value = diagnostic(response, 'fast-forward terminal query');
  const receipt = Array.isArray(value?.fastForwardRequests) ? value.fastForwardRequests.at(-1) : null;
  if (value?.kind !== 'status' || value.status !== 'ok' || !Number.isInteger(receipt?.requestId)
      || typeof receipt.kind !== 'string' || !Number.isInteger(receipt.requestedTicks)
      || !Number.isInteger(receipt.targetInstant) || !Number.isInteger(receipt.admittedCheckpointInstant)
      || !['QUEUED', 'COMPLETED', 'HELD', 'REJECTED', 'FAILED'].includes(receipt.status)
      || (receipt.reachedCheckpointInstant !== null && !Number.isInteger(receipt.reachedCheckpointInstant))
      || (receipt.reason !== null && typeof receipt.reason !== 'string')) {
    throw new Error('zero-player prelude terminal query lacks one recoverable request receipt');
  }
  return Object.freeze(receipt);
}

export function zeroPlayerRejectedReceipt(response) {
  const receipt = zeroPlayerRequestReceipt(response);
  if (receipt.status !== 'REJECTED' || !Number.isInteger(receipt.reachedCheckpointInstant)
      || typeof receipt.reason !== 'string' || !receipt.reason) {
    throw new Error('zero-player prelude terminal query lacks one recoverable rejected request receipt');
  }
  return receipt;
}

export function zeroPlayerPerformance(response) {
  const value = diagnostic(response, 'performance query');
  const slice = value?.fastForwardSlice;
  if (value?.kind !== 'performance' || value.status !== 'ok' || !Number.isInteger(slice?.samples) || slice.samples < 1
      || !['advancedTicks', 'totalNanos', 'maxNanos', 'safetyNanos', 'maxSafetyNanos', 'advanceNanos', 'maxAdvanceNanos']
        .every(field => Number.isInteger(slice[field]) && slice[field] >= 0)) {
    throw new Error('zero-player prelude performance query lacks bounded fast-forward slice attribution');
  }
  return Object.freeze(value);
}

function diagnostic(response, name) {
  const offset = typeof response === 'string' ? response.indexOf(MARKER) : -1;
  if (offset < 0) throw new Error(`zero-player prelude ${name} did not return a PMV3 diagnostic`);
  try { return JSON.parse(response.slice(offset + MARKER.length)); }
  catch { throw new Error(`zero-player prelude ${name} returned malformed JSON`); }
}
