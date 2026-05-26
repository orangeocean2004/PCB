package com.neu.os_design.service.strategy.algorithm;
import com.neu.os_design.model.PCB;
import com.neu.os_design.service.strategy.SchedulingStrategy;

import java.util.List;

public class SJF implements SchedulingStrategy {
    @Override
    public PCB selectNextProcess(List<PCB> readyQueue) {
        if (readyQueue.isEmpty()) return null;
        PCB best = null;
        for (PCB pcb : readyQueue) {
            if (best == null || pcb.getTotalTime() < best.getTotalTime()) {
                best = pcb;
            }
        }
        return best;
    }
    @Override
    public String getAlgorithmName() { return "SJF (短作业优先)"; }
}