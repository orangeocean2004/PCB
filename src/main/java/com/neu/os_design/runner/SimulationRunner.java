package com.neu.os_design.runner;

import com.neu.os_design.model.MemoryBlock;
import com.neu.os_design.service.MemoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Profile("runner")
@Component
public class SimulationRunner implements CommandLineRunner {

    @Autowired
    private MemoryService memoryService;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("\n=======================================================");
        System.out.println("      内存管理 (Best Fit) 自动化暴力测试启动！      ");
        System.out.println("=======================================================\n");

        int totalMemorySize = 1000;
        System.out.println(">>> 1. 重置系统内存为 " + totalMemorySize + "KB");
        memoryService.resetMemory(totalMemorySize);
        printMemoryState();

        System.out.println("\n>>> 2. 连续申请内存 (模拟多进程进场)");
        allocateAndPrint(101, 200);
        allocateAndPrint(102, 100);
        allocateAndPrint(103, 300);
        allocateAndPrint(104, 50);
        printMemoryState();

        System.out.println("\n>>> 3. [重要] 测试防止二次分配 (测你队友发现的bug)");
        allocateAndPrint(102, 150); // 102 之前已经申请过了，应该被拦下

        System.out.println("\n>>> 4. 释放部分内存，产生碎片化");
        releaseAndPrint(102);
        releaseAndPrint(104);
        printMemoryState();

        System.out.println("\n>>> 5. 测试 Best Fit (最佳适应算法)");
        System.out.println("    当前有两个空闲块：原来 102 的 100KB，原来 104 的 50KB，以及最后一大块");
        System.out.println("    尝试分配 PID=105, Size=40KB.  (最佳适应应该选中 50KB 的那个小块)");
        allocateAndPrint(105, 40);
        printMemoryState();

        System.out.println("\n>>> 6. 测试相邻空闲块向上/向下合并功能");
        System.out.println("    现在释放 PID=101(200KB) 和 PID=103(300KB)，看看它们会不会与周边的空闲块融为一体");
        releaseAndPrint(101);
        releaseAndPrint(103);
        releaseAndPrint(105);
        printMemoryState();

        System.out.println("\n>>> 7. 测试超过最大可用额度的爆雷情况");
        allocateAndPrint(999, 1200); // 只要 1000 的内存，非要申请 1200
        printMemoryState();

        System.out.println("\n=======================================================");
        System.out.println("                 内存管理测试结束！                 ");
        System.out.println("=======================================================");
    }

    private void allocateAndPrint(int pid, int size) {
        boolean success = memoryService.allocateMemory(pid, size);
        if (success) {
            System.out.println("  [分配成功] PID=" + pid + " 申请 " + size + "KB");
        } else {
            System.out.println("  [分配失败] PID=" + pid + " 申请 " + size + "KB 被驳回");
        }
    }

    private void releaseAndPrint(int pid) {
        System.out.println("  [释放] PID=" + pid + " 的内存被回收。");
        memoryService.releaseMemory(pid);
    }

    private void printMemoryState() {
        System.out.println("  --------------------------------------");
        System.out.println("  【当前内存条状态】");
        List<MemoryBlock> blocks = memoryService.getMemoryStatus();
        for (MemoryBlock block : blocks) {
            System.out.printf("   [Addr: %4d - %4d] Size: %4d KB | %s\n",
                    block.getStartAddress(),
                    block.getStartAddress() + block.getSize() - 1,
                    block.getSize(),
                    block.isFree() ? "空闲 (Free)" : "被 PID=" + block.getOccupantPid() + " 占用"
            );
        }
        System.out.printf("  【统计】已用: %d KB | 空闲分区数: %d | 最大可用空闲块: %d KB\n",
                memoryService.getUsedMemory(),
                memoryService.getFreePartitionCount(),
                memoryService.getMaxAvailableMemory()
        );
        System.out.println("  --------------------------------------");
    }
}
