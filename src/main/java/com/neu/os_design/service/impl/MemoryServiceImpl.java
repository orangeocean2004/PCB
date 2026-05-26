package com.neu.os_design.service.impl;

import com.neu.os_design.model.MemoryBlock;
import com.neu.os_design.service.MemoryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class MemoryServiceImpl implements MemoryService {

    // 维护一个严格按照物理地址从小到大排序的内存块链表
    private final List<MemoryBlock> memoryBlocks = new ArrayList<>();

    @Override
    public void resetMemory(int totalSize) {
        memoryBlocks.clear();
        memoryBlocks.add(new MemoryBlock(0, totalSize, true, -1)); // 初始化为一整块闲内存
    }

    @Override
    public boolean allocateMemory(int pid, int size) {

        for (MemoryBlock block : memoryBlocks) {
            // 如果发现已经有一块不是空闲的，而且占有者就是当前请求的 PID，果断拒绝！
            if (!block.isFree() && block.getOccupantPid() == pid) {
                System.err.println("[内存警告] PID=" + pid + " 试图重复申请内存被拦截！");
                return false;
            }
        }

        MemoryBlock bestFitBlock = null;

        // 遍历所有块，找满足条件且size最小空闲块
        for (MemoryBlock block : memoryBlocks) {
            if (block.isFree() && block.getSize() >= size) {
                if (bestFitBlock == null) {
                    // 还没有找到合适的块，那么当前块就是最优块
                    bestFitBlock = block;
                } else if (block.getSize() < bestFitBlock.getSize()) {
                    // 当前块更小更适合，那么更新最优块
                    bestFitBlock = block;
                }
            }
        }

        // 如果没有找到合适的块，分配失败
        if (bestFitBlock == null) {
            return false;
        }

        if (bestFitBlock.getSize() == size) {
            bestFitBlock.setFree(false);
            bestFitBlock.setSize(size);
            bestFitBlock.setOccupantPid(pid);
        } else {
            // 找到一个更大的块，分割成两块：一块分配给进程，一块继续空闲
            int originalSize = bestFitBlock.getSize();
            int originalStart = bestFitBlock.getStartAddress();

            // 更新原块为分配块
            bestFitBlock.setFree(false);
            bestFitBlock.setSize(size);
            bestFitBlock.setOccupantPid(pid);

            // 创建一个新的空闲块，起始地址为分配块的结束地址，大小为剩余大小
            MemoryBlock newFreeBlock = new MemoryBlock(originalStart + size, originalSize - size, true, -1);

            int index = memoryBlocks.indexOf(bestFitBlock);
            memoryBlocks.add(index + 1, newFreeBlock); // 在分配块后面插入新的空闲块
        }

        return true;
    }

    @Override
    public void releaseMemory(int pid) {
        // 遍历所有块，找到占用该进程的块，标记为闲置
        for (MemoryBlock block : memoryBlocks) {
            if (!block.isFree() && block.getOccupantPid() == pid) {
                block.setFree(true);
                block.setOccupantPid(-1);
            }
        }

        // 合并相邻的空闲块
        for (int i = 0; i < memoryBlocks.size() - 1;) {
            MemoryBlock current = memoryBlocks.get(i);
            MemoryBlock next = memoryBlocks.get(i + 1);

            if (current.isFree() && next.isFree()) {
                // 如果当前和下一个都空，合并当前块和下一个块
                current.setSize(current.getSize() + next.getSize());
                memoryBlocks.remove(i + 1); // 移除下一个块
                // 不 i++ 因为合并后当前块的下一个块已经变了，需要继续检查当前块和新的下一个块是否也空闲
            } else {
                i++; // 只有当没有合并时才移动到下一个块
            }
        }
    }

    @Override
    public List<MemoryBlock> getMemoryStatus() {
        // 返回内存块列表 包含每块内存的起始地址 大小 是否空闲 占用该内存块的进程ID
        return new ArrayList<>(memoryBlocks);
    }

    @Override
    public int getMaxAvailableMemory() {
        int maxSize = 0;
        for (MemoryBlock block : memoryBlocks) {
            if (block.isFree() && block.getSize() > maxSize) {
                maxSize = block.getSize();
            }
        }
        return maxSize;
    }

    @Override
    public int getUsedMemory() {
        int used = 0;
        for (MemoryBlock block : memoryBlocks) {
            if (!block.isFree()) {
                used += block.getSize();
            }
        }
        return used;
    }

    @Override
    public int getFreePartitionCount() {
        int freeCount = 0;
        for (MemoryBlock block : memoryBlocks) {
            if (block.isFree()) {
                freeCount++;
            }
        }
        return freeCount;
    }
}
