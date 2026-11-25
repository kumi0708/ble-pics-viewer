import { SIGNAL_LABELS, demoPackets, hexDump, processPics } from './pics.mjs';
import { createType2Packet, encodeSignalByte } from './playground.mjs';

const $ = id => document.getElementById(id);
const flow = $('flow');
const flowStatus = $('flow-status');
const flowTimers = [];
let cycleTimer = null;
let selectedType = 2;
const builderSignals = [
  { state: 'Green', remaining: 8 },
  { state: 'Red', remaining: 8 },
];

const PRESETS = {
  normal: [{ state: 'Green', remaining: 8 }, { state: 'Red', remaining: 8 }],
  switching: [{ state: 'BlinkGreen', remaining: 2 }, { state: 'Red', remaining: 2 }],
  'all-red': [{ state: 'Red', remaining: 4 }, { state: 'Red', remaining: 4 }],
  blinking: [{ state: 'BlinkGreen', remaining: 3 }, { state: 'Green', remaining: 6 }],
};

function clearFlow() {
  while (flowTimers.length) clearTimeout(flowTimers.pop());
  flow.classList.remove('is-running');
  flow.querySelectorAll('.flow-step').forEach(step => step.classList.remove('is-active', 'is-complete'));
}

$('play-flow').addEventListener('click', () => {
  clearFlow();
  void flow.offsetWidth;
  flow.classList.add('is-running');
  const messages = ['信号制御機が最新状態を用意', 'BLEアドバタイズとして周囲へ配信', 'ブラウザがメーカーID 0x01CEを受信', 'Typeに応じてバイト列を解析', '信号状態として画面に反映'];
  flow.querySelectorAll('.flow-step').forEach((step, index) => {
    flowTimers.push(setTimeout(() => {
      flow.querySelectorAll('.flow-step').forEach((item, itemIndex) => {
        item.classList.toggle('is-complete', itemIndex < index);
        item.classList.toggle('is-active', itemIndex === index);
      });
      flowStatus.textContent = messages[index];
    }, index * 800));
  });
  flowTimers.push(setTimeout(() => {
    flow.querySelectorAll('.flow-step').forEach(step => {
      step.classList.remove('is-active');
      step.classList.add('is-complete');
    });
    flow.classList.remove('is-running');
    flowStatus.textContent = '受信から表示まで完了。GATT接続や信号機への送信は行いません。';
  }, 4100));
});

function samplePacket(type, step = 0) {
  const packets = demoPackets(step);
  if (step > 0) return packets[0];
  return packets[type];
}

function packetGroups(bytes, type) {
  const groups = [
    ['共通ヘッダー', bytes.subarray(0, 2)],
    [`Type ${type}`, bytes.subarray(2, 4)],
    ['予約領域', bytes.subarray(4, 6)],
    ['交差点ID', bytes.subarray(6, 10)],
    [type === 0 ? '交差点名' : type === 1 ? '緯度・経度' : '信号データ', bytes.subarray(10)],
  ];
  return groups.map(([label, data], index) => {
    const group = document.createElement('div');
    group.className = `byte-group${index === 4 ? ' is-payload' : ''}`;
    const value = document.createElement('strong');
    value.textContent = hexDump(data) || '—';
    const name = document.createElement('span');
    name.textContent = label;
    group.append(value, name);
    return group;
  });
}

function decodedFields(info) {
  const fields = [
    ['メッセージ種別', `Type ${info.message_type}`],
    ['交差点ID', info.intersection_id],
  ];
  if (info.message_type === 0) fields.push(['識別名称', info.identifier]);
  if (info.message_type === 1) {
    fields.push(['緯度', info.latitude.toFixed(6)], ['経度', info.longitude.toFixed(6)]);
  }
  if (info.message_type === 2) {
    info.pedestrian_signals.slice(0, 2).forEach((signal, index) => {
      const remaining = signal.remaining_time < 0 ? '不明' : signal.remaining_time;
      fields.push([`信号${index + 1}`, `${SIGNAL_LABELS[signal.signal_state]} / 残り値 ${remaining}`]);
    });
  }
  return fields.map(([label, value]) => {
    const row = document.createElement('div');
    const dt = document.createElement('dt');
    const dd = document.createElement('dd');
    dt.textContent = label;
    dd.textContent = value;
    row.append(dt, dd);
    return row;
  });
}

function renderPacket(type) {
  selectedType = Number(type);
  const packet = samplePacket(selectedType);
  const info = processPics(packet);
  $('byte-groups').replaceChildren(...packetGroups(packet, selectedType));
  $('decoded').replaceChildren(...decodedFields(info));
  document.querySelectorAll('.type-tab').forEach(tab => {
    const active = Number(tab.dataset.type) === selectedType;
    tab.classList.toggle('is-active', active);
    tab.setAttribute('aria-selected', String(active));
  });
}

document.querySelectorAll('.type-tab').forEach(tab => {
  tab.addEventListener('click', () => renderPacket(tab.dataset.type));
});

function cloneScenario(scenario) {
  return scenario.map(signal => ({ ...signal }));
}

function setBuilderSignals(nextSignals) {
  builderSignals.splice(0, builderSignals.length, ...cloneScenario(nextSignals));
  renderBuilder();
}

function renderBuilder() {
  const packet = createType2Packet(builderSignals);
  const info = processPics(packet);
  info.pedestrian_signals.slice(0, 2).forEach((signal, index) => {
    const setting = builderSignals[index];
    const card = document.querySelector(`[data-builder-signal="${index}"]`);
    card.dataset.state = signal.signal_state;
    card.querySelector('.builder-state').textContent = SIGNAL_LABELS[signal.signal_state];
    card.querySelector('.builder-remaining').textContent = `残り値 ${signal.remaining_time}`;
    const range = document.querySelector(`[data-remaining="${index}"]`);
    range.value = setting.remaining;
    $(`signal-${index}-output`).textContent = setting.remaining;
    $(`signal-${index}-byte`).textContent = `信号${index + 1}: 0x${encodeSignalByte(setting.state, setting.remaining).toString(16).padStart(2, '0').toUpperCase()}`;
    document.querySelectorAll(`[data-signal="${index}"][data-state]`).forEach(button => {
      button.setAttribute('aria-pressed', String(button.dataset.state === setting.state));
    });
  });
  $('builder-raw').textContent = hexDump(packet);
  const preview = $('builder-preview');
  preview.classList.remove('is-updating');
  void preview.offsetWidth;
  preview.classList.add('is-updating');
}

document.querySelectorAll('[data-signal][data-state]').forEach(button => {
  button.addEventListener('click', () => {
    builderSignals[Number(button.dataset.signal)].state = button.dataset.state;
    renderBuilder();
  });
});

document.querySelectorAll('[data-remaining]').forEach(range => {
  range.addEventListener('input', () => {
    builderSignals[Number(range.dataset.remaining)].remaining = Number(range.value);
    renderBuilder();
  });
});

document.querySelectorAll('[data-preset]').forEach(button => {
  button.addEventListener('click', () => setBuilderSignals(PRESETS[button.dataset.preset]));
});

$('swap-signals').addEventListener('click', () => setBuilderSignals([builderSignals[1], builderSignals[0]]));

$('random-preset').addEventListener('click', () => {
  const states = ['Red', 'Green', 'BlinkGreen'];
  setBuilderSignals([0, 1].map(() => ({
    state: states[Math.floor(Math.random() * states.length)],
    remaining: Math.floor(Math.random() * 8) + 1,
  })));
});

function renderSignals(info, step) {
  info.pedestrian_signals.slice(0, 2).forEach((signal, index) => {
    const card = document.querySelector(`.demo-signal[data-signal="${index}"]`);
    card.dataset.state = signal.signal_state;
    card.querySelector('.demo-state').textContent = SIGNAL_LABELS[signal.signal_state];
    card.querySelector('.demo-remaining').textContent = `残り値 ${signal.remaining_time < 0 ? '—' : signal.remaining_time}`;
  });
  const percent = Math.round((step / 23) * 100);
  $('cycle-progress').style.width = `${percent}%`;
  document.querySelector('.cycle-track').setAttribute('aria-valuenow', String(step));
  if (selectedType === 2) {
    $('byte-groups').replaceChildren(...packetGroups(samplePacket(2, step), 2));
    $('decoded').replaceChildren(...decodedFields(info));
  }
}

function stopCycle() {
  if (cycleTimer) clearInterval(cycleTimer);
  cycleTimer = null;
  $('play-cycle').disabled = false;
}

$('play-cycle').addEventListener('click', () => {
  stopCycle();
  let step = 0;
  $('play-cycle').disabled = true;
  $('cycle-status').textContent = 'Type 2パケットを順に受信しています…';
  renderSignals(processPics(samplePacket(2, step)), step);
  cycleTimer = setInterval(() => {
    step += 1;
    renderSignals(processPics(samplePacket(2, step)), step);
    if (step >= 23) {
      stopCycle();
      $('cycle-status').textContent = '1サイクルの再生が完了しました。表示は説明用の架空データです。';
    }
  }, 300);
});

document.addEventListener('visibilitychange', () => {
  if (document.hidden) stopCycle();
});

renderPacket(2);
renderSignals(processPics(samplePacket(2)), 0);
renderBuilder();
