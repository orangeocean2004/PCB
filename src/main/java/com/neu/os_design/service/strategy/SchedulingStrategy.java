package com.neu.os_design.service.strategy;

import com.neu.os_design.model.PCB;
import java.util.List;

/**
 * 进程调度算法的策略接口
 */
public interface SchedulingStrategy {
    /**
     * 根据不同的算法逻辑，从就绪队列中选出一个最合适的进程
     * @param readyQueue 当前的就绪队列
     * @return 选中的进程 (如果队列为空返回 null)
     */
    PCB selectNextProcess(List<PCB> readyQueue);

    /**
     * 返回该算法的名字
     */
    String getAlgorithmName();
}