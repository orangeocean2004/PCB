package com.neu.os_design.service.strategy.algorithm;
import com.neu.os_design.model.PCB;
import com.neu.os_design.service.strategy.SchedulingStrategy;

import java.util.List;

public class Priority implements SchedulingStrategy {
    @Override
    public PCB selectNextProcess(List<PCB> readyQueue) {
        if (readyQueue.isEmpty()) return null;
        PCB best = null;
        // 假设 priority 数字越小，优先级越高（如果你的设定是越大越高。。。）
        for (PCB pcb : readyQueue) {
            if (best == null || pcb.getPriority() < best.getPriority()) {
                best = pcb;
            }
        }
        return best;
    }
    @Override
    public String getAlgorithmName() { return "Priority (优先级调度)"; }
}