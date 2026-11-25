import { SIGNAL_LABELS, hexDump, processPics } from './pics.mjs';
import { createType2Packet } from './playground.mjs';

const $ = id => document.getElementById(id);
const STATE_NAMES = { Red: 'RED', Green: 'GREEN', BlinkGreen: 'BLINK GREEN' };
const state = {
  phase: 0,
  remaining: 8,
  signals: [{ state: 'Green', remaining: 8 }, { state: 'Red', remaining: 8 }],
  auto: false,
  mode: 'AUTO CYCLE',
};
let autoTimer = null;
let xrSupported = false;
let xrSession = null;
let xrGl = null;
let xrSpace = null;

function phaseSignals(phase) {
  const remaining = 8 - (phase % 8);
  return {
    remaining,
    signals: [
      { state: phase < 8 ? 'Green' : phase < 12 ? 'BlinkGreen' : 'Red', remaining },
      { state: phase < 12 ? 'Red' : phase < 20 ? 'Green' : 'BlinkGreen', remaining },
    ],
  };
}

function setPhase(value) {
  state.phase = Number(value);
  const next = phaseSignals(state.phase);
  state.remaining = next.remaining;
  state.signals = next.signals;
  state.mode = state.auto ? 'AUTO CYCLE' : 'TIMELINE';
  render();
}

function setManual(signals, mode = 'MANUAL') {
  stopAuto();
  state.signals = signals.map(signal => ({ ...signal, remaining: state.remaining }));
  state.mode = mode;
  render();
}

function pulsePacket() {
  const map = $('intersection');
  map.classList.remove('is-pulsing');
  void map.offsetWidth;
  map.classList.add('is-pulsing');
}

function renderSignal(parsedSignal, index) {
  const signal = state.signals[index];
  const mapSignal = document.querySelector(`[data-map-signal="${index}"]`);
  mapSignal.dataset.state = signal.state;
  mapSignal.querySelector('strong').textContent = SIGNAL_LABELS[signal.state];
  const phoneSignal = document.querySelector(`[data-phone-signal="${index}"]`);
  phoneSignal.dataset.state = signal.state;
  phoneSignal.querySelector('strong').textContent = SIGNAL_LABELS[signal.state];
  $(`readout-s${index + 1}`).textContent = `${STATE_NAMES[signal.state]} · ${parsedSignal.remaining_time}`;
  document.querySelectorAll(`.walker-${index === 0 ? 'x' : 'y'}`).forEach(walker => {
    walker.classList.toggle('can-walk', signal.state === 'Green' || signal.state === 'BlinkGreen');
  });
}

function byteLabel(index) {
  if (index === 2) return ['TYPE', 'type'];
  if (index >= 6 && index <= 9) return ['ID', 'id'];
  if (index === 10) return ['S1', 's1'];
  if (index === 11) return ['S2', 's2'];
  if (index >= 12) return ['PAD', 'pad'];
  return ['HDR', 'header'];
}

function renderBytes(packet) {
  const bytes = Array.from(packet, (value, index) => {
    const button = document.createElement('button');
    const [label, focus] = byteLabel(index);
    button.type = 'button';
    button.className = 'byte';
    button.dataset.focus = focus;
    button.setAttribute('aria-label', `${label} バイト ${value.toString(16).padStart(2, '0')}`);
    const hex = document.createElement('strong');
    hex.textContent = value.toString(16).padStart(2, '0').toUpperCase();
    const caption = document.createElement('small');
    caption.textContent = label;
    button.append(hex, caption);
    button.addEventListener('click', inspectByte);
    return button;
  });
  $('packet-bytes').replaceChildren(...bytes);
  $('packet-hex').textContent = hexDump(packet);
}

function inspectByte(event) {
  const focus = event.currentTarget.dataset.focus;
  const messages = {
    header: '共通ヘッダー。PICSメッセージの基本情報です。',
    type: '0x02は歩行者信号状態を表すType 2です。',
    id: '4バイトの交差点ID。このデモでは00000001です。',
    s1: '信号1の1バイト。上位4bitが残り値、下位4bitが信号状態です。',
    s2: '信号2の1バイト。操作すると値がその場で変化します。',
    pad: '未使用の信号枠。0x84は対象なし／残り値不明として扱われます。',
  };
  $('packet-explanation').textContent = messages[focus];
  document.querySelectorAll('.map-signal').forEach(signal => signal.classList.remove('is-inspecting'));
  if (focus === 's1' || focus === 's2') {
    const index = focus === 's1' ? 0 : 1;
    const signal = document.querySelector(`[data-map-signal="${index}"]`);
    signal.classList.add('is-inspecting');
    setTimeout(() => signal.classList.remove('is-inspecting'), 900);
  }
}

function render() {
  state.signals.forEach(signal => { signal.remaining = state.remaining; });
  const packet = createType2Packet(state.signals);
  const parsed = processPics(packet);
  parsed.pedestrian_signals.slice(0, 2).forEach(renderSignal);
  renderBytes(packet);
  $('phase-number').textContent = String(state.phase).padStart(2, '0');
  $('phase-control').value = state.phase;
  $('clock').textContent = `PHASE ${String(state.phase).padStart(2, '0')}`;
  $('remaining-control').value = state.remaining;
  $('remaining-output').textContent = state.remaining;
  $('scene-mode').textContent = state.mode;
  $('readout-mode').textContent = state.mode.replace(' CYCLE', '');
  $('phone-received').textContent = `${new Date().toLocaleTimeString('ja-JP', { hour12: false })} · BLE`;
  renderJudgement();
  pulsePacket();
}

function renderJudgement() {
  const green = state.signals.findIndex(signal => signal.state === 'Green');
  const blinking = state.signals.findIndex(signal => signal.state === 'BlinkGreen');
  let condition = 'nominal';
  let jp = '横断許可';
  let en = `ROUTE S${green + 1} / WALK`;
  if (green < 0 && blinking >= 0) {
    condition = 'warning';
    jp = '切替注意';
    en = `ROUTE S${blinking + 1} / BLINKING`;
  }
  if (green < 0 && blinking < 0) {
    condition = 'halt';
    jp = '全方向停止';
    en = 'ALL ROUTES / STOP';
  }
  document.body.dataset.condition = condition;
  $('judgement-jp').textContent = jp;
  $('judgement-en').textContent = en;
}

function cycleSignal(index) {
  const states = ['Red', 'Green', 'BlinkGreen'];
  const current = states.indexOf(state.signals[index].state);
  const next = state.signals.map(signal => ({ ...signal }));
  next[index].state = states[(current + 1) % states.length];
  setManual(next);
}

function startAuto() {
  if (state.auto) return;
  state.auto = true;
  $('auto-play').setAttribute('aria-pressed', 'true');
  $('auto-play').querySelector('strong').textContent = '一時停止';
  state.mode = 'AUTO CYCLE';
  render();
  autoTimer = setInterval(() => setPhase((state.phase + 1) % 24), 780);
}

function stopAuto() {
  state.auto = false;
  clearInterval(autoTimer);
  autoTimer = null;
  $('auto-play').setAttribute('aria-pressed', 'false');
  $('auto-play').querySelector('strong').textContent = '自動再生';
}

$('auto-play').addEventListener('click', () => state.auto ? stopAuto() : startAuto());
$('phase-control').addEventListener('input', event => { stopAuto(); setPhase(event.target.value); });
$('remaining-control').addEventListener('input', event => {
  stopAuto();
  state.remaining = Number(event.target.value);
  state.mode = 'MANUAL';
  render();
});
document.querySelectorAll('[data-map-signal]').forEach(button => {
  button.addEventListener('click', () => cycleSignal(Number(button.dataset.mapSignal)));
});
document.querySelectorAll('[data-scene]').forEach(button => {
  button.addEventListener('click', () => {
    const scene = button.dataset.scene;
    if (scene === 'normal') { stopAuto(); setPhase(0); }
    if (scene === 'switch') { stopAuto(); setPhase(9); }
    if (scene === 'all-red') setManual([{ state: 'Red' }, { state: 'Red' }], 'ALL RED');
    if (scene === 'random') {
      const states = ['Red', 'Green', 'BlinkGreen'];
      state.remaining = Math.floor(Math.random() * 8) + 1;
      setManual([0, 1].map(() => ({ state: states[Math.floor(Math.random() * states.length)] })), 'RANDOM');
    }
  });
});
document.addEventListener('visibilitychange', () => { if (document.hidden) stopAuto(); });

function showXRNotice(message) {
  const notice = $('xr-notice');
  notice.textContent = message;
  notice.hidden = false;
  clearTimeout(showXRNotice.timer);
  showXRNotice.timer = setTimeout(() => { notice.hidden = true; }, 5000);
}

async function detectXR() {
  if (!navigator.xr) {
    $('xr-status').textContent = 'PREVIEW MODE';
    return;
  }
  try {
    xrSupported = await navigator.xr.isSessionSupported('immersive-ar');
    $('xr-status').textContent = xrSupported ? 'AR READY' : 'PREVIEW MODE';
  } catch {
    $('xr-status').textContent = 'PREVIEW MODE';
  }
}

function onXRFrame(time, frame) {
  if (!xrSession) return;
  xrSession.requestAnimationFrame(onXRFrame);
  const pose = frame.getViewerPose(xrSpace);
  if (!pose) return;
  xrGl.bindFramebuffer(xrGl.FRAMEBUFFER, xrSession.renderState.baseLayer.framebuffer);
  xrGl.clearColor(0, 0, 0, 0);
  xrGl.clear(xrGl.COLOR_BUFFER_BIT | xrGl.DEPTH_BUFFER_BIT);
  pose.views.forEach(view => {
    const viewport = xrSession.renderState.baseLayer.getViewport(view);
    xrGl.viewport(viewport.x, viewport.y, viewport.width, viewport.height);
  });
}

function leaveXR() {
  xrSession = null;
  xrGl = null;
  xrSpace = null;
  document.body.classList.remove('xr-active');
  $('xr-toggle').setAttribute('aria-pressed', 'false');
  $('xr-status').textContent = xrSupported ? 'AR READY' : 'PREVIEW MODE';
}

async function enterImmersiveAR() {
  try {
    const session = await navigator.xr.requestSession('immersive-ar', {
      requiredFeatures: ['dom-overlay'],
      optionalFeatures: ['local-floor'],
      domOverlay: { root: document.body },
    });
    const canvas = $('xr-layer');
    const gl = canvas.getContext('webgl', { alpha: true, xrCompatible: true });
    await gl.makeXRCompatible();
    session.updateRenderState({ baseLayer: new XRWebGLLayer(session, gl) });
    xrSession = session;
    xrGl = gl;
    xrSpace = await session.requestReferenceSpace('local');
    session.addEventListener('end', leaveXR, { once: true });
    document.body.classList.add('xr-active');
    $('xr-toggle').setAttribute('aria-pressed', 'true');
    $('xr-status').textContent = 'AR ACTIVE';
    session.requestAnimationFrame(onXRFrame);
  } catch (error) {
    showXRNotice(`XR表示を開始できませんでした。全画面プレビューへ切り替えます。(${error.name || 'XR ERROR'})`);
    await toggleXRPreview();
  }
}

async function toggleXRPreview() {
  const active = document.body.classList.toggle('xr-preview');
  $('xr-toggle').setAttribute('aria-pressed', String(active));
  $('xr-status').textContent = active ? 'PREVIEW ACTIVE' : (xrSupported ? 'AR READY' : 'PREVIEW MODE');
  if (active && document.documentElement.requestFullscreen && !document.fullscreenElement) {
    try { await document.documentElement.requestFullscreen(); } catch { /* Fullscreen is optional. */ }
  } else if (!active && document.fullscreenElement) {
    try { await document.exitFullscreen(); } catch { /* Ignore browser restrictions. */ }
  }
}

$('xr-toggle').addEventListener('click', async () => {
  if (xrSession) { await xrSession.end(); return; }
  if (document.body.classList.contains('xr-preview')) { await toggleXRPreview(); return; }
  if (xrSupported) await enterImmersiveAR();
  else await toggleXRPreview();
});
document.addEventListener('fullscreenchange', () => {
  if (!document.fullscreenElement && document.body.classList.contains('xr-preview')) {
    document.body.classList.remove('xr-preview');
    $('xr-toggle').setAttribute('aria-pressed', 'false');
    $('xr-status').textContent = xrSupported ? 'AR READY' : 'PREVIEW MODE';
  }
});

render();
detectXR();
