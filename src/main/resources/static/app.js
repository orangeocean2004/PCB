// ==================== 操作入口（调用 api.js） ====================

function submit() { submitProcess().then(refresh).catch(e => alert(e.message)); }
function submitBatch() { submitBatchProcesses().then(refresh).catch(e => alert(e.message)); }
function cancel(pid) { cancelProcess(pid).then(refresh); }
function block() { blockProcess().then(refresh); }
function wakeup(pid) { wakeupProcess(pid).then(refresh); }
function tick() { tickApi().then(refresh); }
function tickN() {
  const n = +document.getElementById('tickN').value || 5;
  tickNApi(n).then(refresh);
}
function setAlgorithm(type) { setAlgorithmApi(type).then(refresh); }
function resetSim() { resetSimApi().then(refresh); }

// ==================== 自动 tick ====================

let autoInterval = null;
function toggleAutoTick() {
  if (document.getElementById('autoTick').checked) {
    autoInterval = setInterval(() => { tickApi().then(refresh); }, 1000);
  } else {
    clearInterval(autoInterval);
  }
}

// ==================== 刷新循环 ====================

async function refresh() {
  try {
    const data = await fetchStatus();
    renderStatus(data);
  } catch(e) { console.error(e); }
}

refresh();
