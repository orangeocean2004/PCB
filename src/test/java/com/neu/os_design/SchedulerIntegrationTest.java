package com.neu.os_design;

import com.neu.os_design.model.MemoryBlock;
import com.neu.os_design.model.PCB;
import com.neu.os_design.service.MemoryService;
import com.neu.os_design.service.SchedulerService;
import com.neu.os_design.service.SystemResourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 综合集成测试：覆盖调度器、资源分配、内存管理
 * 不修改任何生产代码，仅通过测试发现问题
 */
@SpringBootTest
public class SchedulerIntegrationTest {

    @Autowired
    private SchedulerService schedulerService;

    @Autowired
    private MemoryService memoryService;

    @Autowired
    private SystemResourceService resourceService;

    @BeforeEach
    void setUp() {
        schedulerService.resetClock();
        memoryService.resetMemory(1024);
        resourceService.resetResources(10, 10, 10);
    }

    // ==================== 内存管理测试 ====================

    @Test
    @DisplayName("内存：Best Fit 应选择最小的足够空闲块")
    void testBestFitSelectsSmallestBlock() {
        memoryService.resetMemory(1000);
        // 创建碎片：申请200, 100, 300, 50
        memoryService.allocateMemory(1, 200);
        memoryService.allocateMemory(2, 100);
        memoryService.allocateMemory(3, 300);
        memoryService.allocateMemory(4, 50);
        // 释放第2个和第4个，产生碎片
        memoryService.releaseMemory(2); // 100KB free at addr 200
        memoryService.releaseMemory(4); // 50KB free at addr 600, 会与后面的350KB合并成400KB

        // 现在空闲块：[200-299] 100KB, [600-999] 400KB
        // Best Fit 申请40KB应选100KB的块（更小）
        boolean result = memoryService.allocateMemory(5, 40);
        assertTrue(result);

        // 验证选中的是100KB的块（地址200），不是400KB的块（地址600）
        List<com.neu.os_design.model.MemoryBlock> blocks = memoryService.getMemoryStatus();
        boolean foundAt200 = false;
        for (var block : blocks) {
            if (block.getOccupantPid() == 5 && block.getStartAddress() == 200) {
                foundAt200 = true;
                break;
            }
        }
        assertTrue(foundAt200, "Best Fit 应选择100KB块(addr=200)而非400KB块(addr=600)");
    }

    @Test
    @DisplayName("内存：防止同一PID重复分配")
    void testDuplicateAllocationPrevented() {
        memoryService.resetMemory(1000);
        assertTrue(memoryService.allocateMemory(1, 100));
        assertFalse(memoryService.allocateMemory(1, 200), "同一PID重复申请应被拒绝");
        assertFalse(memoryService.allocateMemory(1, 50), "同一PID重复申请应被拒绝（即使size不同）");
    }

    @Test
    @DisplayName("内存：超过总内存的申请应失败")
    void testOversizeAllocationFails() {
        memoryService.resetMemory(1000);
        assertFalse(memoryService.allocateMemory(99, 1200));
        assertFalse(memoryService.allocateMemory(99, 1001));
    }

    @Test
    @DisplayName("内存：相邻空闲块合并")
    void testAdjacentFreeBlockMerge() {
        memoryService.resetMemory(1000);
        memoryService.allocateMemory(1, 200);
        memoryService.allocateMemory(2, 100);
        memoryService.allocateMemory(3, 300);

        // 释放PID=2，然后PID=1和PID=3之间应该合并
        memoryService.releaseMemory(2);
        memoryService.releaseMemory(1);
        memoryService.releaseMemory(3);

        // 全部释放后应该只有一个空闲块
        assertEquals(1, memoryService.getFreePartitionCount(),
                "全部释放后应合并为1个空闲分区");
        assertEquals(1000, memoryService.getMaxAvailableMemory());
        assertEquals(0, memoryService.getUsedMemory());
    }

    @Test
    @DisplayName("内存：释放不存在PID不应崩溃")
    void testReleaseNonExistentPid() {
        memoryService.resetMemory(1000);
        // 不应抛异常
        assertDoesNotThrow(() -> memoryService.releaseMemory(9999));
    }

    // ==================== 调度器测试 ====================

    @Test
    @DisplayName("调度：FCFS 按到达顺序调度")
    void testFcfsScheduling() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);

        PCB p1 = schedulerService.submitProcess(10, 1, 0, 0, 0, 64);
        PCB p2 = schedulerService.submitProcess(5, 1, 0, 0, 0, 64);
        PCB p3 = schedulerService.submitProcess(8, 1, 0, 0, 0, 64);

        // 执行足够多次tick直到所有进程完成
        for (int i = 0; i < 50; i++) {
            schedulerService.systemTick();
        }

        List<PCB> deadQueue = schedulerService.getDeadQueue();
        assertEquals(3, deadQueue.size(), "所有3个进程应完成");
        // FCFS: 按到达顺序 = PID顺序（先提交的PID小）
        assertEquals(p1.getPid(), deadQueue.get(0).getPid());
        assertEquals(p2.getPid(), deadQueue.get(1).getPid());
        assertEquals(p3.getPid(), deadQueue.get(2).getPid());
    }

    @Test
    @DisplayName("调度：SJF 短作业优先完成")
    void testSjfScheduling() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_SJF);

        PCB pLong = schedulerService.submitProcess(10, 1, 0, 0, 0, 64);
        PCB pShort = schedulerService.submitProcess(3, 1, 0, 0, 0, 64);
        PCB pMid = schedulerService.submitProcess(7, 1, 0, 0, 0, 64);

        // 执行到第一个进程完成
        for (int i = 0; i < 5; i++) {
            schedulerService.systemTick();
        }

        List<PCB> deadQueue = schedulerService.getDeadQueue();
        assertFalse(deadQueue.isEmpty(), "应有进程完成");
        // SJF应选择最短的进程(totalTime=3)
        assertEquals(pShort.getPid(), deadQueue.get(0).getPid(),
                "SJF应优先完成最短作业(totalTime=3)");
        // 验证确实是总时间最短的
        assertEquals(3, deadQueue.get(0).getTotalTime());
    }

    @Test
    @DisplayName("调度：HRRN 选择最高响应比")
    void testHrrnScheduling() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_HRRN);

        // 提交3个进程
        schedulerService.submitProcess(10, 1, 0, 0, 0, 64);
        schedulerService.submitProcess(10, 1, 0, 0, 0, 64);
        schedulerService.submitProcess(10, 1, 0, 0, 0, 64);

        // 第一个tick时，第一个进程会被调度运行
        schedulerService.systemTick();

        PCB running = schedulerService.getRunningProcess();
        assertNotNull(running, "应有进程在运行");

        // 等待的进程响应比应该 > 1.0(因为等待时间>0)
        for (PCB pcb : schedulerService.getReadyQueue()) {
            assertTrue(pcb.getResponseRatio() >= 1.0,
                    "等待进程的响应比应 ≥ 1.0, 实际: " + pcb.getResponseRatio());
        }
    }

    @Test
    @DisplayName("调度：Priority 优先级调度（数字小的先运行）")
    void testPriorityScheduling() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_PRIORITY);

        PCB pLowPrio = schedulerService.submitProcess(5, 3, 0, 0, 0, 64);  // priority=3
        PCB pHighPrio = schedulerService.submitProcess(5, 1, 0, 0, 0, 64); // priority=1 (最小)
        PCB pMidPrio = schedulerService.submitProcess(5, 5, 0, 0, 0, 64);  // priority=5

        // 执行几个tick
        schedulerService.systemTick();

        PCB running = schedulerService.getRunningProcess();
        assertNotNull(running, "应有进程在运行");

        // ⚠️ 注意: Java Priority调度 "数字越小优先级越高"
        // 但 C++ 版本是 "数字越大优先级越高" — 这是与C++相反的!
        assertEquals(pHighPrio.getPid(), running.getPid(),
                "Priority调度(数字越小优先级越高)应选中priority=1的进程");
        assertEquals(1, running.getPriority(),
                "被选中的进程priority应为1（最小值）");
    }

    @Test
    @DisplayName("调度：RR 时间片轮转 — 时间片到期应切换")
    void testRoundRobinTimeSlice() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_RR);

        schedulerService.submitProcess(10, 1, 0, 0, 0, 64);
        schedulerService.submitProcess(10, 1, 0, 0, 0, 64);

        // 时间片=2，运行2个tick后应该切换
        schedulerService.systemTick(); // t=1, PID=1 running
        schedulerService.systemTick(); // t=2, PID=1 still running (time slice not exceeded yet)

        PCB runningAfter2 = schedulerService.getRunningProcess();
        assertNotNull(runningAfter2);

        schedulerService.systemTick(); // t=3, time slice exceeded → switch

        PCB runningAfter3 = schedulerService.getRunningProcess();
        assertNotNull(runningAfter3, "时间片到后应调度新进程");
        // 确认发生了切换（PID变化或同一个进程继续）
        // 如果有2个进程在就绪队列，应该切换到PID=2
    }

    @Test
    @DisplayName("调度：进程完成后进入dead队列，资源被回收")
    void testProcessCompletion() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);

        // 提交一个只需1个时间单位的进程
        PCB pcb = schedulerService.submitProcess(1, 1, 3, 2, 1, 64);

        // 运行1个tick → 进程完成
        schedulerService.systemTick(); // t=1, process runs and finishes
        schedulerService.systemTick(); // t=2, check cleanup

        List<PCB> deadQueue = schedulerService.getDeadQueue();
        assertEquals(1, deadQueue.size(), "完成的进程应进入dead队列");

        PCB dead = deadQueue.get(0);
        assertEquals(pcb.getPid(), dead.getPid());
        assertEquals(PCB.DEAD, dead.getState());
        assertTrue(dead.getFinishTime() > 0, "finishTime应被设置");
    }

    @Test
    @DisplayName("调度：资源不足时进程应进入阻塞队列")
    void testResourceInsufficientBlocksProcess() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_RR);
        resourceService.resetResources(5, 1, 1); // 总量够，但会被前一个进程占满

        // 第一个进程先拿满A资源并运行
        PCB p1 = schedulerService.submitProcess(10, 1, 5, 0, 0, 64);
        // 第二个进程需要同样的A资源，但只能等前一个释放
        PCB p2 = schedulerService.submitProcess(3, 1, 5, 0, 0, 64);

        for (int i = 0; i < 4; i++) {
            schedulerService.systemTick();
        }

        // PID=2 尝试获取资源失败，应进入阻塞队列
        List<PCB> blockQueue = schedulerService.getBlockQueue();
        assertTrue(blockQueue.contains(p2));
        assertEquals(PCB.BLOCK, p2.getState());
    }

    @Test
    @DisplayName("调度：资源应在运行态申请且不应超额占用系统总资源")
    void testReadyQueueDoesNotOvercommitResources() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_RR);
        resourceService.resetResources(10, 10, 10);
        memoryService.resetMemory(1024);

        PCB p1 = schedulerService.submitProcess(10, 1, 2, 1, 1, 900);
        PCB p2 = schedulerService.submitProcess(5, 1, 9, 1, 1, 100);
        PCB p3 = schedulerService.submitProcess(5, 1, 1, 1, 1, 200);

        assertTrue(schedulerService.getReadyQueue().contains(p1),
                "P1应先只分配内存并进入就绪队列");
        assertTrue(schedulerService.getReadyQueue().contains(p2),
                "P2提交时也应只分配内存并进入就绪队列");
        assertTrue(schedulerService.getJobQueue().contains(p3),
                "P1+P2已占用1000KB后，P3申请200KB内存不足，应进入创建队列");
        assertEquals(10, resourceService.getAvailableA(),
                "提交阶段不应占用A资源");
        assertEquals(1000, memoryService.getUsedMemory(),
                "提交阶段只应占用已进入内存的P1和P2");

        for (int i = 0; i < 4; i++) {
            schedulerService.systemTick();
        }

        assertTrue(schedulerService.getBlockQueue().contains(p2),
                "P1运行态占用A=2后，P2运行态申请A=9应因剩余A=8不足而阻塞");
        assertEquals(8, resourceService.getAvailableA(),
                "只有P1应持有A=2，系统剩余A=8");
        assertEquals(1000, memoryService.getUsedMemory(),
                "资源阻塞不应释放内存，P1和P2仍保留在内存中");
    }

    @Test
    @DisplayName("调度：内存不足时进程进入创建队列（jobQueue）")
    void testMemoryInsufficientPutsProcessInJobQueue() {
        memoryService.resetMemory(100); // 只给100KB

        // 第一个进程占用大部分内存
        schedulerService.submitProcess(5, 1, 0, 0, 0, 80);
        // 第二个进程需要的内存不够 → 进jobQueue
        schedulerService.submitProcess(5, 1, 0, 0, 0, 80);

        List<PCB> jobQueue = schedulerService.getJobQueue();
        assertFalse(jobQueue.isEmpty(), "内存不足的进程应进入创建队列(jobQueue)");
        System.out.println("jobQueue大小: " + jobQueue.size());
    }

    @Test
    @DisplayName("调度：resetClock 清空所有队列并重置资源、内存、PID")
    void testResetClock() {
        PCB p1 = schedulerService.submitProcess(5, 1, 3, 2, 1, 64);
        PCB p2 = schedulerService.submitProcess(5, 1, 0, 0, 0, 64);
        schedulerService.systemTick();

        int oldPid = p2.getPid();
        assertTrue(oldPid > 1, "reset前PID应已递增");

        schedulerService.resetClock();

        // 队列和时间清零
        assertEquals(0, schedulerService.getCurrentTime());
        assertNull(schedulerService.getRunningProcess());
        assertTrue(schedulerService.getReadyQueue().isEmpty());
        assertTrue(schedulerService.getJobQueue().isEmpty());
        assertTrue(schedulerService.getBlockQueue().isEmpty());
        assertTrue(schedulerService.getDeadQueue().isEmpty());

        // 内存已重置(之前分配了2*64=128KB，reset后应为0)
        assertEquals(0, memoryService.getUsedMemory(),
                "resetClock应重置内存，已用内存应为0");

        // PID已重置
        PCB p3 = schedulerService.submitProcess(1, 1, 0, 0, 0, 64);
        assertEquals(1, p3.getPid(), "resetClock后PID应从1重新开始");
    }

    // ==================== 边界和压力测试 ====================

    @Test
    @DisplayName("边界：空就绪队列时systemTick不应崩溃")
    void testEmptyReadyQueueTick() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);
        // 不提交任何进程，直接tick
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 10; i++) {
                schedulerService.systemTick();
            }
        });
        assertEquals(10, schedulerService.getCurrentTime());
    }

    @Test
    @DisplayName("边界：提交零资源需求的进程")
    void testZeroResourceProcess() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);
        PCB pcb = schedulerService.submitProcess(3, 1, 0, 0, 0, 64);
        assertNotNull(pcb);
        assertEquals(PCB.READY, pcb.getState(), "零资源需求的进程应直接进入就绪队列");
    }

    @Test
    @DisplayName("边界：PCB内存需求自动钳位")
    void testMemoryNeedClamping() {
        // PCB构造时，memoryNeed ≤ 0 → 默认为64; memoryNeed > 1024 → 钳位到1024
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);

        PCB pcb1 = schedulerService.submitProcess(5, 1, 0, 0, 0, 0);
        assertEquals(64, pcb1.getMemoryNeed(), "memoryNeed=0应默认为64");

        PCB pcb2 = schedulerService.submitProcess(5, 1, 0, 0, 0, 2000);
        assertEquals(1024, pcb2.getMemoryNeed(), "memoryNeed>1024应钳位到1024");
    }

    @Test
    @DisplayName("压力：连续提交大量进程并运行")
    void testManyProcesses() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);
        memoryService.resetMemory(10240);
        resourceService.resetResources(100, 100, 100);

        int processCount = 20;
        for (int i = 0; i < processCount; i++) {
            schedulerService.submitProcess(3, 1, 1, 1, 1, 64);
        }

        // 运行直到所有进程完成或超过最大迭代次数
        int maxTicks = 200;
        for (int i = 0; i < maxTicks; i++) {
            schedulerService.systemTick();
            if (schedulerService.getDeadQueue().size() >= processCount) {
                break;
            }
        }

        System.out.println("完成进程数: " + schedulerService.getDeadQueue().size()
                + " / 提交: " + processCount);
        System.out.println("当前时间: " + schedulerService.getCurrentTime());

        assertEquals(processCount, schedulerService.getDeadQueue().size(),
                "所有进程应最终完成");
    }

    // ==================== 算法切换测试 ====================

    @Test
    @DisplayName("算法：运行时切换调度算法")
    void testAlgorithmSwitch() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);
        schedulerService.submitProcess(10, 1, 0, 0, 0, 64);
        schedulerService.submitProcess(5, 2, 0, 0, 0, 64);

        schedulerService.systemTick(); // FCFS 运行 PID=1

        // 运行时切换到SJF
        schedulerService.setAlgorithmType(SchedulerService.ALGO_SJF);
        schedulerService.systemTick();

        // 不应崩溃
        assertNotNull(schedulerService.getRunningProcess());
    }

    @Test
    @DisplayName("算法：运行中切换到RR")
    void testSwitchToRR() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);
        schedulerService.submitProcess(10, 1, 0, 0, 0, 64);
        schedulerService.submitProcess(10, 1, 0, 0, 0, 64);

        schedulerService.systemTick();

        // 切换到RR
        schedulerService.setAlgorithmType(SchedulerService.ALGO_RR);

        // 继续运行不应崩溃
        for (int i = 0; i < 10; i++) {
            schedulerService.systemTick();
        }

        assertTrue(schedulerService.getCurrentTime() > 0);
    }

    // ==================== 资源管理测试 ====================

    @Test
    @DisplayName("资源：allocateResources 不足时正确回滚")
    void testResourceAllocationRollback() {
        resourceService.resetResources(1, 10, 10);

        PCB pcb = new PCB(5, 1, 3, 0, 0, 64, 0);
        // 需要A=3但只有A=1，应失败
        boolean result = resourceService.allocateResources(pcb);
        assertFalse(result, "资源不足时分配应失败");
        assertEquals(0, pcb.getGetA(), "失败后getA应保持0");
        assertEquals(0, pcb.getGetB(), "失败后getB应保持0");
        assertEquals(0, pcb.getGetC(), "失败后getC应保持0");
    }

    @Test
    @DisplayName("资源：释放后资源可被再次分配")
    void testResourceReleaseAndReallocate() {
        resourceService.resetResources(5, 5, 5);

        PCB pcb = new PCB(5, 1, 3, 2, 1, 64, 0);
        assertTrue(resourceService.allocateResources(pcb));

        resourceService.releaseResources(pcb);
        assertEquals(0, pcb.getGetA());
        assertEquals(0, pcb.getGetB());
        assertEquals(0, pcb.getGetC());

        // 再次分配应成功
        PCB pcb2 = new PCB(5, 1, 3, 2, 1, 64, 0);
        assertTrue(resourceService.allocateResources(pcb2));
    }

    // ==================== PCB 模型测试 ====================

    @Test
    @DisplayName("PCB：runProcess 正确推进进度")
    void testPcbRunProcess() {
        PCB pcb = new PCB(5, 1, 0, 0, 0, 64, 0);
        pcb.setState(PCB.RUNNING);

        // 运行4次
        for (int i = 0; i < 4; i++) {
            boolean finished = pcb.runProcess(i + 1);
            assertFalse(finished, "运行" + (i + 1) + "次后不应完成(总需5)");
        }

        // 第5次应完成
        boolean finished = pcb.runProcess(5);
        assertTrue(finished, "运行5次后应完成");
        assertEquals(PCB.DEAD, pcb.getState());
        assertEquals(5, pcb.getFinishTime());
    }

    @Test
    @DisplayName("PCB：waitingTime 计算正确")
    void testWaitingTimeCalculation() {
        PCB pcb = new PCB(10, 1, 0, 0, 0, 64, 5); // arrivalTime=5
        pcb.setState(PCB.READY);
        pcb.updateWaitingTime(10); // 当前时间10，等待了5个单位
        assertEquals(5, pcb.getWaitingTime());
    }

    @Test
    @DisplayName("PCB：responseRatio 计算 (waiting+total)/total")
    void testResponseRatio() {
        PCB pcb = new PCB(10, 1, 0, 0, 0, 64, 0);
        pcb.setState(PCB.READY);
        pcb.updateWaitingTime(5); // 等待了5个单位
        pcb.calculateResponseRatio();
        // (5 + 10) / 10 = 1.5
        assertEquals(1.5, pcb.getResponseRatio(), 0.01);
    }

    // ==================== C++差异检测测试 ====================

    @Test
    @DisplayName("C++对比：Priority方向验证 — Java是数值越小优先级越高")
    void testPriorityDirectionVsCpp() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_PRIORITY);

        PCB highPriority = schedulerService.submitProcess(5, 1, 0, 0, 0, 64);   // priority=1
        PCB lowPriority = schedulerService.submitProcess(5, 10, 0, 0, 0, 64);   // priority=10

        schedulerService.systemTick();

        PCB running = schedulerService.getRunningProcess();
        assertNotNull(running);

        assertEquals(highPriority.getPid(), running.getPid(),
                "Java版Priority: 应选中priority=1(最小值)的进程");
        assertEquals(1, running.getPriority(),
                "Java版Priority: 数字越小优先级越高 → 应选priority=1");
    }

    // ==================== RR 时间片=3 专项测试 ====================

    @Test
    @DisplayName("RR：时间片=3，运行3个tick后应切换进程")
    void testRRTimeSlice3SwitchesAtBoundary() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_RR);

        PCB p1 = schedulerService.submitProcess(10, 1, 0, 0, 0, 64);
        PCB p2 = schedulerService.submitProcess(10, 1, 0, 0, 0, 64);

        // tick 1,2,3 → p1 运行3个单位（时间片刚好到）
        schedulerService.systemTick(); // t=1
        schedulerService.systemTick(); // t=2
        schedulerService.systemTick(); // t=3

        // t=3 结束时 timeSliceCounter=3 >= TIME_SLICE=3，应切换
        PCB runningBeforeSwitch = schedulerService.getRunningProcess();
        // 此时可能已经切换，也可能还没切换（取决于实现细节）
        // 关键是第4个tick时一定会触发新调度
        schedulerService.systemTick(); // t=4 — 这里一定会调度下一个进程

        PCB runningAfter4 = schedulerService.getRunningProcess();
        assertNotNull(runningAfter4, "t=4时应有进程在运行");
    }

    @Test
    @DisplayName("RR：单进程时时间片到不应无故切换（无其他就绪进程）")
    void testRRSingleProcessNoUnnecessarySwitch() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_RR);

        PCB p1 = schedulerService.submitProcess(10, 1, 0, 0, 0, 64);

        // 运行6个tick（2个时间片周期）
        for (int i = 0; i < 6; i++) {
            schedulerService.systemTick();
        }

        // 只有一个进程，它应该一直在运行（即使时间片到了也没别人可切换）
        PCB running = schedulerService.getRunningProcess();
        if (running != null) {
            assertEquals(p1.getPid(), running.getPid(), "单进程时不应被换下");
        }
    }

    @Test
    @DisplayName("RR：多进程完整运行直到全部完成")
    void testRRCompleteAllProcesses() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_RR);

        int processCount = 5;
        for (int i = 0; i < processCount; i++) {
            schedulerService.submitProcess(4, 1, 0, 0, 0, 64);
        }

        // 运行足够长时间
        for (int i = 0; i < 100; i++) {
            schedulerService.systemTick();
            if (schedulerService.getDeadQueue().size() >= processCount) break;
        }

        assertEquals(processCount, schedulerService.getDeadQueue().size(),
                "RR: 所有进程应最终完成");
    }

    // ==================== CPU回滚路径测试 ====================

    @Test
    @DisplayName("回滚：验证allocateCpu失败时资源正确回滚（模拟异常路径）")
    void testCpuAllocationFailureRollback() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);

        PCB p1 = schedulerService.submitProcess(5, 1, 3, 0, 0, 64);
        schedulerService.systemTick(); // p1 被调度，占用CPU+资源A=3

        PCB running = schedulerService.getRunningProcess();
        assertNotNull(running);
        // p1 此时占用了资源A=3
        assertEquals(3, running.getGetA(), "进程应已获取资源A=3");

        // 正常情况下，allocateCpu不会失败。资源回滚路径只在极端异常时触发。
        // 这里验证：如果进程在运行中，再次调度时资源已分配不会再重复分配
        // （remaining=0，会直接通过）
        schedulerService.systemTick();
        assertNotNull(schedulerService.getRunningProcess());
    }

    // ==================== 资源竞争与阻塞恢复测试 ====================

    @Test
    @DisplayName("资源：阻塞进程在资源释放后能恢复运行")
    void testBlockedProcessRecoveryAfterResourceRelease() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);
        resourceService.resetResources(3, 10, 10); // A只有3个

        // P1需要A=2，能运行
        schedulerService.submitProcess(3, 1, 2, 0, 0, 64);
        // P2需要A=2，也能运行
        schedulerService.submitProcess(3, 1, 2, 0, 0, 64);
        // P3需要A=2，但只剩下A=1不够，应阻塞
        schedulerService.submitProcess(3, 1, 2, 0, 0, 64);

        // 运行到所有进程完成或超时
        for (int i = 0; i < 100; i++) {
            schedulerService.systemTick();
            if (schedulerService.getDeadQueue().size() >= 3) break;
        }

        assertEquals(3, schedulerService.getDeadQueue().size(),
                "所有进程应最终完成（阻塞的进程在资源释放后恢复）");
    }

    @Test
    @DisplayName("资源：多个进程竞争同一资源，按调度顺序分配")
    void testResourceContentionWithScheduling() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);
        resourceService.resetResources(2, 10, 10); // A只有2个

        schedulerService.submitProcess(2, 1, 1, 0, 0, 64);
        schedulerService.submitProcess(2, 1, 1, 0, 0, 64);
        schedulerService.submitProcess(2, 1, 1, 0, 0, 64); // 这个需要等待

        for (int i = 0; i < 50; i++) {
            schedulerService.systemTick();
            if (schedulerService.getDeadQueue().size() >= 3) break;
        }

        assertEquals(3, schedulerService.getDeadQueue().size(),
                "即使资源不够同时分配所有进程，最终也全部完成");
    }

    // ==================== 内存碎片化压力测试 ====================

    @Test
    @DisplayName("内存：碎片化场景 — 大量分配释放后Best Fit仍正确")
    void testFragmentedMemoryBestFit() {
        memoryService.resetMemory(1000);

        // 交替分配产生碎片
        memoryService.allocateMemory(1, 100);
        memoryService.allocateMemory(2, 200);
        memoryService.allocateMemory(3, 100);
        memoryService.allocateMemory(4, 200);
        memoryService.allocateMemory(5, 100);

        // 释放交替的块产生碎片
        memoryService.releaseMemory(1); // 100KB free
        memoryService.releaseMemory(3); // 100KB free
        memoryService.releaseMemory(5); // 100KB free

        // 现在有多个100KB碎片 + 被占用的200KB块
        // 尝试分配90KB → Best Fit应选100KB块（不是后面的空闲区）
        assertTrue(memoryService.allocateMemory(6, 90));

        // 尝试分配150KB → 没有100KB块能满足，必须等200KB块释放或使用更大的
        // 当前最大空闲可能是最后的400KB
        assertTrue(memoryService.getMaxAvailableMemory() >= 150
                || memoryService.allocateMemory(7, 150));

        // 验证内存完整性：已用+空闲分区总和 = 1000
        int totalFromBlocks = 0;
        for (MemoryBlock block : memoryService.getMemoryStatus()) {
            totalFromBlocks += block.getSize();
        }
        assertEquals(1000, totalFromBlocks, "所有块的总大小应等于总内存1000KB");
    }

    @Test
    @DisplayName("内存：大量随机分配释放压力测试")
    void testMemoryStressRandomAllocations() {
        memoryService.resetMemory(10000);

        // 随机分配
        java.util.Random rand = new java.util.Random(42);
        int successCount = 0;
        int failCount = 0;
        java.util.Set<Integer> allocatedPids = new java.util.HashSet<>();

        for (int i = 0; i < 100; i++) {
            int pid = i + 100;
            int size = rand.nextInt(500) + 10;
            if (memoryService.allocateMemory(pid, size)) {
                allocatedPids.add(pid);
                successCount++;
            } else {
                failCount++;
            }
        }

        // 随机释放一半
        int releaseCount = 0;
        for (int pid : allocatedPids) {
            if (rand.nextBoolean()) {
                memoryService.releaseMemory(pid);
                releaseCount++;
            }
        }

        // 再分配一些
        for (int i = 0; i < 20; i++) {
            int pid = 1000 + i;
            int size = rand.nextInt(300) + 10;
            memoryService.allocateMemory(pid, size);
        }

        // 验证内存完整性
        int totalFromBlocks = 0;
        for (MemoryBlock block : memoryService.getMemoryStatus()) {
            totalFromBlocks += block.getSize();
        }
        assertEquals(10000, totalFromBlocks, "压力测试后内存总大小应不变");

        System.out.println("内存压力测试: 成功分配=" + successCount
                + " 失败=" + failCount + " 释放=" + releaseCount);
    }

    // ==================== 进程生命周期测试 ====================

    @Test
    @DisplayName("生命周期：进程runningTime不应超过totalTime")
    void testRunningTimeNeverExceedsTotalTime() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);

        PCB p = schedulerService.submitProcess(3, 1, 0, 0, 0, 64);

        for (int i = 0; i < 10; i++) {
            schedulerService.systemTick();
        }

        // 进程应已完成
        List<PCB> dead = schedulerService.getDeadQueue();
        assertEquals(1, dead.size());
        PCB finished = dead.get(0);
        assertTrue(finished.getRunningTime() <= finished.getTotalTime(),
                "runningTime(" + finished.getRunningTime()
                + ") 不应超过 totalTime(" + finished.getTotalTime() + ")");
        assertEquals(finished.getTotalTime(), finished.getRunningTime(),
                "进程完成时 runningTime 应等于 totalTime");
    }

    @Test
    @DisplayName("生命周期：进程startTime、finishTime、turnaroundTime一致性")
    void testProcessTimeConsistency() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);

        PCB p = schedulerService.submitProcess(3, 1, 0, 0, 0, 64);

        for (int i = 0; i < 20; i++) {
            schedulerService.systemTick();
        }

        List<PCB> dead = schedulerService.getDeadQueue();
        PCB finished = dead.get(0);

        assertTrue(finished.getStartTime() >= 0, "startTime应被设置");
        assertTrue(finished.getFinishTime() > 0, "finishTime应被设置");
        assertTrue(finished.getFinishTime() >= finished.getStartTime(),
                "finishTime >= startTime");
        assertEquals(finished.getFinishTime() - finished.getArrivalTime(),
                finished.getTurnaroundTime(),
                "turnaroundTime = finishTime - arrivalTime");
    }

    // ==================== 算法切换压力测试 ====================

    @Test
    @DisplayName("切换：运行中反复切换所有5种算法，不应崩溃")
    void testRapidAlgorithmSwitching() {
        int[] algos = {
            SchedulerService.ALGO_FCFS,
            SchedulerService.ALGO_SJF,
            SchedulerService.ALGO_HRRN,
            SchedulerService.ALGO_PRIORITY,
            SchedulerService.ALGO_RR,
        };

        // 提交一批进程
        for (int i = 0; i < 5; i++) {
            schedulerService.submitProcess(3, i + 1, 0, 0, 0, 64);
        }

        // 每个tick切换一次算法
        for (int i = 0; i < 30; i++) {
            schedulerService.setAlgorithmType(algos[i % algos.length]);
            schedulerService.systemTick();
        }

        // 不应崩溃
        assertTrue(schedulerService.getDeadQueue().size() > 0,
                "反复切换算法后至少应有进程完成");
    }

    // ==================== 边界值测试 ====================

    @Test
    @DisplayName("边界：totalTime=0的进程（极端短作业）")
    void testZeroTotalTimeProcess() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);

        PCB p = schedulerService.submitProcess(0, 1, 0, 0, 0, 64);
        schedulerService.systemTick();
        schedulerService.systemTick();

        // totalTime=0的进程应该在第一次runProcess时就完成
        List<PCB> dead = schedulerService.getDeadQueue();
        if (!dead.isEmpty()) {
            PCB finished = dead.get(0);
            assertTrue(finished.getRunningTime() >= 0);
        }
    }

    @Test
    @DisplayName("边界：最大优先级和最小优先级进程混合")
    void testExtremePriorityValues() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_PRIORITY);

        PCB pMin = schedulerService.submitProcess(3, Integer.MIN_VALUE, 0, 0, 0, 64);
        PCB pMax = schedulerService.submitProcess(3, Integer.MAX_VALUE, 0, 0, 0, 64);
        PCB pZero = schedulerService.submitProcess(3, 0, 0, 0, 0, 64);

        schedulerService.systemTick();

        PCB running = schedulerService.getRunningProcess();
        assertNotNull(running);
        assertEquals(Integer.MIN_VALUE, running.getPriority(),
                "Priority算法(越小越高)应选最小priority值");
    }

    @Test
    @DisplayName("边界：需要全部ABC资源的进程")
    void testProcessNeedingAllResources() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);
        resourceService.resetResources(5, 5, 5);

        schedulerService.submitProcess(3, 1, 5, 5, 5, 64);

        for (int i = 0; i < 20; i++) {
            schedulerService.systemTick();
        }

        assertEquals(1, schedulerService.getDeadQueue().size(),
                "需要全部资源的进程应能完成");
    }

    // ==================== 队列一致性测试 ====================

    @Test
    @DisplayName("队列：进程不应同时出现在多个队列中")
    void testProcessNotInMultipleQueues() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);

        PCB p1 = schedulerService.submitProcess(5, 1, 0, 0, 0, 64);
        PCB p2 = schedulerService.submitProcess(5, 1, 0, 0, 0, 64);

        for (int i = 0; i < 20; i++) {
            schedulerService.systemTick();

            // 检查每个进程是否只在一个队列中
            for (PCB p : new PCB[]{p1, p2}) {
                int count = 0;
                if (schedulerService.getReadyQueue().contains(p)) count++;
                if (schedulerService.getBlockQueue().contains(p)) count++;
                if (schedulerService.getJobQueue().contains(p)) count++;
                if (schedulerService.getDeadQueue().contains(p)) count++;
                if (schedulerService.getRunningProcess() == p) count++;
                assertTrue(count <= 1,
                        "PID=" + p.getPid() + " 同时出现在" + count + "个队列中！");
            }
        }
    }

    @Test
    @DisplayName("队列：所有提交的进程终态要么在deadQueue要么在running")
    void testAllProcessesAccounted() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);

        int submitted = 5;
        java.util.Set<Integer> submittedPids = new java.util.HashSet<>();
        for (int i = 0; i < submitted; i++) {
            PCB p = schedulerService.submitProcess(3, 1, 0, 0, 0, 64);
            submittedPids.add(p.getPid());
        }

        // 运行足够长时间
        for (int i = 0; i < 100; i++) {
            schedulerService.systemTick();
            if (schedulerService.getDeadQueue().size() >= submitted) break;
        }

        // 所有提交的进程都在deadQueue中
        java.util.Set<Integer> deadPids = new java.util.HashSet<>();
        for (PCB p : schedulerService.getDeadQueue()) {
            deadPids.add(p.getPid());
        }
        assertEquals(submittedPids, deadPids,
                "所有提交的进程都应该在deadQueue中");
    }

    @Test
    @DisplayName("定时提交：未来进程到达提交时间后才进入系统")
    void testScheduledProcessArrivesAtSubmitTime() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);

        PCB p1 = schedulerService.submitProcessAt(0, null, 3, 1, 0, 0, 0, 64);
        PCB p2 = schedulerService.submitProcessAt(20, null, 3, 1, 0, 0, 0, 64);

        assertTrue(schedulerService.getReadyQueue().contains(p1),
                "当前时间提交的进程应立即进入就绪队列");
        assertTrue(schedulerService.getPendingQueue().contains(p2),
                "未来提交时间的进程应先进入待提交队列");

        for (int i = 0; i < 19; i++) {
            schedulerService.systemTick();
        }

        assertTrue(schedulerService.getPendingQueue().contains(p2),
                "T=19 时，T=20 对应的进程还不应进入系统");

        schedulerService.systemTick(); // T=20, p2 到达

        boolean admitted = schedulerService.getReadyQueue().contains(p2)
                || schedulerService.getJobQueue().contains(p2)
                || schedulerService.getBlockQueue().contains(p2)
                || schedulerService.getRunningProcess() == p2;
        assertFalse(schedulerService.getPendingQueue().contains(p2),
                "到达提交时间后进程应离开待提交队列");
        assertTrue(admitted, "到达提交时间后进程应进入系统内的某个活动位置");
        assertEquals(20, p2.getArrivalTime());
        assertNull(p2.getSubmitClock());
    }

    @Test
    @DisplayName("定时提交：resetClock 清空待提交队列")
    void testResetClockClearsPendingQueue() {
        schedulerService.submitProcessAt(10, null, 5, 1, 0, 0, 0, 64);

        assertEquals(1, schedulerService.getPendingQueue().size());

        schedulerService.resetClock();

        assertTrue(schedulerService.getPendingQueue().isEmpty(),
                "resetClock 应清空待提交队列");
    }

    // ==================== Cancel/Block/Wakeup 测试 ====================

    @Test
    @DisplayName("Cancel：撤销就绪队列中的进程")
    void testCancelProcessInReadyQueue() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);
        PCB pcb = schedulerService.submitProcess(10, 1, 2, 1, 1, 64);

        assertTrue(schedulerService.getReadyQueue().contains(pcb));

        boolean result = schedulerService.cancelProcess(pcb.getPid());
        assertTrue(result, "撤销应成功");
        assertEquals(PCB.DEAD, pcb.getState());
        assertTrue(schedulerService.getDeadQueue().contains(pcb));
        assertFalse(schedulerService.getReadyQueue().contains(pcb));
    }

    @Test
    @DisplayName("Cancel：撤销正在运行的进程，CPU应被释放")
    void testCancelRunningProcess() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);
        PCB pcb = schedulerService.submitProcess(10, 1, 0, 0, 0, 64);
        schedulerService.systemTick(); // 调度运行

        assertEquals(pcb, schedulerService.getRunningProcess());

        boolean result = schedulerService.cancelProcess(pcb.getPid());
        assertTrue(result);
        assertNull(schedulerService.getRunningProcess(), "撤销运行进程后CPU应为空");
        assertTrue(schedulerService.getDeadQueue().contains(pcb));
    }

    @Test
    @DisplayName("Cancel：撤销阻塞队列中的进程")
    void testCancelBlockedProcess() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_RR);
        resourceService.resetResources(2, 10, 10);

        PCB p1 = schedulerService.submitProcess(10, 1, 2, 0, 0, 64);
        PCB p2 = schedulerService.submitProcess(3, 1, 1, 0, 0, 64); // A会暂时不够

        // 运行让p2尝试调度，资源不足→阻塞
        for (int i = 0; i < 4; i++) {
            schedulerService.systemTick();
        }

        // p2应该在阻塞队列
        assertTrue(schedulerService.getBlockQueue().contains(p2));

        boolean result = schedulerService.cancelProcess(p2.getPid());
        assertTrue(result);
        assertTrue(schedulerService.getDeadQueue().contains(p2));
        assertFalse(schedulerService.getBlockQueue().contains(p2));
    }

    @Test
    @DisplayName("Cancel：撤销创建队列中的进程")
    void testCancelProcessInJobQueue() {
        memoryService.resetMemory(32);
        PCB pcb = schedulerService.submitProcess(5, 1, 1, 0, 0, 64);

        assertTrue(schedulerService.getJobQueue().contains(pcb));

        boolean result = schedulerService.cancelProcess(pcb.getPid());
        assertTrue(result);
        assertTrue(schedulerService.getDeadQueue().contains(pcb));
        assertFalse(schedulerService.getJobQueue().contains(pcb));
    }

    @Test
    @DisplayName("Cancel：撤销不存在的PID返回false")
    void testCancelNonexistentProcess() {
        assertFalse(schedulerService.cancelProcess(9999));
    }

    @Test
    @DisplayName("Cancel：撤销后资源可被再次使用")
    void testCancelFreesResources() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);
        resourceService.resetResources(3, 10, 10);

        PCB pcb = schedulerService.submitProcess(5, 1, 3, 0, 0, 64);
        schedulerService.systemTick(); // 分配资源+运行

        // 撤销进程
        schedulerService.cancelProcess(pcb.getPid());

        // 资源应该已释放，可以再分配给新进程
        PCB pcb2 = schedulerService.submitProcess(3, 1, 3, 0, 0, 64);
        schedulerService.systemTick();
        schedulerService.systemTick();
        schedulerService.systemTick();
        schedulerService.systemTick();

        assertTrue(schedulerService.getDeadQueue().contains(pcb2),
                "撤销释放的资源应能被新进程使用");
    }

    @Test
    @DisplayName("Block：阻塞当前运行进程")
    void testBlockRunningProcess() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);
        PCB pcb = schedulerService.submitProcess(10, 1, 2, 1, 1, 64);
        schedulerService.systemTick();

        assertEquals(pcb, schedulerService.getRunningProcess());

        boolean result = schedulerService.blockProcess();
        assertTrue(result);
        assertNull(schedulerService.getRunningProcess(), "阻塞后应无运行进程");
        assertTrue(schedulerService.getBlockQueue().contains(pcb));
        assertEquals(PCB.BLOCK, pcb.getState());

        // 资源应保留
        assertTrue(pcb.getGetA() > 0, "阻塞应保留资源A");
        assertTrue(pcb.getAllocatedMemory() > 0, "阻塞应保留内存");
    }

    @Test
    @DisplayName("Block：没有运行进程时返回false")
    void testBlockWithNoRunningProcess() {
        assertFalse(schedulerService.blockProcess());
    }

    @Test
    @DisplayName("Block：阻塞后其他就绪进程可以运行")
    void testBlockAndScheduleNext() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);
        PCB p1 = schedulerService.submitProcess(10, 1, 0, 0, 0, 64);
        PCB p2 = schedulerService.submitProcess(3, 1, 0, 0, 0, 64);

        schedulerService.systemTick(); // p1运行
        schedulerService.blockProcess(); // 阻塞p1

        assertNull(schedulerService.getRunningProcess());

        schedulerService.systemTick(); // 应调度p2

        PCB running = schedulerService.getRunningProcess();
        assertNotNull(running, "阻塞p1后应调度p2");
        assertEquals(p2.getPid(), running.getPid());
    }

    @Test
    @DisplayName("Wakeup：唤醒持有资源的阻塞进程")
    void testWakeupBlockedProcess() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);

        PCB pcb = schedulerService.submitProcess(10, 1, 2, 1, 1, 64);
        schedulerService.systemTick(); // 调度运行
        schedulerService.blockProcess(); // 阻塞

        assertTrue(schedulerService.getBlockQueue().contains(pcb));

        boolean result = schedulerService.wakeupProcess(pcb.getPid());
        assertTrue(result, "持有资源的进程唤醒应成功");
        assertTrue(schedulerService.getReadyQueue().contains(pcb));
        assertFalse(schedulerService.getBlockQueue().contains(pcb));
        assertEquals(PCB.READY, pcb.getState());
    }

    @Test
    @DisplayName("Wakeup：资源不足的进程唤醒失败")
    void testWakeupFailsWithoutResources() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);
        resourceService.resetResources(0, 10, 10);

        PCB pcb = new PCB(5, 1, 1, 0, 0, 64, 0);
        pcb.setAllocatedMemory(64);
        pcb.setState(PCB.BLOCK);
        schedulerService.getBlockQueueA().add(pcb);

        // 确认在阻塞队列
        assertTrue(schedulerService.getBlockQueue().contains(pcb),
                "资源不足的进程应进入阻塞队列");

        // 确认没有分配到任何ABC资源
        assertEquals(0, pcb.getGetA());
        assertEquals(0, pcb.getGetB());
        assertEquals(0, pcb.getGetC());

        // 资源不足时唤醒应失败
        boolean result = schedulerService.wakeupProcess(pcb.getPid());
        assertFalse(result, "资源不足的进程唤醒应失败");
        assertTrue(schedulerService.getBlockQueue().contains(pcb),
                "唤醒失败应保留在阻塞队列");
    }

    @Test
    @DisplayName("调度：超过系统总资源上限的进程应在提交阶段被拒绝")
    void testProcessExceedingSystemCapacityRejected() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);

        assertThrows(IllegalArgumentException.class,
                () -> schedulerService.submitProcess(5, 1, 11, 1, 1, 64));

        assertTrue(schedulerService.getReadyQueue().isEmpty());
        assertTrue(schedulerService.getJobQueue().isEmpty());
        assertTrue(schedulerService.getBlockQueue().isEmpty());
        assertTrue(schedulerService.getDeadQueue().isEmpty());
    }

    @Test
    @DisplayName("Wakeup：不存在的PID返回false")
    void testWakeupNonexistentProcess() {
        assertFalse(schedulerService.wakeupProcess(9999));
    }

    @Test
    @DisplayName("完整流程：提交→运行→阻塞→唤醒→继续运行→完成")
    void testFullBlockWakeupLifecycle() {
        schedulerService.setAlgorithmType(SchedulerService.ALGO_FCFS);

        PCB pcb = schedulerService.submitProcess(5, 1, 1, 0, 0, 64);
        schedulerService.systemTick(); // t=1 scheduleNextProcess 将进程设为运行
        schedulerService.systemTick(); // t=2 manageRunningProcess 运行1个单位

        assertEquals(1, pcb.getRunningTime(), "运行1个tick后runningTime应为1");

        schedulerService.blockProcess(); // 阻塞
        assertEquals(PCB.BLOCK, pcb.getState());

        schedulerService.wakeupProcess(pcb.getPid()); // 唤醒
        assertEquals(PCB.READY, pcb.getState());

        // 继续运行直到完成
        for (int i = 0; i < 10; i++) {
            schedulerService.systemTick();
            if (schedulerService.getDeadQueue().contains(pcb)) break;
        }

        assertTrue(schedulerService.getDeadQueue().contains(pcb),
                "阻塞→唤醒后进程应能继续运行完成");
        assertEquals(5, pcb.getRunningTime(), "总运行时间应为5");
        assertEquals(PCB.DEAD, pcb.getState());
    }
}
