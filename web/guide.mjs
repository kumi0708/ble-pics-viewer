import { SIGNAL_LABELS, processPics } from './pics.mjs';
import { createType2Packet } from './playground.mjs';

const $ = id => document.getElementById(id);
const state = {
  phase: 0,
  remaining: 8,
  signals: [{ state: 'Green', remaining: 8 }, { state: 'Red', remaining: 8 }],
  auto: false,
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
  state.phase = Number(value) % 24;
  const next = phaseSignals(state.phase);
  state.remaining = next.remaining;
  state.signals = next.signals;
  render();
}

function stopAuto() {
  state.auto = false;
  clearInterval(autoTimer);
  autoTimer = null;
  $('auto-play').setAttribute('aria-pressed', 'false');
  $('auto-play').querySelector('strong').textContent = '自動走査';
}

function startAuto() {
  if (state.auto) return;
  state.auto = true;
  $('auto-play').setAttribute('aria-pressed', 'true');
  $('auto-play').querySelector('strong').textContent = '走査停止';
  autoTimer = setInterval(() => setPhase(state.phase + 1), 850);
}

function cycleSignal(index) {
  stopAuto();
  const order = ['Red', 'Green', 'BlinkGreen'];
  const current = order.indexOf(state.signals[index].state);
  state.signals[index].state = order[(current + 1) % order.length];
  render();
}

function renderTarget(index, parsedSignal) {
  const signal = state.signals[index];
  const target = document.querySelector(`[data-signal="${index}"]`);
  target.dataset.state = signal.state;
  target.querySelector(`[data-signal-label="${index}"]`).textContent = SIGNAL_LABELS[signal.state];
  target.querySelector(`[data-signal-remaining="${index}"]`).textContent = String(parsedSignal.remaining_time).padStart(2, '0');
}

function renderVerdict() {
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
    jp = '横断停止';
    en = 'ALL ROUTES / STOP';
  }

  document.body.dataset.condition = condition;
  $('verdict-jp').textContent = jp;
  $('verdict-en').textContent = en;
  $('verdict-count').textContent = `残り ${String(state.remaining).padStart(2, '0')}`;
}

function render() {
  state.signals.forEach(signal => { signal.remaining = state.remaining; });
  const packet = createType2Packet(state.signals);
  const parsed = processPics(packet);

  parsed.pedestrian_signals.slice(0, 2).forEach((signal, index) => renderTarget(index, signal));
  $('s1-code').textContent = packet[10].toString(16).padStart(2, '0').toUpperCase();
  $('s2-code').textContent = packet[11].toString(16).padStart(2, '0').toUpperCase();
  $('phase-number').textContent = String(state.phase).padStart(2, '0');
  $('rx-time').textContent = new Date().toLocaleTimeString('ja-JP', { hour12: false });
  renderVerdict();
}

$('auto-play').addEventListener('click', () => state.auto ? stopAuto() : startAuto());
$('next-state').addEventListener('click', () => {
  stopAuto();
  setPhase(state.phase + 4);
});
document.querySelectorAll('[data-signal]').forEach(button => {
  button.addEventListener('click', () => cycleSignal(Number(button.dataset.signal)));
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
    $('xr-status').textContent = 'PREVIEW';
    return;
  }
  try {
    xrSupported = await navigator.xr.isSessionSupported('immersive-ar');
    $('xr-status').textContent = xrSupported ? 'AR READY' : 'PREVIEW';
  } catch {
    $('xr-status').textContent = 'PREVIEW';
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
  $('xr-toggle').querySelector('strong').textContent = 'ARを起動';
  $('xr-status').textContent = xrSupported ? 'AR READY' : 'PREVIEW';
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
    $('xr-toggle').querySelector('strong').textContent = 'ARを終了';
    $('xr-status').textContent = 'AR ACTIVE';
    session.requestAnimationFrame(onXRFrame);
  } catch (error) {
    showXRNotice(`ARを開始できないため端末プレビューを表示します（${error.name || 'XR ERROR'}）`);
    toggleXRPreview();
  }
}

function toggleXRPreview() {
  const active = document.body.classList.toggle('xr-preview');
  $('xr-toggle').setAttribute('aria-pressed', String(active));
  $('xr-toggle').querySelector('strong').textContent = active ? '表示を戻す' : 'ARを起動';
  $('xr-status').textContent = active ? 'PREVIEW ACTIVE' : (xrSupported ? 'AR READY' : 'PREVIEW');
}

$('xr-toggle').addEventListener('click', async () => {
  if (xrSession) {
    await xrSession.end();
    return;
  }
  if (document.body.classList.contains('xr-preview')) {
    toggleXRPreview();
    return;
  }
  if (xrSupported) await enterImmersiveAR();
  else toggleXRPreview();
});

render();
detectXR();
