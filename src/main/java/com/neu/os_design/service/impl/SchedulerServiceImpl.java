package com.neu.os_design.service.impl;

import com.neu.os_design.model.PCB;
import com.neu.os_design.service.MemoryService;
import com.neu.os_design.service.SchedulerService;
import com.neu.os_design.service.SystemResourceService;
import com.neu.os_design.service.strategy.SchedulingStrategy;
import com.neu.os_design.service.strategy.algorithm.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

@Service
public class SchedulerServiceImpl implements SchedulerService {

    @Autowired
    private MemoryService memoryService;

    @Autowired
    private SystemResourceService resourceService;

    private int currentTime = 0;

    // 初始资源配置，供 resetClock() 恢复
    private int initialMemorySize = 1024;
    private int initialA = 10;
    private int initialB = 10;
    private int initialC = 10;

    private SchedulingStrategy currentStrategy = new HRRN();
    private int currentAlgorithmType = SchedulerService.ALGO_HRRN;

    private int timeSliceCounter = 0;
    private final int TIME_SLICE = 3; // 时间片长度 3 个时钟周期

    // 队列定义
    private final List<PCB> jobQueue = new LinkedList<>();   // 创建态队列
    private final List<PCB> readyQueue = new LinkedList<>();  // 就绪态队列
    private final List<PCB> blockQueue = new LinkedList<>();  // 阻塞态队列
    private final List<PCB> deadQueue = new LinkedList<>();   // 终止态队列

    private PCB runningProcess = null;    // 当前正在运行的进程

    private boolean manualDispatchMode = false; // 手动资源分配模式开关

    @Override
    public PCB submitProcess(int totalTime, int priority, int needA, int needB, int needC, int memoryNeed) {
        PCB pcb = new PCB(totalTime, priority, needA, needB, needC, memoryNeed, currentTime);
        pcb.setState(PCB.CREATED);

        if (manualDispatchMode) {
            // 手动模式：进程直接进入创建队列，不自动分配任何资源
            jobQueue.add(pcb);
            System.out.println("[提交进程] PID=" + pcb.getPid() + " [手动模式] 进入 [创建队列]，等待手动分配资源");
            return pcb;
        }

        // 自动模式：尝试分配内存
        if (memoryService.allocateMemory(pcb.getPid(), pcb.getMemoryNeed())) {
            pcb.setAllocatedMemory(pcb.getMemoryNeed());
            pcb.setState(PCB.READY);
            readyQueue.add(pcb);
            System.out.println("[提交进程] PID=" + pcb.getPid() + " 内存分配成功，进入 [就绪队列]");
        } else {
            jobQueue.add(pcb);
            System.out.println("[提交进程] PID=" + pcb.getPid() + " 内存分配失败，进入 [创建队列]");
        }

        return pcb;
    }

    @Override
    public void systemTick() {
        // 全局时间 ++
        currentTime++;
        System.out.println("============== [当前系统时钟: " + currentTime + "] ==============");

        // 1. 更新所有等待队伍
        updateAllWaitTimes();

        // 2. 检查创建队列 有多的内存看看能不能调入就绪队列
        checkJobQueueForMemory();

        // 3. 检查阻塞队列 有多的资源看看能不能调入就绪队列
        checkBlockQueueForResources();

        // 4. cpu上正在运行的进程的时间片 进度 判断 跑完？ 时间片到了？
        manageRunningProcess();

        // 5. cpu空闲 就调度
        scheduleNextProcess();
    }

    private void updateAllWaitTimes() {
        // 遍历就绪队列的进程，更新等待时间和响应比
        for (PCB pcb : readyQueue) {
            pcb.updateWaitingTime(currentTime);
            pcb.calculateResponseRatio();
        }
    }

    private void checkJobQueueForMemory() {
        // 手动模式下不自动分配，由用户手动触发 dispatchResources()
        if (manualDispatchMode) {
            return;
        }

        var it = jobQueue.iterator();

        while (it.hasNext()) {
            PCB pcb = it.next();

            if (memoryService.allocateMemory(pcb.getPid(), pcb.getMemoryNeed())) {
                pcb.setAllocatedMemory(pcb.getMemoryNeed());
                pcb.setState(PCB.READY);
                it.remove();
                readyQueue.add(pcb);
                System.out.println("[创建队列] PID=" + pcb.getPid() + " 内存分配成功，进入 [就绪队列]");
            }
        }
    }

    private void checkBlockQueueForResources() {
        // 遍历block队列，尝试分配内存+ABC资源，如果成功就转移到ready队列
        var it = blockQueue.iterator();

        while (it.hasNext()) {
            PCB pcb = it.next();
            boolean newlyAllocatedMemory = false;

            // 如果进程还没有内存，先尝试分配内存
            if (pcb.getAllocatedMemory() == 0) {
                if (!memoryService.allocateMemory(pcb.getPid(), pcb.getMemoryNeed())) {
                    continue; // 内存不够，继续等
                }
                pcb.setAllocatedMemory(pcb.getMemoryNeed());
                newlyAllocatedMemory = true;
            }

            if (resourceService.allocateResources(pcb)) {
                pcb.setState(PCB.READY);
                it.remove();
                readyQueue.add(pcb);
                System.out.println("[阻塞队列] PID=" + pcb.getPid() + " 资源分配成功，进入 [就绪队列]");
            } else {
                // ABC资源不够，只回滚本轮刚分配的内存，不动blockProcess保留的
                if (newlyAllocatedMemory) {
                    memoryService.releaseMemory(pcb.getPid());
                    pcb.setAllocatedMemory(0);
                }
            }
        }
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
            System.out.println("[完成] PID=" + runningProcess.getPid() + " 进程完成，进入 [终止队列]");

            // 进程完成，回收资源和内存
            resourceService.releaseCpu();
            resourceService.releaseResources(runningProcess);
            memoryService.releaseMemory(runningProcess.getPid());



            // 转移到终止队列
            deadQueue.add(runningProcess);
            runningProcess = null;
            timeSliceCounter = 0;
        } else if (currentStrategy instanceof RR && timeSliceCounter >= TIME_SLICE) {
            System.out.println("[时间片到] PID=" + runningProcess.getPid() + " 时间片到，回到 [就绪队列] 队尾");

            // 时间片到，回收CPU资源，转移回就绪队列
            resourceService.releaseCpu();
            runningProcess.setState(PCB.READY);
            readyQueue.add(runningProcess);
            runningProcess = null;
            timeSliceCounter = 0;
        }
    }

    private void scheduleNextProcess() {
        // 获取下一个要运行的进程，占有CPU互斥锁
        if (runningProcess != null) {
            return;
        }

        if (readyQueue.isEmpty()) {
            return;
        }

        // 根据现有调度算法从就绪队列中选出一个最优的进程
        PCB bestCandidate = currentStrategy.selectNextProcess(readyQueue);

        if (bestCandidate == null) {
            return;
        }

        boolean hasAllNeadedResources = resourceService.allocateResources(bestCandidate);

        if (hasAllNeadedResources) {
            if (resourceService.allocateCpu()) {
                readyQueue.remove(bestCandidate);
                runningProcess = bestCandidate;
                runningProcess.setState(PCB.RUNNING);

                if (runningProcess.getStartTime() == -1) {
                    runningProcess.setStartTime(currentTime);
                }

                System.out.println("[调度][" + currentStrategy.getAlgorithmName() + "] PID=" + runningProcess.getPid() + " 被调度运行" + " 响应比: " + runningProcess.getResponseRatio());
            } else {
                // CPU 被占用 — 回滚已分配的资源，进程放回就绪队列
                resourceService.releaseResources(bestCandidate);
                System.err.println("[异常] CPU 被占用，PID=" + bestCandidate.getPid() + " 资源已回滚，回到就绪队列");
            }
        } else {
            readyQueue.remove(bestCandidate);
            // ABC资源不足，回滚已分配的内存
            if (bestCandidate.getAllocatedMemory() > 0) {
                memoryService.releaseMemory(bestCandidate.getPid());
                bestCandidate.setAllocatedMemory(0);
            }
            bestCandidate.setState(PCB.BLOCK);
            blockQueue.add(bestCandidate);
            System.out.println("[调度] PID=" + bestCandidate.getPid() + " 资源A B C不足，已释放内存，进入 [阻塞队列]");
        }
    }

    @Override
    public List<PCB> getReadyQueue() {
        return readyQueue;
    }

    @Override
    public List<PCB> getBlockQueue() {
        return blockQueue;
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
        jobQueue.clear();
        readyQueue.clear();
        blockQueue.clear();
        deadQueue.clear();
        memoryService.resetMemory(initialMemorySize);
        resourceService.resetResources(initialA, initialB, initialC);
        com.neu.os_design.model.PCB.resetPidSeq();
        manualDispatchMode = false;
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
    public void setResourceDispatchMode(boolean manual) {
        boolean wasManual = this.manualDispatchMode;
        this.manualDispatchMode = manual;
        if (manual && !wasManual) {
            System.out.println(">>> 资源分配模式切换为: [手动模式] — 进程进入创建队列，需手动触发分配");
        } else if (!manual && wasManual) {
            System.out.println(">>> 资源分配模式切换为: [自动模式] — 进程提交时自动分配资源");
            // 切换到自动模式时，立刻尝试把创建队列里的进程分配了
            checkJobQueueForMemory();
        }
    }

    @Override
    public boolean isManualDispatchMode() {
        return manualDispatchMode;
    }

    @Override
    public void dispatchResources() {
        if (jobQueue.isEmpty()) {
            System.out.println("[手动分配] 创建队列为空，没有进程需要分配资源");
            return;
        }

        if (!manualDispatchMode) {
            System.out.println("[手动分配] 当前为自动模式，无需手动分配。请先切换到手动模式。");
            return;
        }

        System.out.println("[手动分配] 开始遍历创建队列，共 " + jobQueue.size() + " 个进程...");

        int successCount = 0;
        int failCount = 0;

        var it = jobQueue.iterator();
        while (it.hasNext()) {
            PCB pcb = it.next();

            // 先分配内存
            boolean memOk = memoryService.allocateMemory(pcb.getPid(), pcb.getMemoryNeed());
            if (!memOk) {
                failCount++;
                System.out.println("[手动分配] PID=" + pcb.getPid() + " 内存不足(" + pcb.getMemoryNeed() + "KB)，跳过");
                continue;
            }
            pcb.setAllocatedMemory(pcb.getMemoryNeed());

            // 再分配ABC资源
            boolean resOk = resourceService.allocateResources(pcb);
            if (!resOk) {
                // 回滚内存
                memoryService.releaseMemory(pcb.getPid());
                pcb.setAllocatedMemory(0);
                failCount++;
                System.out.println("[手动分配] PID=" + pcb.getPid() + " ABC资源不足，内存已回滚，跳过");
                continue;
            }

            // 全部成功 → 转入就绪队列
            pcb.setState(PCB.READY);
            it.remove();
            readyQueue.add(pcb);
            successCount++;
            System.out.println("[手动分配] PID=" + pcb.getPid() + " 内存+" + pcb.getMemoryNeed()
                    + "KB + ABC(" + pcb.getNeedA() + "," + pcb.getNeedB() + "," + pcb.getNeedC()
                    + ") 分配成功 → [就绪队列]");
        }

        System.out.println("[手动分配] 完成：成功=" + successCount + "，失败=" + failCount
                + "，创建队列剩余=" + jobQueue.size());
    }

    @Override
    public boolean cancelProcess(int pid) {
        // 1. 检查是否在创建队列
        var jobIt = jobQueue.iterator();
        while (jobIt.hasNext()) {
            PCB pcb = jobIt.next();
            if (pcb.getPid() == pid) {
                jobIt.remove();
                pcb.setState(PCB.DEAD);
                deadQueue.add(pcb);
                System.out.println("[撤销] PID=" + pid + " 从创建队列中撤销，进入 [终止队列]");
                return true;
            }
        }

        // 2. 检查是否在就绪队列
        var readyIt = readyQueue.iterator();
        while (readyIt.hasNext()) {
            PCB pcb = readyIt.next();
            if (pcb.getPid() == pid) {
                readyIt.remove();
                memoryService.releaseMemory(pid);
                resourceService.releaseResources(pcb);
                pcb.setState(PCB.DEAD);
                deadQueue.add(pcb);
                System.out.println("[撤销] PID=" + pid + " 从就绪队列中撤销，资源已回收，进入 [终止队列]");
                return true;
            }
        }

        // 3. 检查是否在阻塞队列
        var blockIt = blockQueue.iterator();
        while (blockIt.hasNext()) {
            PCB pcb = blockIt.next();
            if (pcb.getPid() == pid) {
                blockIt.remove();
                memoryService.releaseMemory(pid);
                resourceService.releaseResources(pcb);
                pcb.setState(PCB.DEAD);
                deadQueue.add(pcb);
                System.out.println("[撤销] PID=" + pid + " 从阻塞队列中撤销，资源已回收，进入 [终止队列]");
                return true;
            }
        }

        // 4. 检查是否是当前运行的进程
        if (runningProcess != null && runningProcess.getPid() == pid) {
            resourceService.releaseCpu();
            memoryService.releaseMemory(pid);
            resourceService.releaseResources(runningProcess);
            runningProcess.setState(PCB.DEAD);
            deadQueue.add(runningProcess);
            System.out.println("[撤销] PID=" + pid + " 正在运行的进程被撤销，CPU/内存/资源已回收，进入 [终止队列]");
            runningProcess = null;
            timeSliceCounter = 0;
            return true;
        }

        System.out.println("[撤销] 未找到 PID=" + pid + " 的活跃进程");
        return false;
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
        runningProcess = null;
        timeSliceCounter = 0;
        blockQueue.add(pcb);
        System.out.println("[阻塞] PID=" + pcb.getPid() + " 被阻塞，CPU已释放，资源保留，进入 [阻塞队列]");
        return true;
    }

    @Override
    public boolean wakeupProcess(int pid) {
        var it = blockQueue.iterator();
        while (it.hasNext()) {
            PCB pcb = it.next();
            if (pcb.getPid() == pid) {
                boolean hasResources = pcb.getAllocatedMemory() > 0
                        || pcb.getGetA() > 0
                        || pcb.getGetB() > 0
                        || pcb.getGetC() > 0;

                if (!hasResources) {
                    System.out.println("[唤醒] PID=" + pid + " 唤醒失败：未持有资源，需先手动分配资源");
                    return false;
                }

                it.remove();
                pcb.setState(PCB.READY);
                readyQueue.add(pcb);
                System.out.println("[唤醒] PID=" + pid + " 唤醒成功，进入 [就绪队列]");
                return true;
            }
        }

        System.out.println("[唤醒] 未在阻塞队列中找到 PID=" + pid);
        return false;
    }
}
