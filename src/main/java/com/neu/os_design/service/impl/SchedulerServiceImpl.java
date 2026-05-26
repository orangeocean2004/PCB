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

    private SchedulingStrategy currentStrategy = new HRRN();

    private int timeSliceCounter = 0;
    private final int TIME_SLICE = 2; // 时间片长度 2 个时钟周期

    // 队列定义
    private final List<PCB> jobQueue = new LinkedList<>();   // 创建态队列
    private final List<PCB> readyQueue = new LinkedList<>();  // 就绪态队列
    private final List<PCB> blockQueue = new LinkedList<>();  // 阻塞态队列
    private final List<PCB> deadQueue = new LinkedList<>();   // 终止态队列

    private PCB runningProcess = null;    // 当前正在运行的进程

    @Override
    public PCB submitProcess(int totalTime, int priority, int needA, int needB, int needC, int memoryNeed) {
        // 创建进程 PCB
        PCB pcb = new PCB(totalTime, priority, needA, needB, needC, memoryNeed, currentTime);
        pcb.setState(PCB.CREATED);

        // 尝试分配内存
        if (memoryService.allocateMemory(pcb.getPid(), pcb.getMemoryNeed())) {
            // 内存分配成功，加入就绪态队列
            pcb.setState(PCB.READY);
            readyQueue.add(pcb);
            System.out.println("[提交进程] PID=" + pcb.getPid() + " 内存分配成功，进入 [就绪队列]");
        } else {
            // 内存分配失败 加入创建态队列 等待内存资源
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
        // 遍历job队列，尝试分配内存，如果成功就转移到ready队列
        var it = jobQueue.iterator();

        while (it.hasNext()) {
            PCB pcb = it.next();

            if (memoryService.allocateMemory(pcb.getPid(), pcb.getMemoryNeed())) {
                pcb.setAllocatedMemory(pcb.getMemoryNeed());
                pcb.setState(PCB.READY);
                it.remove();    // 从创建队列移除
                readyQueue.add(pcb);    // 加入就绪队列
                System.out.println("[创建队列] PID=" + pcb.getPid() + " 内存分配成功，进入 [就绪队列]");
            }
        }
    }

    private void checkBlockQueueForResources() {
        // 遍历block队列，尝试分配资源，如果成功就转移到ready队列
        var it = blockQueue.iterator();

        while (it.hasNext()) {
            PCB pcb = it.next();

            if (resourceService.allocateResources(pcb)) {
                pcb.setState(PCB.READY);
                it.remove();
                readyQueue.add(pcb);
                System.out.println("[阻塞队列] PID=" + pcb.getPid() + " 资源分配成功，进入 [就绪队列]");
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
                System.err.println("[异常] CPU 被未知实体占用");
            }
        } else {
            readyQueue.remove(bestCandidate);
            bestCandidate.setState(PCB.BLOCK);
            blockQueue.add(bestCandidate);
            System.out.println("[调度] PID=" + bestCandidate.getPid() + " 资源A B C不足，进入 [阻塞队列]");
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
    }

    @Override
    public void setAlgorithmType(int algorithmType) {
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
        }
        System.out.println(">>> 调度算法已切换为: " + currentStrategy.getAlgorithmName());
    }
}
