package com.neu.os_design.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.Semaphore;

/**
 * 进程控制块 (PCB)
 */
@Data // 自动生成所有属性的 getter、setter、toString、equals、hashCode
@NoArgsConstructor // 自动生成无参构造函数 (Spring/MyBatis 等框架通常需要)
public class PCB {

    // ==== 进程状态常量定义 ====
    //  1 - 创建 2 - 就绪 3 - 阻塞 4 - 运行 5 - 终止
    public static final int CREATED = 0;
    public static final int READY = 1;
    public static final int BLOCK = 2;
    public static final int RUNNING = 3;
    public static final int DEAD = 4;

    // ==== 临界资源与互斥锁 ====
    private static int pid_seq = 1;
    private static final Semaphore pidMutex = new Semaphore(1);

    // ================== 基本进程信息 ==================
    private int pid;
    private int totalTime;
    private int runningTime = 0; // 可以直接给出初始值
    private int priority;
    private int state = CREATED;

    // ================== ABC资源需求和占用 ==================
    private int needA;
    private int needB;
    private int needC;
    private int getA = 0;
    private int getB = 0;
    private int getC = 0;

    // ================== 内存需求 ==================
    private int memoryNeed;
    private int allocatedMemory = 0;

    // ================== 时间统计 ==================
    private int arrivalTime;
    private int startTime = -1;
    private int finishTime = -1;
    private int waitingTime = 0;
    private int turnaroundTime = 0;
    private double responseRatio = 1.0;


    public PCB(int totalTime, int priority, int needA, int needB, int needC, int memoryNeed, int currentTime) {
        // PV 操作分配 PID
        try {
            pidMutex.acquire();
            this.pid = pid_seq++;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pidMutex.release();
        }

        this.totalTime = totalTime;
        this.priority = priority;
        this.needA = needA;
        this.needB = needB;
        this.needC = needC;

        // 保证内存下限
        if (memoryNeed <= 0) {
            this.memoryNeed = 64;
        } else if (memoryNeed > 1024) {
            this.memoryNeed = 1024;
        } else {
            this.memoryNeed = memoryNeed;
        }

        this.arrivalTime = currentTime;
    }

    // 运行进程 需要在外部调用 allocateCpu() 来占用 CPU 资源
    public boolean runProcess(int currentTime) {
        if (this.runningTime < this.totalTime) {
            this.runningTime++;
        }
        if (this.runningTime >= this.totalTime) {
            this.state = DEAD;
            this.finishTime = currentTime;
            this.turnaroundTime = this.finishTime - this.arrivalTime;
            return true;
        }
        return false;
    }

    // 更新等待时间 需要在外部调用 allocateCpu() 来占用 CPU 资源
    public void updateWaitingTime(int currentTime) {
        if (this.state == READY && this.startTime == -1) {
            this.waitingTime = currentTime - this.arrivalTime;
        } else if (this.state == READY && this.startTime != -1) {
            this.waitingTime = currentTime - this.arrivalTime - this.runningTime;
        }
    }

    // 计算响应比 需要在外部调用 allocateCpu() 来占用 CPU 资源
    public void calculateResponseRatio() {
        if (this.totalTime > 0) {
            this.responseRatio = (double) (this.waitingTime + this.totalTime) / this.totalTime;
        }
    }

    public String getStateString() {
        switch (this.state) {
            case CREATED: return "创建态";
            case READY: return "就绪态";
            case BLOCK: return "阻塞态";
            case RUNNING: return "运行态";
            case DEAD: return "终止态";
            default: return "未知";
        }
    }

}
