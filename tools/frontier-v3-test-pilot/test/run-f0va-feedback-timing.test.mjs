import assert from 'node:assert/strict';
import test from 'node:test';
import { parse } from '../src/run-f0va-feedback-timing.mjs';

test('feedback timing CLI accepts only explicit bounded build paths', () => {
  assert.deepEqual(parse(['--prepared=build/prepared.json', '--output=build/feedback.json']), {
    prepared: 'build/prepared.json', output: 'build/feedback.json'
  });
  assert.throws(() => parse(['--prepared=../prepared.json', '--output=build/feedback.json']), /safe relative/);
  assert.throws(() => parse(['--prepared=build/prepared.json']), /usage/);
});
