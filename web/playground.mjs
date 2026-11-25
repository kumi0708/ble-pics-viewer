const STATE_CODES = Object.freeze({
  Red: 1,
  BlinkGreen: 2,
  Green: 3,
});

export function encodeSignalByte(state, remaining) {
  const stateCode = STATE_CODES[state];
  if (stateCode == null) throw new RangeError(`未対応の信号状態です: ${state}`);
  if (!Number.isInteger(remaining) || remaining < 1 || remaining > 8) {
    throw new RangeError('残り値は1〜8の整数で指定してください。');
  }
  return ((remaining - 1) << 4) | stateCode;
}

export function createType2Packet(signals, intersectionId = [0x00, 0x00, 0x00, 0x01]) {
  if (!Array.isArray(signals) || signals.length < 1 || signals.length > 6) {
    throw new RangeError('信号は1〜6件で指定してください。');
  }
  if (!Array.isArray(intersectionId) || intersectionId.length !== 4) {
    throw new RangeError('交差点IDは4バイトで指定してください。');
  }
  const packet = new Uint8Array(16);
  packet[2] = 2;
  packet.set(intersectionId, 6);
  packet.fill(0x84, 10);
  signals.forEach((signal, index) => {
    packet[10 + index] = encodeSignalByte(signal.state, signal.remaining);
  });
  return packet;
}
