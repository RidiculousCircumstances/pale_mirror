import assert from 'node:assert/strict';
import test from 'node:test';
import { resolveVisibleDisplayAuthority } from '../src/visible-display.mjs';

test('visible native pilot derives one exact current Xwayland authority', () => {
  assert.equal(resolveVisibleDisplayAuthority({ display: ':0', runtimeDirectory: '/run/user/1000', entries: [
    '.mutter-Xwaylandauth.current', 'unrelated'
  ] }), '/run/user/1000/.mutter-Xwaylandauth.current');
  assert.equal(resolveVisibleDisplayAuthority({ display: ':0', xauthority: '/tmp/exact-xauth', runtimeDirectory: '/', entries: [] }), '/tmp/exact-xauth');
});

test('visible native pilot fails before launch when display authority is unsafe or ambiguous', () => {
  assert.throws(() => resolveVisibleDisplayAuthority({ display: ':1', runtimeDirectory: '/run/user/1000', entries: [] }), /DISPLAY/);
  assert.throws(() => resolveVisibleDisplayAuthority({ display: ':0', xauthority: 'relative', runtimeDirectory: '/', entries: [] }), /not absolute/);
  assert.throws(() => resolveVisibleDisplayAuthority({ display: ':0', runtimeDirectory: '/run/user/1000', entries: [] }), /ambiguous or absent/);
  assert.throws(() => resolveVisibleDisplayAuthority({ display: ':0', runtimeDirectory: '/run/user/1000', entries: [
    '.mutter-Xwaylandauth.one', '.mutter-Xwaylandauth.two'
  ] }), /ambiguous or absent/);
});
