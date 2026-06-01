package com.neu.os_design.service.strategy.algorithm;

import com.neu.os_design.model.PCB;
import com.neu.os_design.service.strategy.SchedulingStrategy;

import java.util.List;

public class PreemptivePriority implements SchedulingStrategy {
    @Override
    public PCB selectNextProcess(List<PCB> readyQueue) {
        if (readyQueue.isEmpty()) return null;

        PCB best = null;
        for (PCB pcb : readyQueue) {
            if (best == null || isBetterCandidate(pcb, best)) {
                best = pcb;
            }
        }
        return best;
    }

    public boolean hasHigherPriority(PCB candidate, PCB current) {
        return candidate.getPriority() < current.getPriority();
    }

    private boolean isBetterCandidate(PCB candidate, PCB current) {
        if (hasHigherPriority(candidate, current)) {
            return true;
        }
        if (candidate.getPriority() > current.getPriority()) {
            return false;
        }
        if (candidate.getArrivalTime() != current.getArrivalTime()) {
            return candidate.getArrivalTime() < current.getArrivalTime();
        }
        return candidate.getPid() < current.getPid();
    }

    @Override
    public String getAlgorithmName() {
        return "Preemptive Priority";
    }
}
