package com.neu.os_design.service.strategy.algorithm;

import com.neu.os_design.model.PCB;
import com.neu.os_design.service.strategy.SchedulingStrategy;

import java.util.List;

public class FCFS implements SchedulingStrategy {
    @Override
    public PCB selectNextProcess(List<PCB> readyQueue) {
        if (readyQueue.isEmpty()) return null;
        return readyQueue.get(0); // 永远拿队头
    }

    @Override
    public String getAlgorithmName() {
        return "FCFS (先来先服务)";
    }
}
