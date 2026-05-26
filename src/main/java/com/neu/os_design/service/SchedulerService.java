package com.neu.os_design.service;

import com.neu.os_design.model.PCB;

import java.util.List;

public interface SchedulerService {

    //
    PCB submitProcess(int totalTime, int priority, int needA, int needB, int needC, int memoryNeed);

    // 模拟系统时钟滴答，触发调度器进行调度
    void systemTick();

    // 获取当前正在运行的进程，各个队列的进程列表，以及当前系统时间，供前端展示
    List<PCB> getJobQueue();   // 创建态队列

    List<PCB> getReadyQueue();  // 就绪态队列

    List<PCB> getBlockQueue();  // 阻塞态队列

    List<PCB> getDeadQueue();   // 终止态队列

    PCB getRunningProcess();    // 当前正在运行的进程

    int getCurrentTime();   // 当前系统时间

    // ==== 调度算法类型常量 ====
    int ALGO_FCFS = 1;     // 先来先服务
    int ALGO_SJF = 2;      // 短作业优先
    int ALGO_HRRN = 3;     // 高响应比优先
    int ALGO_PRIORITY = 4; // 优先级调度
    int ALGO_RR = 5;       // 时间片轮转

    // 选择调度算法 1 = FCFS 2 = SJF 3 = HRRN 4 = Priority 5 =RR
    void setAlgorithmType(int algorithmType);

    // 重置时钟和所有队列，用于新一轮测试
    void resetClock();
}
