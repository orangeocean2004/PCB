package com.neu.os_design.runner;

import com.neu.os_design.model.PCB;
import com.neu.os_design.service.SchedulerService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Profile("runner")
@Component
public class AlgorithmComparisonRunner implements CommandLineRunner {

    @Autowired
    private SchedulerService schedulerService;

    private static final int[] ALGORITHMS = {
        SchedulerService.ALGO_FCFS,
        SchedulerService.ALGO_SJF,
        SchedulerService.ALGO_HRRN,
        SchedulerService.ALGO_PRIORITY,
        SchedulerService.ALGO_RR
    };

    private static final String[] ALGO_NAMES = {
        "FCFS", "SJF ", "HRRN", "PRIO", "RR  "
    };

    @Override
    public void run(String... args) throws Exception {
        System.out.println("\n===========================================================");
        System.out.println("          调度算法对比报告 (Algorithm Comparison)          ");
        System.out.println("===========================================================\n");

        batchArrivalScenario();
        staggeredArrivalScenario();

        System.out.println("===========================================================\n");
    }

    // ========== 场景1：批量到达（4进程同时到达） ==========

    private void batchArrivalScenario() {
        System.out.println("【场景1】批量到达 — 4个进程同时在 t=0 到达");
        System.out.println("  进程参数: P1(长/10) P2(中/5) P3(中/8) P4(短/3)\n");

        System.out.println("┌────────┬──────────────────────────────┬──────────┐");
        System.out.println("│  算法  │  各进程周转时间               │ 平均周转 │");
        System.out.println("├────────┼──────────────────────────────┼──────────┤");

        for (int i = 0; i < ALGORITHMS.length; i++) {
            schedulerService.resetClock();
            schedulerService.setAlgorithmType(ALGORITHMS[i]);

            // t=0 同时提交4个进程 (totalTime, priority, needA, B, C, memory)
            PCB p1 = schedulerService.submitProcess(10, 1, 0, 0, 0, 64);
            PCB p2 = schedulerService.submitProcess(5, 1, 0, 0, 0, 64);
            PCB p3 = schedulerService.submitProcess(8, 1, 0, 0, 0, 64);
            PCB p4 = schedulerService.submitProcess(3, 1, 0, 0, 0, 64);

            // 推进直到全部完成
            runUntilAllDone(4);

            int t1 = getTurnaround(p1);
            int t2 = getTurnaround(p2);
            int t3 = getTurnaround(p3);
            int t4 = getTurnaround(p4);
            double avg = (t1 + t2 + t3 + t4) / 4.0;

            System.out.printf("│ %-6s │ T1=%-3d T2=%-3d T3=%-3d T4=%-3d │ %8.2f │\n",
                    ALGO_NAMES[i], t1, t2, t3, t4, avg);
        }
        System.out.println("└────────┴──────────────────────────────┴──────────┘\n");
    }

    // ========== 场景2：错峰到达（长作业先到，短作业陆续到） ==========

    private void staggeredArrivalScenario() {
        System.out.println("【场景2】错峰到达 — 长作业先到，短作业陆续到达");
        System.out.println("  t=0: P1(长/10)到达");
        System.out.println("  t=2: P2(短/3) 到达");
        System.out.println("  t=4: P3(短/2) 到达\n");

        System.out.println("┌────────┬──────────────────────────────┬──────────┐");
        System.out.println("│  算法  │  各进程周转时间               │ 平均周转 │");
        System.out.println("├────────┼──────────────────────────────┼──────────┤");

        for (int i = 0; i < ALGORITHMS.length; i++) {
            schedulerService.resetClock();
            schedulerService.setAlgorithmType(ALGORITHMS[i]);

            // t=0 长作业到达
            PCB p1 = schedulerService.submitProcess(10, 1, 0, 0, 0, 64);
            schedulerService.systemTick(); // t=1
            schedulerService.systemTick(); // t=2

            // t=2 短作业1到达
            PCB p2 = schedulerService.submitProcess(3, 1, 0, 0, 0, 64);
            schedulerService.systemTick(); // t=3
            schedulerService.systemTick(); // t=4

            // t=4 短作业2到达
            PCB p3 = schedulerService.submitProcess(2, 1, 0, 0, 0, 64);

            // 推进直到全部完成
            runUntilAllDone(3);

            int t1 = getTurnaround(p1);
            int t2 = getTurnaround(p2);
            int t3 = getTurnaround(p3);
            double avg = (t1 + t2 + t3) / 3.0;

            System.out.printf("│ %-6s │ T1=%-3d T2=%-3d T3=%-3d           │ %8.2f │\n",
                    ALGO_NAMES[i], t1, t2, t3, avg);
        }
        System.out.println("└────────┴──────────────────────────────┴──────────┘\n");
    }

    // ========== 辅助方法 ==========

    private void runUntilAllDone(int expectedCount) {
        int maxTicks = 100;
        while (schedulerService.getDeadQueue().size() < expectedCount && maxTicks-- > 0) {
            schedulerService.systemTick();
        }
    }

    private int getTurnaround(PCB pcb) {
        // 进程进入 deadQueue 后，turnaroundTime 已由 runProcess 计算好
        // 从 deadQueue 中查找同一个 PID（resetClock 后 PID 会重置）
        for (PCB dead : schedulerService.getDeadQueue()) {
            if (dead.getPid() == pcb.getPid()) {
                return dead.getTurnaroundTime();
            }
        }
        return -1;
    }
}
