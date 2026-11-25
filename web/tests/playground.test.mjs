import assert from 'node:assert/strict';
import test from 'node:test';

import { createType2Packet, encodeSignalByte } from '../playground.mjs';
import { processPics } from '../pics.mjs';

test('encodes a signal state and remaining value into one PICS byte', () => {
  assert.equal(encodeSignalByte('Green', 8), 0x73);
  assert.equal(encodeSignalByte('Red', 8), 0x71);
  assert.equal(encodeSignalByte('BlinkGreen', 1), 0x02);
});

test('creates a Type 2 packet that round-trips through the production parser', () => {
  const packet = createType2Packet([
    { state: 'BlinkGreen', remaining: 3 },
    { state: 'Red', remaining: 5 },
  ]);
  const parsed = processPics(packet);
  assert.equal(parsed.message_type, 2);
  assert.equal(parsed.intersection_id, '00000001');
  assert.deepEqual(parsed.pedestrian_signals.slice(0, 2), [
    { state: undefined, remaining_time: 3, signal_state: 'BlinkGreen' },
    { state: undefined, remaining_time: 5, signal_state: 'Red' },
  ].map(({ remaining_time, signal_state }) => ({ remaining_time, signal_state })));
});

test('rejects unsupported states and out-of-range remaining values', () => {
  assert.throws(() => encodeSignalByte('Blue', 4), /未対応/);
  assert.throws(() => encodeSignalByte('Green', 0), /1〜8/);
  assert.throws(() => createType2Packet([]), /1〜6/);
});
