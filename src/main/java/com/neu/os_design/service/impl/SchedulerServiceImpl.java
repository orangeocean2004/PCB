package com.neu.os_design.service.impl;

import com.neu.os_design.model.PCB;
import com.neu.os_design.service.MemoryService;
import com.neu.os_design.service.SchedulerService;
import com.neu.os_design.service.SystemResourceService;
import com.neu.os_design.service.strategy.SchedulingStrategy;
import com.neu.os_design.service.strategy.algorithm.*;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

@Service
public class SchedulerServiceImpl implements SchedulerService {

    private static final int INITIAL_MEMORY_SIZE = 1024;
    private static final int INITIAL_A = 10;
    private static final int INITIAL_B = 10;
    private static final int INITIAL_C = 10;
    private static final int TIME_SLICE = 3; // 时间片长度 3 个时钟周期

    private final MemoryService memoryService;
    private final SystemResourceService resourceService;

    private int currentTime = 0;

    private SchedulingStrategy currentStrategy = new HRRN();
    private int currentAlgorithmType = SchedulerService.ALGO_HRRN;

    private int timeSliceCounter = 0;

    // 队列定义
    private final List<PCB> pendingQueue = new LinkedList<>(); // 待提交队列
    private final List<PCB> jobQueue = new LinkedList<>();   // 创建态队列
    private final List<PCB> readyQueue = new LinkedList<>();  // 就绪态队列
    private final List<PCB> manualBlockQueue = new LinkedList<>(); // 阻塞队列
    private final List<PCB> blockQueueA = new LinkedList<>(); // 阻塞态队列 A 资源
    private final List<PCB> blockQueueB = new LinkedList<>(); // 阻塞态队列 B 资源
    private final List<PCB> blockQueueC = new LinkedList<>(); // 阻塞态队列 C 资源
    private final List<PCB> deadQueue = new LinkedList<>();   // 终止态队列

    private PCB runningProcess = null;    // 当前正在运行的进程

    public SchedulerServiceImpl(MemoryService memoryService, SystemResourceService resourceService) {
        this.memoryService = memoryService;
        this.resourceService = resourceService;
    }

    @Override
    public PCB submitProcess(int totalTime, int priority, int needA, int needB, int needC, int memoryNeed) {
        return submitProcessAt(currentTime, null, totalTime, priority, needA, needB, needC, memoryNeed);
    }

    @Override
    public PCB submitProcessAt(int submitTime, String submitClock,
                               int totalTime, int priority, int needA, int needB, int needC, int memoryNeed) {
        validateMemoryNeed(memoryNeed);
        validateResourceCapacity(needA, needB, needC);

        int actualSubmitTime = Math.max(0, submitTime);
        PCB pcb = new PCB(totalTime, priority, needA, needB, needC, memoryNeed, actualSubmitTime);
        pcb.setSubmitClock(submitClock);

        if (actualSubmitTime > currentTime) {
            pcb.setState(PCB.SCHEDULED);
            pendingQueue.add(pcb);
            pendingQueue.sort(Comparator.comparingInt(PCB::getArrivalTime).thenComparingInt(PCB::getPid));
            System.out.println("[定时提交] PID=" + pcb.getPid() + " 将在 T=" + actualSubmitTime
                    + formatSubmitClock(pcb) + " 提交");
            return pcb;
        }

        admitProcess(pcb);
        return pcb;
    }

    private void validateMemoryNeed(int memoryNeed) {
        if (memoryNeed <= PCB.MAX_MEMORY_NEED) {
            return;
        }

        String message = "内存需求超过系统上限: memoryNeed=" + memoryNeed + "KB/" + PCB.MAX_MEMORY_NEED + "KB";
        System.out.println("[提交拒绝] " + message);
        throw new IllegalArgumentException(message);
    }

    private void admitProcess(PCB pcb) {
        pcb.setState(PCB.CREATED);

        if (!allocateMemoryForReady(pcb, "[提交进程]")) {
            jobQueue.add(pcb);
            System.out.println("[提交进程] PID=" + pcb.getPid() + " 内存分配失败，进入 [创建队列]");
        }

    }

    private String formatSubmitClock(PCB pcb) {
        if (pcb.getSubmitClock() == null || pcb.getSubmitClock().isBlank()) {
            return "";
        }
        return " (" + pcb.getSubmitClock() + ")";
    }

    private void validateResourceCapacity(int needA, int needB, int needC) {
        if (resourceService.checkResourcesWithinTotal(needA, needB, needC)) {
            return;
        }

        String message = "资源请求超过系统总量: needA=" + needA + "/" + resourceService.getTotalA()
                + ", needB=" + needB + "/" + resourceService.getTotalB()
                + ", needC=" + needC + "/" + resourceService.getTotalC();
        System.out.println("[提交拒绝] " + message);
        throw new IllegalArgumentException(message);
    }

    @Override
    public void systemTick() {
        // 全局时间 ++
        currentTime++;
        System.out.println("============== [当前系统时钟: " + currentTime + "] ==============");

        // 1. 将到达提交时间的进程正式送入系统
        releaseDueSubmissions();

        // 2. 更新所有等待队伍
        updateAllWaitTimes();

        // 3. 检查创建队列，有多余内存时自动调入就绪队列
        checkJobQueueForMemory();

        // 4. 抢占式优先级调度：新到达/新就绪的高优先级进程立即抢占
        preemptRunningProcessIfNeeded();

        // 5. CPU 上正在运行的进程的时间片、进度判断：跑完？时间片到了？
        manageRunningProcess();

        // 6. 运行进程可能刚释放内存，立即重试创建队列，避免等到下一个 tick
        checkJobQueueForMemory();

        // 7. 检查资源阻塞队列，有多余资源时自动调入就绪队列
        checkBlockQueueForResources();

        // 8. 被唤醒的高优先级进程也可以立即抢占
        preemptRunningProcessIfNeeded();

        // 9. CPU 空闲就调度，调度后运行态自动申请资源
        scheduleNextProcess();
    }

    private void releaseDueSubmissions() {
        var it = pendingQueue.iterator();
        while (it.hasNext()) {
            PCB pcb = it.next();
            if (pcb.getArrivalTime() > currentTime) {
                break;
            }
            it.remove();
            System.out.println("[到达提交时间] PID=" + pcb.getPid() + " 在 T=" + currentTime
                    + formatSubmitClock(pcb) + " 进入系统");
            admitProcess(pcb);
        }
    }

    private void updateAllWaitTimes() {
        // 遍历就绪队列的进程，更新等待时间和响应比
        for (PCB pcb : readyQueue) {
            pcb.updateWaitingTime(currentTime);
            pcb.calculateResponseRatio();
        }
    }

    private void checkJobQueueForMemory() {
        var it = jobQueue.iterator();
        while (it.hasNext()) {
            PCB pcb = it.next();
            if (allocateMemoryForReady(pcb, "[创建队列]")) {
                it.remove();
            }
        }
    }

    private boolean allocateMemoryForReady(PCB pcb, String source) {
        if (!memoryService.allocateMemory(pcb.getPid(), pcb.getMemoryNeed())) {
            return false;
        }

        pcb.setAllocatedMemory(pcb.getMemoryNeed());
        moveToReady(pcb, source + " PID=" + pcb.getPid() + " 内存分配成功，进入 [就绪队列]，资源将在运行态申请");
        return true;
    }

    private void moveToReady(PCB pcb, String message) {
        pcb.setState(PCB.READY);
        readyQueue.add(pcb);
        if (message != null && !message.isBlank()) {
            System.out.println(message);
        }
    }

    private void clearRunningSlot() {
        //重置输入
        runningProcess = null;
        timeSliceCounter = 0;
    }

    private void checkBlockQueueForResources() {
        checkAndWakeup(blockQueueA, "A");
        checkAndWakeup(blockQueueB, "B");
        checkAndWakeup(blockQueueC, "C");
    }

    private void checkAndWakeup(List<PCB> blockQueue, String resourceName) {
        var it = blockQueue.iterator();
        while (it.hasNext()) {
            PCB pcb = it.next();
            if (resourceService.allocateResources(pcb)) {
                it.remove();
                moveToReady(pcb, "[阻塞队列" + resourceName + "] PID=" + pcb.getPid() + " 资源分配成功，进入 [就绪队列]");
            }
        }
    }

    private void preemptRunningProcessIfNeeded() {
        //判断是否要发生被动抢占
        if (!(currentStrategy instanceof PreemptivePriority preemptivePriority)
                || runningProcess == null
                || readyQueue.isEmpty()) {
            return;
        }

        PCB bestCandidate = currentStrategy.selectNextProcess(readyQueue);
        //判断最优候选进程和当前运行进程的优先级关系，如果没有更高优先级的候选进程，则不抢占
        if (bestCandidate == null || !preemptivePriority.hasHigherPriority(bestCandidate, runningProcess)) {
            return;
        }

        System.out.println("[抢占] PID=" + bestCandidate.getPid() + " 优先级高于 PID="
                + runningProcess.getPid() + "，当前进程回到 [就绪队列]");

        resourceService.releaseCpu();
        PCB preemptedProcess = runningProcess;
        clearRunningSlot();
        moveToReady(preemptedProcess, null);
    }

    private void manageRunningProcess() {
        if (runningProcess == null) {
            return;
        }

        boolean isDead = runningProcess.runProcess(currentTime);
        if (currentStrategy instanceof RR) {
            timeSliceCounter++;
        }
        System.out.println("[运行中] PID=" + runningProcess.getPid() + " 剩余时间: " + (runningProcess.getTotalTime() - runningProcess.getRunningTime()));


        if (isDead) {
            PCB finishedProcess = runningProcess;
            resourceService.releaseCpu();
            clearRunningSlot();
            terminateProcess(finishedProcess, true, true,
                    "[完成] PID=" + finishedProcess.getPid() + " 进程完成，进入 [终止队列]");
        } else if (currentStrategy instanceof RR && timeSliceCounter >= TIME_SLICE) {
            System.out.println("[时间片到] PID=" + runningProcess.getPid() + " 时间片到，回到 [就绪队列] 队尾");

            // 时间片到，回收CPU资源，转移回就绪队列
            resourceService.releaseCpu();
            PCB expiredProcess = runningProcess;
            clearRunningSlot();
            moveToReady(expiredProcess, null);
        }
    }

    private void scheduleNextProcess() {
        // 获取下一个要运行的进程，占有CPU互斥锁
        if (runningProcess != null) {
            return;
        }

        while (!readyQueue.isEmpty()) {
            // 根据现有调度算法从就绪队列中选出一个最优的进程
            PCB bestCandidate = currentStrategy.selectNextProcess(readyQueue);
            if (bestCandidate == null) {
                return;
            }

            if (!resourceService.allocateCpu()) {
                System.err.println("[异常] CPU 被占用，PID=" + bestCandidate.getPid() + " 调度失败");
                return;
            }

            readyQueue.remove(bestCandidate);
            runningProcess = bestCandidate;
            runningProcess.setState(PCB.RUNNING);

            if (runningProcess.getStartTime() == -1) {
                runningProcess.setStartTime(currentTime);
            }

            System.out.println("[调度][" + currentStrategy.getAlgorithmName() + "] PID=" + runningProcess.getPid()
                    + " 被调度运行，开始自动申请资源 响应比: " + runningProcess.getResponseRatio());

            if (requestResourcesForRunningProcess()) {
                return;
            }
        }
    }

    private boolean requestResourcesForRunningProcess() {
        if (runningProcess == null) {
            return false;
        }

        if (resourceService.allocateResources(runningProcess)) {
            System.out.println("[运行申请资源] PID=" + runningProcess.getPid() + " 资源申请成功，继续运行");
            return true;
        }

        PCB blockedProcess = runningProcess;
        resourceService.releaseCpu();
        blockedProcess.setState(PCB.BLOCK);
        clearRunningSlot();
        addToMissingResourceBlockQueue(blockedProcess, "[运行申请资源]");
        return false;
    }

    private void addToMissingResourceBlockQueue(PCB pcb, String source) {
        if (pcb.getNeedA() - pcb.getGetA() > resourceService.getAvailableA()) {
            blockQueueA.add(pcb);
            System.out.println(source + " PID=" + pcb.getPid() + " 缺少A资源，进入 [阻塞队列A]");
        } else if (pcb.getNeedB() - pcb.getGetB() > resourceService.getAvailableB()) {
            blockQueueB.add(pcb);
            System.out.println(source + " PID=" + pcb.getPid() + " 缺少B资源，进入 [阻塞队列B]");
        } else {
            blockQueueC.add(pcb);
            System.out.println(source + " PID=" + pcb.getPid() + " 缺少C资源，进入 [阻塞队列C]");
        }
    }

    @Override
    public List<PCB> getPendingQueue() {
        return pendingQueue;
    }

    @Override
    public List<PCB> getReadyQueue() {
        return readyQueue;
    }

    @Override
    public List<PCB> getBlockQueue() {
        List<PCB> combined = new LinkedList<>();
        combined.addAll(manualBlockQueue);
        combined.addAll(blockQueueA);
        combined.addAll(blockQueueB);
        combined.addAll(blockQueueC);
        return combined;
    }

    @Override
    public List<PCB> getBlockQueueA() {
        return blockQueueA;
    }

    @Override
    public List<PCB> getBlockQueueB() {
        return blockQueueB;
    }

    @Override
    public List<PCB> getBlockQueueC() {
        return blockQueueC;
    }

    @Override
    public List<PCB> getDeadQueue() {
        return deadQueue;
    }

    @Override
    public PCB getRunningProcess() {
        return runningProcess;
    }

    @Override
    public int getCurrentTime() {
        return currentTime;
    }

    @Override
    public List<PCB> getJobQueue() {
        return jobQueue;
    }

    @Override
    public void resetClock() {
        currentTime = 0;
        timeSliceCounter = 0;
        runningProcess = null;
        pendingQueue.clear();
        jobQueue.clear();
        readyQueue.clear();
        manualBlockQueue.clear();
        blockQueueA.clear();
        blockQueueB.clear();
        blockQueueC.clear();
        deadQueue.clear();
        memoryService.resetMemory(INITIAL_MEMORY_SIZE);
        resourceService.resetResources(INITIAL_A, INITIAL_B, INITIAL_C);
        com.neu.os_design.model.PCB.resetPidSeq();
    }

    @Override
    public void setAlgorithmType(int algorithmType) {
        this.currentAlgorithmType = algorithmType;
        switch (algorithmType) {
            case SchedulerService.ALGO_FCFS:
                this.currentStrategy = new FCFS();
                break;
            case SchedulerService.ALGO_SJF:
                this.currentStrategy = new SJF();
                break;
            case SchedulerService.ALGO_HRRN:
                this.currentStrategy = new HRRN();
                break;
            case SchedulerService.ALGO_PRIORITY:
                this.currentStrategy = new Priority();
                break;
            case SchedulerService.ALGO_RR:
                this.currentStrategy = new RR();
                break;
            case SchedulerService.ALGO_PREEMPTIVE_PRIORITY:
                this.currentStrategy = new PreemptivePriority();
                break;
            default:
                this.currentStrategy = new HRRN();
                this.currentAlgorithmType = SchedulerService.ALGO_HRRN;
        }
        System.out.println(">>> 调度算法已切换为: " + currentStrategy.getAlgorithmName());
    }

    @Override
    public int getAlgorithmType() {
        return currentAlgorithmType;
    }

    @Override
    public String getAlgorithmName() {
        return currentStrategy.getAlgorithmName();
    }

    @Override
    public boolean cancelProcess(int pid) {
        if (cancelFromQueue(pendingQueue, pid, false, false, "待提交队列")
                || cancelFromQueue(jobQueue, pid, false, false, "创建队列")
                || cancelFromQueue(readyQueue, pid, true, true, "就绪队列")
                || cancelFromQueue(manualBlockQueue, pid, true, true, "手动阻塞队列")
                || cancelFromQueue(blockQueueA, pid, true, true, "阻塞队列A")
                || cancelFromQueue(blockQueueB, pid, true, true, "阻塞队列B")
                || cancelFromQueue(blockQueueC, pid, true, true, "阻塞队列C")) {
            return true;
        }
        //检查要撤销的队列在不在运行状态
        if (runningProcess != null && runningProcess.getPid() == pid) {
            PCB canceledProcess = runningProcess;
            resourceService.releaseCpu();
            clearRunningSlot();
            terminateProcess(canceledProcess, true, true,
                    "[撤销] PID=" + pid + " 正在运行的进程被撤销，CPU/内存/资源已回收，进入 [终止队列]");
            return true;
        }

        System.out.println("[撤销] 未找到 PID=" + pid + " 的活跃进程");
        return false;
    }

    private boolean cancelFromQueue(List<PCB> queue, int pid, boolean releaseMemory, boolean releaseResources,
                                    String queueName) {
        var it = queue.iterator();
        while (it.hasNext()) {
            PCB pcb = it.next();
            if (pcb.getPid() == pid) {
                it.remove();
                String cleanup = releaseMemory || releaseResources ? "，资源已回收" : "";
                terminateProcess(pcb, releaseMemory, releaseResources,
                        "[撤销] PID=" + pid + " 从" + queueName + "中撤销" + cleanup + "，进入 [终止队列]");
                return true;
            }
        }
        return false;
    }

    //进程死亡处理函数
    private void terminateProcess(PCB pcb, boolean releaseMemory, boolean releaseResources, String message) {
        if (releaseMemory) {
            memoryService.releaseMemory(pcb.getPid());
        }
        if (releaseResources) {
            resourceService.releaseResources(pcb);
        }
        pcb.setState(PCB.DEAD);
        deadQueue.add(pcb);
        System.out.println(message);
    }

    @Override
    public boolean blockProcess() {
        if (runningProcess == null) {
            System.out.println("[阻塞] 当前没有正在运行的进程");
            return false;
        }

        PCB pcb = runningProcess;
        resourceService.releaseCpu();
        pcb.setState(PCB.BLOCK);
        clearRunningSlot();

        manualBlockQueue.add(pcb);
        System.out.println("[阻塞] PID=" + pcb.getPid() + " 被手动阻塞，CPU已释放，资源保留，进入 [手动阻塞队列]");
        return true;
    }

    @Override
    public boolean wakeupProcess(int pid) {
        if (wakeupFromQueue(manualBlockQueue, pid)
                || wakeupFromQueue(blockQueueA, pid)
                || wakeupFromQueue(blockQueueB, pid)
                || wakeupFromQueue(blockQueueC, pid)) {
            return true;
        }
        System.out.println("[唤醒] 未在阻塞队列中找到 PID=" + pid);
        return false;
    }

    private boolean wakeupFromQueue(List<PCB> queue, int pid) {
        var it = queue.iterator();
        while (it.hasNext()) {
            PCB pcb = it.next();
            if (pcb.getPid() == pid) {
                if (!resourceService.allocateResources(pcb)) {
                    System.out.println("[唤醒] PID=" + pid + " 唤醒失败：资源仍不足，继续阻塞");
                    return false;
                }

                it.remove();
                moveToReady(pcb, "[唤醒] PID=" + pid + " 唤醒成功，进入 [就绪队列]");
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean sendMessage(int fromPid, int toPid, String content) {
        String messageText = content == null ? "" : content.trim();
        if (messageText.isEmpty()) {
            System.out.println("[IPC] 发送失败：消息内容为空");
            return false;
        }

        PCB from = findProcessByPid(fromPid);
        PCB to = findProcessByPid(toPid);
        if (!canUseIpc(from) || !canUseIpc(to)) {
            System.out.println("[IPC] 发送失败：发送方或接收方不存在，或进程不在可通信状态");
            return false;
        }

        to.receiveMessage(new PCB.IpcMessage(fromPid, toPid, messageText, currentTime));
        System.out.println("[IPC] PID=" + fromPid + " -> PID=" + toPid + " 消息: " + messageText);
        return true;
    }

    @Override
    public List<PCB.IpcMessage> getMessages(int pid) {
        //查看消息
        PCB pcb = findProcessByPid(pid);
        if (pcb == null) {
            return List.of();
        }

        return new LinkedList<>(pcb.getInbox());
    }

    @Override
    public boolean clearMessages(int pid) {
        //清空收件箱
        PCB pcb = findProcessByPid(pid);
        if (pcb == null) {
            System.out.println("[IPC] 清空失败：未找到 PID=" + pid);
            return false;
        }

        pcb.clearInbox();
        System.out.println("[IPC] PID=" + pid + " 收件箱已清空");
        return true;
    }

    private boolean canUseIpc(PCB pcb) {
        return pcb != null && pcb.getState() != PCB.DEAD && pcb.getState() != PCB.SCHEDULED;
    }

    private PCB findProcessByPid(int pid) {
        if (runningProcess != null && runningProcess.getPid() == pid) {
            return runningProcess;
        }

        for (List<PCB> queue : List.of(jobQueue, readyQueue, manualBlockQueue,
                blockQueueA, blockQueueB, blockQueueC, deadQueue, pendingQueue)) {
            PCB found = findInQueue(queue, pid);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    private PCB findInQueue(List<PCB> queue, int pid) {
        for (PCB pcb : queue) {
            if (pcb.getPid() == pid) {
                return pcb;
            }
        }

        return null;
    }
}
