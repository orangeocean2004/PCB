package com.neu.os_design.service.strategy.algorithm;
import com.neu.os_design.model.PCB;
import com.neu.os_design.service.strategy.SchedulingStrategy;

import java.util.List;

public class RR implements SchedulingStrategy {
    @Override
    public PCB selectNextProcess(List<PCB> readyQueue) {
        if (readyQueue.isEmpty()) return null;
        return readyQueue.get(0);
    }
    @Override
    public String getAlgorithmName() { return "RR (时间片轮转)"; }
}