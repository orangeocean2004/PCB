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
  await fetch(API + '/processes', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(body)
  });
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
