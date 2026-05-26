package com.neu.os_design.service.strategy.algorithm;
import com.neu.os_design.model.PCB;
import com.neu.os_design.service.strategy.SchedulingStrategy;

import java.util.List;

public class HRRN implements SchedulingStrategy {
    @Override
    public PCB selectNextProcess(List<PCB> readyQueue) {
        if (readyQueue.isEmpty()) return null;
        PCB best = null;
        for (PCB pcb : readyQueue) {
            if (best == null || pcb.getResponseRatio() > best.getResponseRatio()) {
                best = pcb;
            }
        }
        return best;
    }
    @Override
    public String getAlgorithmName() { return "HRRN (高响应比优先)"; }
}