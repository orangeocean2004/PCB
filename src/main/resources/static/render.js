function renderStatus(data) {
  document.getElementById('clockTime').textContent = data.currentTime;
  document.getElementById('algoSelect').value = data.algorithmType;

  renderRunningProcess(data.runningProcess);
  renderQueues(data);
  renderRunningActions(data);
  renderResources(data.resources);
  renderMemory(data.memory);
  renderMode(data.manualDispatchMode);
}

function renderRunningProcess(rp) {
  const div = document.getElementById('runningInfo');
  if (!rp) {
    div.innerHTML = '<span class="idle">CPU 空闲</span>';
    return;
  }
  const pct = rp.totalTime > 0 ? Math.round(rp.runningTime / rp.totalTime * 100) : 0;
  div.innerHTML = `<div class="running-info">
    <div class="item"><div class="label">PID</div><div class="value">${rp.pid}</div></div>
    <div class="item"><div class="label">优先级</div><div class="value">${rp.priority}</div></div>
    <div class="item"><div class="label">进度</div><div class="value">${rp.runningTime}/${rp.totalTime} (${pct}%)</div></div>
    <div class="item"><div class="label">需要A/B/C</div><div class="value">${rp.needA}/${rp.needB}/${rp.needC}</div></div>
    <div class="item"><div class="label">已得A/B/C</div><div class="value">${rp.getA}/${rp.getB}/${rp.getC}</div></div>
    <div class="item"><div class="label">内存</div><div class="value">${rp.allocatedMemory}/${rp.memoryNeed}KB</div></div>
    <div class="item"><div class="label">响应比</div><div class="value">${rp.responseRatio?.toFixed(2)}</div></div>
    <div class="item"><div class="label">等待时间</div><div class="value">${rp.waitingTime}</div></div>
  </div>`;
}

function renderQueues(data) {
  renderQueue('readyBody', data.readyQueue, 'ready', 'readyCount');
  renderQueue('blockBody', data.blockQueue, 'block', 'blockCount');
  renderQueue('jobBody', data.jobQueue, 'job', 'jobCount');
  renderQueue('deadBody', data.deadQueue, 'dead', 'deadCount');
}

function renderQueue(tbodyId, queue, type, countId) {
  const tbody = document.getElementById(tbodyId);
  document.getElementById(countId).textContent = queue.length;

  if (queue.length === 0) {
    const cols = tbody.parentElement.querySelector('thead tr').children.length;
    tbody.innerHTML = `<tr><td colspan="${cols}" class="empty-row">空</td></tr>`;
    return;
  }

  tbody.innerHTML = queue.map(p => {
    let cols = `<td><b>PID=${p.pid}</b></td>`;
    cols += `<td>${p.totalTime}</td>`;
    if (type === 'ready' || type === 'block') cols += `<td>${p.runningTime}</td>`;
    cols += `<td>${p.priority}</td>`;
    if (type === 'ready')  cols += `<td>${p.allocatedMemory}/${p.memoryNeed}KB</td>`;
    if (type === 'job')    cols += `<td>${p.memoryNeed}KB</td><td>${p.needA}/${p.needB}/${p.needC}</td>`;
    if (type === 'block')  cols += `<td>${p.getA}/${p.getB}/${p.getC}</td>`;
    if (type === 'dead')   cols += `<td>${p.turnaroundTime || '-'}</td><td><span class="badge badge-dead">终止</span></td>`;

    let actions = '<td><div class="process-actions">';
    actions += `<button class="btn btn-danger btn-sm" onclick="cancel(${p.pid})">X</button>`;
    if (type === 'block') {
      actions += `<button class="btn btn-sm green" onclick="wakeup(${p.pid})">唤醒</button>`;
    }
    actions += '</div></td>';
    cols += actions;

    return `<tr>${cols}</tr>`;
  }).join('');
}

function renderRunningActions(data) {
  const card = document.getElementById('runningCard');
  let bar = card.querySelector('.running-actions');
  if (bar) bar.remove();

  if (data.runningProcess) {
    bar = document.createElement('div');
    bar.className = 'running-actions';
    bar.innerHTML = `<button class="btn btn-danger btn-sm" onclick="cancel(${data.runningProcess.pid})">撤销</button>
      <button class="btn btn-sm warn" onclick="block()">阻塞</button>`;
    card.appendChild(bar);
  }
}

function renderResources(res) {
  document.getElementById('resA').textContent = res.availableA;
  document.getElementById('resB').textContent = res.availableB;
  document.getElementById('resC').textContent = res.availableC;
  const cpuEl = document.getElementById('cpuStatus');
  cpuEl.textContent = res.cpuAvailable ? '空闲' : '占用';
  cpuEl.className = 'val ' + (res.cpuAvailable ? '' : 'busy');
}

function renderMemory(mem) {
  document.getElementById('memUsed').textContent = mem.usedMemory;
  document.getElementById('memFree').textContent = 1024 - mem.usedMemory;
  document.getElementById('memMaxFree').textContent = mem.maxAvailable;
  document.getElementById('memPartitions').textContent = mem.freePartitionCount;

  const bar = document.getElementById('memoryBar');
  bar.innerHTML = '';
  mem.blocks.forEach(b => {
    const seg = document.createElement('div');
    seg.className = 'seg ' + (b.free ? 'free' : 'used');
    seg.style.width = (b.size / 1024 * 100) + '%';
    seg.title = b.free ? `空闲 ${b.size}KB [${b.startAddress}-${b.startAddress + b.size - 1}]`
      : `PID=${b.occupantPid}: ${b.size}KB [${b.startAddress}-${b.startAddress + b.size - 1}]`;
    bar.appendChild(seg);
  });
}

function renderMode(manual) {
  document.getElementById('manualMode').checked = manual;
  document.getElementById('modeLabel').textContent = manual
    ? '手动分配模式 (当前: 手动)' : '手动分配模式 (当前: 自动)';
}
