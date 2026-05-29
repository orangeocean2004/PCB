const API = '/api';

async function submitProcess() {
  const body = {
    totalTime: +document.getElementById('fTotalTime').value,
    priority: +document.getElementById('fPriority').value,
    needA: +document.getElementById('fNeedA').value,
    needB: +document.getElementById('fNeedB').value,
    needC: +document.getElementById('fNeedC').value,
    memoryNeed: +document.getElementById('fMemory').value
  };
  const res = await fetch(API + '/processes', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(body)
  });

  const result = await readJsonResponse(res);
  if (!res.ok) {
    throw new Error(result.message || '提交失败');
  }
  return result;
}

async function submitBatchProcesses() {
  const text = document.getElementById('batchInput').value;
  const processes = parseBatchInput(text);
  if (processes.length === 0) return;

  const res = await fetch(API + '/processes/batch', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(processes)
  });

  const result = await readJsonResponse(res);
  if (!res.ok) {
    throw new Error(result.message || '批量提交失败');
  }
  if ((result.rejectedCount || 0) > 0) {
    console.warn('批量提交中被拒绝的进程:', result.rejected);
    alert('有 ' + result.rejectedCount + ' 个进程被拒绝，原因见控制台或返回结果。');
  }
  return result;
}

async function readJsonResponse(res) {
  try {
    return await res.json();
  } catch {
    return {};
  }
}

function parseBatchInput(text) {
  return text.split(/\r?\n/)
    .map(line => line.split('#')[0].trim())
    .filter(Boolean)
    .map(parseBatchLine);
}

function parseBatchLine(line) {
  const parts = line.split(/[,\s，]+/).filter(Boolean);
  if (parts.length < 2) {
    throw new Error('批量提交行缺少字段: ' + line);
  }

  const submitToken = parts[0];
  const process = {
    totalTime: readBatchNumber(parts[1], 5),
    priority: readBatchNumber(parts[2], 1),
    needA: readBatchNumber(parts[3], 0),
    needB: readBatchNumber(parts[4], 0),
    needC: readBatchNumber(parts[5], 0),
    memoryNeed: readBatchNumber(parts[6], 64)
  };

  const clock = normalizeBatchClock(submitToken);
  if (clock) {
    process.submitClock = clock;
  } else {
    process.submitTime = readBatchNumber(submitToken, 0);
  }

  return process;
}

function readBatchNumber(value, fallback) {
  const number = Number.parseInt(value, 10);
  return Number.isFinite(number) ? number : fallback;
}

function normalizeBatchClock(value) {
  const match = String(value).trim().replace('：', ':').match(/^(\d{1,2}):(\d{1,2})$/);
  if (!match) return null;

  const hour = Number.parseInt(match[1], 10);
  const minute = Number.parseInt(match[2], 10);
  if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;

  return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
}

async function cancelProcess(pid) {
  await fetch(API + '/processes/' + pid, {method: 'DELETE'});
}

async function blockProcess() {
  await fetch(API + '/processes/block', {method: 'POST'});
}

async function wakeupProcess(pid) {
  await fetch(API + '/processes/' + pid + '/wakeup', {method: 'POST'});
}

async function tickApi() {
  await fetch(API + '/tick', {method: 'POST'});
}

async function tickNApi(n) {
  await fetch(API + '/tick/' + Math.min(n, 100), {method: 'POST'});
}

async function setAlgorithmApi(type) {
  await fetch(API + '/algorithm/' + type, {method: 'PUT'});
}

async function resetSimApi() {
  await fetch(API + '/reset', {method: 'POST'});
}

async function setDispatchModeApi(manual) {
  await fetch(API + '/dispatch-mode', {
    method: 'PUT',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({manual: manual})
  });
}

async function dispatchResourcesApi() {
  await fetch(API + '/dispatch', {method: 'POST'});
}

async function fetchStatus() {
  const res = await fetch(API + '/status');
  return res.json();
}
