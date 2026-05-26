package com.neu.os_design.model;

import lombok.Data;

/**
 * 内存块实体类
 * 用于表示连续内存中的一个片段，方便前端渲染内存条状态
 */
@Data
public class MemoryBlock {
    private int startAddress; // 内存块起始地址
    private int size;
    private boolean free; // 是否已分配
    private int occupantPid; // 占用该内存块的进程的 PID

    public MemoryBlock(int startAddress, int size, boolean free, int occupantPid) {
        this.startAddress = startAddress;
        this.size = size;
        this.free = free;
        this.occupantPid = occupantPid;
    }
}
