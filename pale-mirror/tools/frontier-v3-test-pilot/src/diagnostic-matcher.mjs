import { isDeepStrictEqual } from 'node:util';

// Match the native pilot: objects are partial expectations; arrays are exact
// JSON values, including length, order and every member's fields.
export function matches(actual, expected) {
  if (actual === null || typeof actual !== 'object' || Array.isArray(actual)) return false;
  return Object.entries(expected).every(([key, value]) => {
    if (!Object.hasOwn(actual, key)) return false;
    if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
      return matches(actual[key], value);
    }
    return isDeepStrictEqual(actual[key], value);
  });
}
