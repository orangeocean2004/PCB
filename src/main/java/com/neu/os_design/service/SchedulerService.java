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

    // 查询当前调度算法类型和名称（供前端展示）
    int getAlgorithmType();
    String getAlgorithmName();

    // 重置时钟和所有队列，用于新一轮测试
    void resetClock();

    // ==== 手动/自动资源分配模式 ====
    // true=手动模式(进程进入创建队列，手动触发分配)
    // false=自动模式(默认，提交时自动分配内存)
    void setResourceDispatchMode(boolean manual);

    boolean isManualDispatchMode();

    // 手动触发一次资源分配：遍历创建队列，分配内存+ABC资源，成功则转入就绪
    void dispatchResources();

    // ==== 进程操作 ====
    // 强制终止指定PID的进程，释放其全部资源，移入终止队列
    boolean cancelProcess(int pid);

    // 阻塞当前正在运行的进程（释放CPU，保留资源，移入阻塞队列）
    boolean blockProcess();

    // 唤醒指定PID的阻塞进程（需已持有资源才能唤醒，移入就绪队列）
    boolean wakeupProcess(int pid);
}
