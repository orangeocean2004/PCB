package com.neu.os_design.service.impl;

import com.neu.os_design.model.PCB;
import com.neu.os_design.service.SystemResourceService;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;

@Service
public class SystemResourceServiceImpl implements SystemResourceService {
    private int totalA = 10;
    private int totalB = 10;
    private int totalC = 10;

    // 默认三种资源初始数量为10
    private Semaphore resourceA = new Semaphore(10);
    private Semaphore resourceB = new Semaphore(10);
    private Semaphore resourceC = new Semaphore(10);

    // 单核CPU 只有一个CPU资源 相当于一个互斥锁 mutex
    private Semaphore cpuResource = new Semaphore(1);

    @Override
    public void resetResources(int initA, int initB, int initC) {
        // 重置信号量 仅用于初始化使用
        totalA = initA;
        totalB = initB;
        totalC = initC;
        resourceA = new Semaphore(initA);
        resourceB = new Semaphore(initB);
        resourceC = new Semaphore(initC);
        cpuResource = new Semaphore(1);
    }

    @Override
    public boolean checkResourcesEnough(PCB pcb) {
        // 计算该进程还需要多少资源
        int remainingA = Math.max(0, pcb.getNeedA() - pcb.getGetA());
        int remainingB = Math.max(0, pcb.getNeedB() - pcb.getGetB());
        int remainingC = Math.max(0, pcb.getNeedC() - pcb.getGetC());

        // 检查信号量中的可用资源是否够
        return resourceA.availablePermits() >= remainingA &&
                resourceB.availablePermits() >= remainingB &&
                resourceC.availablePermits() >= remainingC;
    }

    @Override
    public boolean checkResourcesWithinTotal(int needA, int needB, int needC) {
        return needA <= totalA && needB <= totalB && needC <= totalC;
    }

    // P
    @Override
    public boolean allocateResources(PCB pcb) {
        // 检查资源是否够，避免死锁
        if (!checkResourcesEnough(pcb)) {
            return false;
        }

        int remainingNeedA = Math.max(0, pcb.getNeedA() - pcb.getGetA());
        int remainingNeedB = Math.max(0, pcb.getNeedB() - pcb.getGetB());
        int remainingNeedC = Math.max(0, pcb.getNeedC() - pcb.getGetC());

        // 尝试 P 操作占用资源
        boolean aAcquired = remainingNeedA == 0 || resourceA.tryAcquire(remainingNeedA);
        boolean bAcquired = remainingNeedB == 0 || resourceB.tryAcquire(remainingNeedB);
        boolean cAcquired = remainingNeedC == 0 || resourceC.tryAcquire(remainingNeedC);

        if (aAcquired && bAcquired && cAcquired) {
            pcb.setGetA(pcb.getGetA() + remainingNeedA);
            pcb.setGetB(pcb.getGetB() + remainingNeedB);
            pcb.setGetC(pcb.getGetC() + remainingNeedC);
            return true;
        } else {
            // 如果有一个资源获取失败了，要释放已经获取的资源
            if (aAcquired) resourceA.release(remainingNeedA);
            if (bAcquired) resourceB.release(remainingNeedB);
            if (cAcquired) resourceC.release(remainingNeedC);
            return false;
        }
    }

    // V
    @Override
    public void releaseResources(PCB pcb) {
        int getA = pcb.getGetA();
        int getB = pcb.getGetB();
        int getC = pcb.getGetC();

        if (getA > 0) {
            resourceA.release(getA);
            pcb.setGetA(0);
        }
        if (getB > 0) {
            resourceB.release(getB);
            pcb.setGetB(0);
        }
        if (getC > 0) {
            resourceC.release(getC);
            pcb.setGetC(0);
        }
    }

    @Override
    public boolean checkCpuAvailable() {
        return cpuResource.availablePermits() > 0;
    }

    // P cpu
    @Override
    public boolean allocateCpu() {

        boolean gotCpu = cpuResource.tryAcquire();
        if(!gotCpu)
        {
            System.out.println("CPU 被占用了");
        }
        return gotCpu;

    }

    @Override
    public void releaseCpu() {
        if(cpuResource.availablePermits() == 0) {
            cpuResource.release();
        }
    }

    @Override
    public int getAvailableA() {
        return resourceA.availablePermits();
    }

    @Override
    public int getAvailableB() {
        return resourceB.availablePermits();
    }

    @Override
    public int getAvailableC() {
        return resourceC.availablePermits();
    }

    @Override
    public int getTotalA() {
        return totalA;
    }

    @Override
    public int getTotalB() {
        return totalB;
    }

    @Override
    public int getTotalC() {
        return totalC;
    }

}


