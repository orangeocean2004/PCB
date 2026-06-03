# OS 进程调度模拟器 使用说明

## 项目简介

一个 Java Spring Boot 实现的操作系统进程调度模拟器，支持 6 种调度算法、内存管理（Best Fit）、ABC 三类系统资源管理、最小消息传递式 IPC，提供 REST API 和 Web 前端。

## 快速启动

### 环境要求

- JDK 17+
- Maven 3.6+

### 启动 Web 服务（默认）

```bash
cd OS_design
mvn spring-boot:run
```

浏览器打开 `http://localhost:8080` 进入操作界面。

### 运行测试

```bash
mvn test -Dtest=SchedulerIntegrationTest
```

集成测试覆盖调度算法、内存管理、资源分配、进程操作、进程通信等场景。

### 运行算法对比报告

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=runner
```

输出 6 种算法在"批量到达"和"错峰到达"两种场景下的周转时间对比表，以及内存管理（Best Fit）的暴力测试结果。

---

## Web 界面操作

界面左侧是控制面板，右侧是状态展示区。

### 提交进程

在左侧面板填写：

| 字段 | 说明 | 示例 |
|------|------|------|
| 总时长 | 进程需要的 CPU 时间 | 5 |
| 优先级 | 数值越小优先级越高 | 1 |
| 需要A/B/C | 三类资源需求（0-10） | 2/0/1 |
| 内存需求 | 单位 KB（64-1024） | 128 |

点击 **提交进程**。

### 时钟控制

- **+1 Tick**：推进一个时钟周期
- **N**：一次推进 N 个周期（输入框填数量）
- **自动推进**：勾选后每秒自动 tick，时钟持续走动

### 调度算法

下拉框切换 6 种算法：FCFS、SJF、HRRN、Priority、RR、Preemptive Priority。

### 自动资源分配

提交进程时只自动尝试分配内存，A/B/C 资源记录在 PCB 中，等进程被调度到运行态后自动申请。运行态资源申请失败的进程进入阻塞队列；内存不足的进程进入创建队列，并在后续 tick 中自动重试。

### 进程操作

每个进程行或运行进程卡片上：

- **X（撤销）**：强制终止进程，回收其全部资源
- **阻塞**：暂停当前运行进程（保留内存和资源）
- **唤醒**：恢复阻塞队列中的进程（需已持有资源）

### 进程通信

左侧 **进程通信** 区填写发送 PID、接收 PID 和消息内容，点击 **发送消息**。消息会进入接收进程的 IPC 收件箱，并在右侧 **IPC 消息** 表格中展示。

该功能采用最小消息传递模型：发送方和接收方都必须是已进入系统且未终止的 PCB；消息不会影响调度，只用于模拟进程间传递文本数据。

---

## REST API 使用

### 端点一览

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/status` | 查询完整模拟状态 |
| POST | `/api/processes` | 提交新进程 |
| GET | `/api/processes/{pid}` | 查询指定进程 |
| DELETE | `/api/processes/{pid}` | 撤销进程 |
| POST | `/api/processes/block` | 阻塞当前运行进程 |
| POST | `/api/processes/{pid}/wakeup` | 唤醒阻塞进程 |
| POST | `/api/ipc/messages` | 发送 IPC 消息 |
| GET | `/api/ipc/messages/{pid}` | 查询指定进程的 IPC 收件箱 |
| DELETE | `/api/ipc/messages/{pid}` | 清空指定进程的 IPC 收件箱 |
| POST | `/api/tick` | 推进 1 个时钟 |
| POST | `/api/tick/{n}` | 推进 N 个时钟（上限 100） |
| PUT | `/api/algorithm/{type}` | 切换调度算法 |
| POST | `/api/reset` | 重置模拟 |

---

## 演示案例

以下使用经典教材数据，能清晰对比不同算法的行为差异。

所有 curl 命令在 Git Bash 或 WSL 下执行。Windows CMD 用户需将 `\` 替换为 `^`。

---

### 案例 1：经典调度算法对比 — FCFS vs SJF vs HRRN

这是操作系统教科书上的经典案例。三个进程同时到达：

| 进程 | 总时长 |
|------|--------|
| P1   | 24     |
| P2   | 3      |
| P3   | 3      |

**FCFS（先来先服务）**

```bash
curl -s -X POST http://localhost:8080/api/reset
curl -s -X PUT http://localhost:8080/api/algorithm/1

# 按 P1→P2→P3 顺序提交
curl -s -X POST http://localhost:8080/api/processes \
  -H 'Content-Type: application/json' \
  -d '{"totalTime":24}'
curl -s -X POST http://localhost:8080/api/processes \
  -H 'Content-Type: application/json' \
  -d '{"totalTime":3}'
curl -s -X POST http://localhost:8080/api/processes \
  -H 'Content-Type: application/json' \
  -d '{"totalTime":3}'

curl -s -X POST http://localhost:8080/api/tick/50
```

执行顺序：**P1 → P2 → P3**

```
P1 周转=24   P2 周转=27   P3 周转=30   平均周转=27.0
```

短作业 P2、P3 被长作业 P1 堵在后面，用户体验极差。

**SJF（短作业优先）**

```bash
curl -s -X POST http://localhost:8080/api/reset
curl -s -X PUT http://localhost:8080/api/algorithm/2

# 同样三个进程
curl -s -X POST http://localhost:8080/api/processes \
  -H 'Content-Type: application/json' \
  -d '{"totalTime":24}'
curl -s -X POST http://localhost:8080/api/processes \
  -H 'Content-Type: application/json' \
  -d '{"totalTime":3}'
curl -s -X POST http://localhost:8080/api/processes \
  -H 'Content-Type: application/json' \
  -d '{"totalTime":3}'

curl -s -X POST http://localhost:8080/api/tick/50
```

执行顺序：**P2 → P3 → P1**

```
P1 周转=30   P2 周转=3    P3 周转=6    平均周转=13.0
```

平均周转从 27 降到 13，直接砍半。这就是短作业优先的核心优势。

**HRRN（高响应比优先）**

```bash
curl -s -X POST http://localhost:8080/api/reset
curl -s -X PUT http://localhost:8080/api/algorithm/3

# 同样三个进程
curl -s -X POST http://localhost:8080/api/processes \
  -H 'Content-Type: application/json' \
  -d '{"totalTime":24}'
curl -s -X POST http://localhost:8080/api/processes \
  -H 'Content-Type: application/json' \
  -d '{"totalTime":3}'
curl -s -X POST http://localhost:8080/api/processes \
  -H 'Content-Type: application/json' \
  -d '{"totalTime":3}'

curl -s -X POST http://localhost:8080/api/tick/50
```

执行顺序也是 **P2 → P3 → P1**，HRRN 兼顾了短作业和长作业的公平性——等待越久，响应比越高，长作业不会被饿死。

---

### 案例 2：RR 时间片轮转 — 交互式体验

两个进程，时间片=3 tick。演示抢占和交替执行。

| 进程 | 总时长 |
|------|--------|
| P1   | 7      |
| P2   | 5      |

```bash
curl -s -X POST http://localhost:8080/api/reset
curl -s -X PUT http://localhost:8080/api/algorithm/5

curl -s -X POST http://localhost:8080/api/processes \
  -H 'Content-Type: application/json' \
  -d '{"totalTime":7}'
curl -s -X POST http://localhost:8080/api/processes \
  -H 'Content-Type: application/json' \
  -d '{"totalTime":5}'

# 逐步推进，观察交替切换
curl -s -X POST http://localhost:8080/api/tick/3
curl -s http://localhost:8080/api/status | python3 -c "
import sys,json; d=json.load(sys.stdin)
rp=d['runningProcess']
print(f'运行中: PID={rp[\"pid\"]} 进度={rp[\"runningTime\"]}/{rp[\"totalTime\"]}')
print(f'就绪队列: {[p[\"pid\"] for p in d[\"readyQueue\"]]}')"

curl -s -X POST http://localhost:8080/api/tick/6
curl -s http://localhost:8080/api/status | python3 -c "
import sys,json; d=json.load(sys.stdin)
rp=d['runningProcess']
print(f'运行中: PID={rp[\"pid\"] if rp else \"无\"}')
print(f'就绪队列: {[p[\"pid\"] for p in d[\"readyQueue\"]]}')
print(f'终止队列: {[p[\"pid\"] for p in d[\"deadQueue\"]]}')"
```

执行时间线：

```
Tick 1-3:  P1 运行 (跑3) → 时间片到 → P1 回队尾
Tick 4-6:  P2 运行 (跑3) → 时间片到 → P2 回队尾
Tick 7-9:  P1 运行 (跑3) → 时间片到 → P1 回队尾
Tick 10-11: P2 运行 (跑2) → 完成退出
Tick 12-13: P1 运行 (跑1) → 完成退出
```

两个进程交替执行，谁都不会长时间独占 CPU——这就是分时系统的核心思想。

---

### 案例 3：资源竞争 — 阻塞与自动唤醒

系统初始 A=10，提交 3 个各需 A=5 的进程。提交阶段只分配内存，进程运行时才申请 A 资源；前 2 个运行过的进程消耗完 A 资源，第 3 个被调度运行时因资源不足进入阻塞队列。等前面进程完成释放 A 后自动恢复。

| 进程 | 总时长 | needA |
|------|--------|-------|
| P1   | 5      | 5     |
| P2   | 5      | 5     |
| P3   | 5      | 5     |

```bash
curl -s -X POST http://localhost:8080/api/reset
curl -s -X PUT http://localhost:8080/api/algorithm/5

# 一次提交 3 个各需 A=5 的进程
for i in 1 2 3; do
  curl -s -X POST http://localhost:8080/api/processes \
    -H 'Content-Type: application/json' \
    -d '{"totalTime":5, "needA":5}'
done

# 推进 7 tick，P1/P2已分别运行并持有A资源，P3运行态申请A失败后阻塞
curl -s -X POST http://localhost:8080/api/tick/7
echo "=== 7 tick 后 ==="
curl -s http://localhost:8080/api/status | python3 -c "
import sys,json; d=json.load(sys.stdin)
rp=d['runningProcess']
print(f'运行中: PID={rp[\"pid\"]} A持有={rp[\"getA\"]}' if rp else '无')
print(f'阻塞队列: {[(p[\"pid\"],p[\"stateString\"]) for p in d[\"blockQueue\"]]}')
print(f'A剩余={d[\"resources\"][\"availableA\"]}')"

# 跑完全部
curl -s -X POST http://localhost:8080/api/tick/20
echo "=== 完成后 ==="
curl -s http://localhost:8080/api/status | python3 -c "
import sys,json; d=json.load(sys.stdin)
print(f'终止: {len(d[\"deadQueue\"])}个 阻塞: {len(d[\"blockQueue\"])}个')"
```

**预期**：7 tick 后 P1 在运行（持 A=5），P2 在就绪（持 A=5），A 剩余 0，P3 在阻塞队列。全部完成后 deadQueue=3，blockQueue=0，A 恢复为 10。

---

### 案例 4：阻塞与唤醒

运行中的进程可以手动阻塞（保留资源，释放 CPU），稍后唤醒继续。

```bash
curl -s -X POST http://localhost:8080/api/reset

# 提交一个长进程（20 tick）
curl -s -X POST http://localhost:8080/api/processes \
  -H 'Content-Type: application/json' \
  -d '{"totalTime":20}'

# 跑 2 tick
curl -s -X POST http://localhost:8080/api/tick/2

# 阻塞它
curl -s -X POST http://localhost:8080/api/processes/block
# → {"success":true}

# 此时 CPU 空闲，但进程仍持有内存和资源
curl -s http://localhost:8080/api/status | python3 -c "
import sys,json; d=json.load(sys.stdin)
print(f'运行中={\"是\" if d[\"runningProcess\"] else \"无\"}')
print(f'阻塞队列长度={len(d[\"blockQueue\"])}')"

# 唤醒
curl -s -X POST http://localhost:8080/api/processes/1/wakeup
# → {"success":true,"pid":1}

# 继续跑完
curl -s -X POST http://localhost:8080/api/tick/30
```

**预期**：阻塞后 runningProcess=null、blockQueue=1。唤醒后进程回到就绪队列，被重新调度执行。

---

## 调度算法说明

| 算法 | 常量 | 策略 | 抢占 |
|------|------|------|------|
| FCFS | 1 | 先来先服务，按到达顺序执行 | 否 |
| SJF | 2 | 短作业优先，选总时长最短的 | 否 |
| HRRN | 3 | 高响应比优先，响应比 = (等待+总时长)/总时长 | 否 |
| Priority | 4 | 优先级调度，数值越小优先级越高 | 否 |
| RR | 5 | 时间片轮转，时间片=3 tick，到点切回队尾 | 是 |
| Preemptive Priority | 6 | 抢占式优先级调度，就绪队列出现更高优先级进程时抢占当前进程 | 是 |

---

## 进程状态流转

```
提交 → [创建态] → (内存分配成功) → [就绪态] → (CPU调度) → [运行态] → (完成) → [终止态]
                        ↑                                │   ↑
                        │                                │   │ 资源申请成功
                        │                        资源申请失败 │
                        │                                ↓   │
                        └────────────── (资源满足) ← [阻塞态]
```

---

## 项目结构

```
src/main/java/com/neu/os_design/
├── OsDesignApplication.java          # 入口
├── controller/
│   └── SimulationController.java     # REST API（15个端点）
├── model/
│   ├── PCB.java                      # 进程控制块（含 IPC 收件箱）
│   └── MemoryBlock.java              # 内存块
├── service/
│   ├── SchedulerService.java         # 调度器接口
│   ├── MemoryService.java            # 内存管理接口
│   ├── SystemResourceService.java    # 资源管理接口
│   └── strategy/
│       ├── SchedulingStrategy.java   # 调度策略接口
│       └── algorithm/                # 6种算法实现
│           ├── FCFS.java
│           ├── SJF.java
│           ├── HRRN.java
│           ├── Priority.java
│           ├── RR.java
│           └── PreemptivePriority.java
├── service/impl/
│   ├── SchedulerServiceImpl.java     # 调度器实现
│   ├── MemoryServiceImpl.java        # 内存管理实现（Best Fit）
│   └── SystemResourceServiceImpl.java # 资源管理实现（Semaphore）
└── runner/
    ├── SimulationRunner.java         # 内存测试（Profile: runner）
    └── AlgorithmComparisonRunner.java # 算法对比（Profile: runner）

src/main/resources/static/
├── index.html                         # Web 前端
├── style.css                          # 样式
├── api.js                             # API 调用
├── render.js                          # DOM 渲染
└── app.js                             # 入口 + 自动 tick

src/test/java/.../
└── SchedulerIntegrationTest.java      # 65个集成测试
```
