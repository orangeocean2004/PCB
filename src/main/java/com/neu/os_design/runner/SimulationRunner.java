package com.neu.os_design.runner;

import com.neu.os_design.model.PCB;
import com.neu.os_design.service.MemoryService;
import com.neu.os_design.service.SchedulerService;
import com.neu.os_design.service.SystemResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SimulationRunner implements CommandLineRunner {

    @Autowired
    private SchedulerService schedulerService;

    @Autowired
    private MemoryService memoryService;

    @Autowired
    private SystemResourceService resourceService;

    // 存储每个算法的测试结果
    private static class AlgoResult {
        String name;
        List<PCB> finishedProcesses = new ArrayList<>();
        int totalTime;
    }

    private final Map<Integer, AlgoResult> batchResults = new LinkedHashMap<>();
    private final Map<Integer, AlgoResult> staggeredResults = new LinkedHashMap<>();

    // ==== 测试进程规格 ====
    // 批量到达场景: 所有进程在 t=0 同时到达
    private static final int[][] BATCH_PROCS = {
        // {totalTime, priority, needA, needB, needC, memory}
        {8, 3, 1, 1, 1, 100},  // P1 长作业 中优先级
        {2, 1, 1, 1, 1, 100},  // P2 短作业 最高优先级
        {4, 4, 1, 1, 1, 100},  // P3 中作业 最低优先级
        {2, 2, 1, 1, 1, 100},  // P4 短作业 次高优先级
    };

    // 错峰到达场景: 进程在不同时间到达
    private static final int[][] STAGGERED_PROCS = {
        // {totalTime, priority, needA, needB, needC, memory, arrivalTime}
        {10, 1, 1, 1, 1, 100, 0},   // P1 长作业 t=0到达
        {2,  2, 1, 1, 1, 100, 2},   // P2 短作业 t=2到达
        {2,  3, 1, 1, 1, 100, 4},   // P3 短作业 t=4到达
        {2,  4, 1, 1, 1, 100, 6},   // P4 短作业 t=6到达
    };

    private static final int[] ALL_ALGOS = {
        SchedulerService.ALGO_FCFS,
        SchedulerService.ALGO_SJF,
        SchedulerService.ALGO_HRRN,
        SchedulerService.ALGO_PRIORITY,
        SchedulerService.ALGO_RR,
    };

    private static String algoName(int algo) {
        switch (algo) {
            case SchedulerService.ALGO_FCFS:     return "FCFS(先来先服务)";
            case SchedulerService.ALGO_SJF:      return "SJF(短作业优先)";
            case SchedulerService.ALGO_HRRN:     return "HRRN(高响应比优先)";
            case SchedulerService.ALGO_PRIORITY: return "Priority(优先级)";
            case SchedulerService.ALGO_RR:       return "RR(时间片轮转)";
            default: return "Unknown";
        }
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("\n");
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║         操作系统进程调度 — 五大调度算法对比测试               ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        // ============ 场景一：批量到达 ============
        System.out.println("\n┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│  场景一：批量到达 — 4个进程在 t=0 同时到达                    │");
        System.out.println("│  P1(需8,pri=3) P2(需2,pri=1) P3(需4,pri=4) P4(需2,pri=2)    │");
        System.out.println("└──────────────────────────────────────────────────────────────┘");

        for (int algo : ALL_ALGOS) {
            runBatchScenario(algo);
        }

        // ============ 场景二：错峰到达 ============
        System.out.println("\n\n┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│  场景二：错峰到达 — 长作业先到，短作业陆续到达                 │");
        System.out.println("│  P1(需10)@t=0  P2(需2)@t=2  P3(需2)@t=4  P4(需2)@t=6      │");
        System.out.println("└──────────────────────────────────────────────────────────────┘");

        for (int algo : ALL_ALGOS) {
            runStaggeredScenario(algo);
        }

        // ============ 打印对比报告 ============
        printComparisonReport();

        System.out.println("\n====== 五大调度算法对比测试完毕！ ======");
    }

    // ==================== 批量到达场景 ====================
    private void runBatchScenario(int algo) {
        resetEnvironment();
        schedulerService.setAlgorithmType(algo);

        // 所有进程在 t=0 同时提交
        for (int[] spec : BATCH_PROCS) {
            schedulerService.submitProcess(spec[0], spec[1], spec[2], spec[3], spec[4], spec[5]);
        }

        System.out.println("\n>>> [" + algoName(algo) + "] 批量到达 — 开始运行");

        for (int i = 0; i < 200; i++) {
            schedulerService.systemTick();
            if (schedulerService.getDeadQueue().size() == BATCH_PROCS.length) {
                break;
            }
        }

        AlgoResult result = new AlgoResult();
        result.name = algoName(algo);
        result.totalTime = schedulerService.getCurrentTime();
        for (PCB pcb : schedulerService.getDeadQueue()) {
            result.finishedProcesses.add(pcb);
        }
        batchResults.put(algo, result);
    }

    // ==================== 错峰到达场景 ====================
    private void runStaggeredScenario(int algo) {
        resetEnvironment();
        schedulerService.setAlgorithmType(algo);

        System.out.println("\n>>> [" + algoName(algo) + "] 错峰到达 — 开始运行");

        for (int i = 0; i < 200; i++) {
            // 在特定时间提交进程
            int ct = schedulerService.getCurrentTime();
            for (int[] spec : STAGGERED_PROCS) {
                if (ct == spec[6]) {
                    schedulerService.submitProcess(spec[0], spec[1], spec[2], spec[3], spec[4], spec[5]);
                }
            }

            schedulerService.systemTick();

            if (schedulerService.getDeadQueue().size() == STAGGERED_PROCS.length) {
                break;
            }
        }

        AlgoResult result = new AlgoResult();
        result.name = algoName(algo);
        result.totalTime = schedulerService.getCurrentTime();
        for (PCB pcb : schedulerService.getDeadQueue()) {
            result.finishedProcesses.add(pcb);
        }
        staggeredResults.put(algo, result);
    }

    // ==================== 对比报告 ====================
    private void printComparisonReport() {
        System.out.println("\n\n");
        System.out.println("╔══════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                        📊 五大调度算法 对比报告                          ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════╝");

        printScenarioReport("场景一：批量到达 (t=0 同时到达)", batchResults);
        printScenarioReport("场景二：错峰到达 (长作业先到)", staggeredResults);

        // 综合分析
        System.out.println("\n┌──────────────────────────────────────────────────────────────────────────┐");
        System.out.println("│                           📝 算法行为总结                                 │");
        System.out.println("└──────────────────────────────────────────────────────────────────────────┘");

        System.out.println("┌─────────────────────────────────────────────────────────────────────────┐");
        System.out.println("│ FCFS  │ 按提交顺序执行，公平但短作业会被长作业阻塞 (护航效应)             │");
        System.out.println("│ SJF   │ 最短作业优先，平均等待最小，但长作业可能饿死                      │");
        System.out.println("│ HRRN  │ 响应比=(等待+服务)/服务，兼顾长短作业，是FCFS和SJF的折中          │");
        System.out.println("│ Prio  │ 按优先级执行，高优先级进程被优待，低优先级可能饿死                 │");
        System.out.println("│ RR    │ 时间片轮转，响应快、公平，但上下文切换开销大，平均周转较长         │");
        System.out.println("└─────────────────────────────────────────────────────────────────────────┘");
    }

    private void printScenarioReport(String title, Map<Integer, AlgoResult> results) {
        System.out.println("\n━━━ " + title + " ━━━");

        // 表头
        System.out.println("┌──────────┬──────────────────────────────────┬────────┬────────┐");
        System.out.println("│   算法   │       各进程 (到达/完成/周转)      │ 平均周转│ 平均等待│");
        System.out.println("├──────────┼──────────────────────────────────┼────────┼────────┤");

        for (AlgoResult r : results.values()) {
            // 按 PID 排序
            List<PCB> procs = new ArrayList<>(r.finishedProcesses);
            procs.sort(Comparator.comparingInt(PCB::getPid));

            StringBuilder detail = new StringBuilder();
            double totalTAT = 0, totalWT = 0;
            for (PCB p : procs) {
                int tat = p.getFinishTime() - p.getArrivalTime();
                int wt = tat - p.getTotalTime();
                totalTAT += tat;
                totalWT += wt;
                if (detail.length() > 0) detail.append(" ");
                detail.append(String.format("P%d(%d/%d/%d)",
                    p.getPid(), p.getArrivalTime(), p.getFinishTime(), tat));
            }
            double avgTAT = totalTAT / procs.size();
            double avgWT = totalWT / procs.size();

            System.out.printf("│ %-8s │ %-32s │ %6.2f │ %6.2f │\n",
                r.name.split("\\(")[0], detail.toString(), avgTAT, avgWT);
        }
        System.out.println("└──────────┴──────────────────────────────────┴────────┴────────┘");
    }

    // ==================== 辅助方法 ====================
    private void resetEnvironment() {
        schedulerService.resetClock();
        memoryService.resetMemory(1024);
        resourceService.resetResources(10, 10, 10);
    }

}
