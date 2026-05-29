package com.neu.os_design.service;

import com.neu.os_design.model.PCB;

public interface SystemResourceService {

    // 重置系统资源
    void resetResources(int initA, int initB, int initC);

    // 检查系统剩余资源是否足够供应给该进程
    boolean checkResourcesEnough(PCB pcb);

    // 检查进程请求是否没有超过系统总资源上限
    boolean checkResourcesWithinTotal(int needA, int needB, int needC);

    // 为这个进程分配所需的 ABC 资源
    // @return true 成功 false 失败
    boolean allocateResources(PCB pcb);

    // 进程结束 回收占用的 ABC 三种资源
    void releaseResources(PCB pcb);

    // 检查当前有没有可用的 CPU
    boolean checkCpuAvailable();

    // 占用CPU
    boolean allocateCpu();

    // 释放CPU
    void releaseCpu();

    // 查询当前可用资源数量（供前端展示）
    int getAvailableA();
    int getAvailableB();
    int getAvailableC();

    // 查询系统资源总量
    int getTotalA();
    int getTotalB();
    int getTotalC();
}
