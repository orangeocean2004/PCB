package com.neu.os_design.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * 进程控制块 (PCB)
 */
@Data // 自动生成所有属性的 getter、setter、toString、equals、hashCode
@NoArgsConstructor // 自动生成无参构造函数 (Spring/MyBatis 等框架通常需要)
public class PCB {

    /**
     * 最简单的消息传递式 IPC：发送进程把文本消息投递到目标进程的收件箱。
     */
    @Data
    @NoArgsConstructor
    public static class IpcMessage {
        private int fromPid;
        private int toPid;
        private String content;
        private int sentTime;

        public IpcMessage(int fromPid, int toPid, String content, int sentTime) {
            this.fromPid = fromPid;
            this.toPid = toPid;
            this.content = content;
            this.sentTime = sentTime;
        }
    }

    // ==== 进程状态常量定义 ====
    // -1 - 待提交 0 - 创建 1 - 就绪 2 - 阻塞 3 - 运行 4 - 终止
    public static final int SCHEDULED = -1;
    public static final int CREATED = 0;
    public static final int READY = 1;
    public static final int BLOCK = 2;
    public static final int RUNNING = 3;
    public static final int DEAD = 4;
    public static final int DEFAULT_MEMORY_NEED = 64;
    public static final int MAX_MEMORY_NEED = 1024;

    // ==== 临界资源与互斥锁 ====
    private static int pid_seq = 1;
    private static final Semaphore pidMutex = new Semaphore(1);

    public static void resetPidSeq() {
        try {
            pidMutex.acquire();
            pid_seq = 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pidMutex.release();
        }
    }

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
    private String submitClock;
    private final List<IpcMessage> inbox = new ArrayList<>();


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

        // 保证内存下限；上限由提交入口校验并拒绝，避免悄悄钳位掩盖输入错误。
        if (memoryNeed <= 0) {
            this.memoryNeed = DEFAULT_MEMORY_NEED;
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

    public void receiveMessage(IpcMessage message) {
        this.inbox.add(message);
    }

    public void clearInbox() {
        this.inbox.clear();
    }

    public String getStateString() {
        switch (this.state) {
            case SCHEDULED: return "待提交";
            case CREATED: return "创建态";
            case READY: return "就绪态";
            case BLOCK: return "阻塞态";
            case RUNNING: return "运行态";
            case DEAD: return "终止态";
            default: return "未知";
        }
    }

}
