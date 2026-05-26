package com.neu.os_design.service;

import com.neu.os_design.model.MemoryBlock;

import java.util.List;

/**
 * 内存管理服务接口
 * 定义了内存分配和回收的相关方法
 */
public interface MemoryService {

    /**
     * 重置内存 初始化为一整块闲内存
     *
     * @param totalSize 内存总大小 ??KB
     */
    void resetMemory(int totalSize);

    /**
     * 为进程分配内存
     *
     * @param pid  进程ID
     * @param size 需要分配的内存大小 ??KB
     * @return true 分配成功 false 分配失败（内存不足）
     */
    boolean allocateMemory(int pid, int size);

    /**
     * 回收进程占用的内存
     *
     * @param pid 进程ID
     */
    void releaseMemory(int pid);

    /**
     * 获取当前内存状态
     *
     * @return 内存块列表 包含每块内存的起始地址 大小 是否空闲 占用该内存块的进程ID
     */
    List<MemoryBlock> getMemoryStatus();

    /**
     * 获取当前最大的连续空闲内存块的大小 用来判断能不能满足新进程
     */
    int getMaxAvailableMemory();
}
