package com.neu.os_design.controller;

import com.neu.os_design.model.MemoryBlock;
import com.neu.os_design.model.PCB;
import com.neu.os_design.service.MemoryService;
import com.neu.os_design.service.SchedulerService;
import com.neu.os_design.service.SystemResourceService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SimulationController {

    @Autowired
    private SchedulerService schedulerService;

    @Autowired
    private MemoryService memoryService;

    @Autowired
    private SystemResourceService resourceService;

    // ==================== 状态查询 ====================

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("currentTime", schedulerService.getCurrentTime());
        status.put("algorithmType", schedulerService.getAlgorithmType());
        status.put("algorithmName", schedulerService.getAlgorithmName());
        status.put("manualDispatchMode", schedulerService.isManualDispatchMode());

        status.put("runningProcess", schedulerService.getRunningProcess());
        status.put("jobQueue", schedulerService.getJobQueue());
        status.put("readyQueue", schedulerService.getReadyQueue());
        status.put("blockQueue", schedulerService.getBlockQueue());
        status.put("deadQueue", schedulerService.getDeadQueue());

        Map<String, Object> resources = new LinkedHashMap<>();
        resources.put("availableA", resourceService.getAvailableA());
        resources.put("availableB", resourceService.getAvailableB());
        resources.put("availableC", resourceService.getAvailableC());
        resources.put("cpuAvailable", resourceService.checkCpuAvailable());
        status.put("resources", resources);

        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("blocks", memoryService.getMemoryStatus());
        memory.put("usedMemory", memoryService.getUsedMemory());
        memory.put("maxAvailable", memoryService.getMaxAvailableMemory());
        memory.put("freePartitionCount", memoryService.getFreePartitionCount());
        status.put("memory", memory);

        return ResponseEntity.ok(status);
    }

    @GetMapping("/processes/{pid}")
    public ResponseEntity<?> getProcess(@PathVariable int pid) {
        for (PCB pcb : schedulerService.getJobQueue()) {
            if (pcb.getPid() == pid) return ResponseEntity.ok(pcb);
        }
        for (PCB pcb : schedulerService.getReadyQueue()) {
            if (pcb.getPid() == pid) return ResponseEntity.ok(pcb);
        }
        for (PCB pcb : schedulerService.getBlockQueue()) {
            if (pcb.getPid() == pid) return ResponseEntity.ok(pcb);
        }
        for (PCB pcb : schedulerService.getDeadQueue()) {
            if (pcb.getPid() == pid) return ResponseEntity.ok(pcb);
        }
        PCB running = schedulerService.getRunningProcess();
        if (running != null && running.getPid() == pid) return ResponseEntity.ok(running);

        return ResponseEntity.notFound().build();
    }

    // ==================== 进程操作 ====================

    @PostMapping("/processes")
    public ResponseEntity<PCB> submitProcess(@RequestBody Map<String, Integer> body) {
        int totalTime = body.getOrDefault("totalTime", 5);
        int priority = body.getOrDefault("priority", 1);
        int needA = body.getOrDefault("needA", 0);
        int needB = body.getOrDefault("needB", 0);
        int needC = body.getOrDefault("needC", 0);
        int memoryNeed = body.getOrDefault("memoryNeed", 64);

        PCB pcb = schedulerService.submitProcess(totalTime, priority, needA, needB, needC, memoryNeed);
        return ResponseEntity.ok(pcb);
    }

    @DeleteMapping("/processes/{pid}")
    public ResponseEntity<Map<String, Object>> cancelProcess(@PathVariable int pid) {
        boolean success = schedulerService.cancelProcess(pid);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("pid", pid);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/processes/block")
    public ResponseEntity<Map<String, Object>> blockProcess() {
        boolean success = schedulerService.blockProcess();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/processes/{pid}/wakeup")
    public ResponseEntity<Map<String, Object>> wakeupProcess(@PathVariable int pid) {
        boolean success = schedulerService.wakeupProcess(pid);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("pid", pid);
        return ResponseEntity.ok(result);
    }

    // ==================== 时钟推进 ====================

    @PostMapping("/tick")
    public ResponseEntity<Map<String, Object>> tick() {
        schedulerService.systemTick();
        return ResponseEntity.ok(Map.of("currentTime", schedulerService.getCurrentTime()));
    }

    @PostMapping("/tick/{n}")
    public ResponseEntity<Map<String, Object>> tickN(@PathVariable int n) {
        int clamped = Math.min(n, 100); // 防止一次推进太多
        for (int i = 0; i < clamped; i++) {
            schedulerService.systemTick();
        }
        return ResponseEntity.ok(Map.of("currentTime", schedulerService.getCurrentTime(),
                "ticksExecuted", clamped));
    }

    // ==================== 算法切换 ====================

    @PutMapping("/algorithm/{type}")
    public ResponseEntity<Map<String, Object>> setAlgorithm(@PathVariable int type) {
        schedulerService.setAlgorithmType(type);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("algorithmType", schedulerService.getAlgorithmType());
        result.put("algorithmName", schedulerService.getAlgorithmName());
        return ResponseEntity.ok(result);
    }

    // ==================== 重置 ====================

    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> reset() {
        schedulerService.resetClock();
        return ResponseEntity.ok(Map.of("message", "模拟已重置"));
    }

    // ==================== 手动/自动模式 ====================

    @PutMapping("/dispatch-mode")
    public ResponseEntity<Map<String, Object>> setDispatchMode(@RequestBody Map<String, Boolean> body) {
        boolean manual = body.getOrDefault("manual", false);
        schedulerService.setResourceDispatchMode(manual);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("manualDispatchMode", schedulerService.isManualDispatchMode());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/dispatch")
    public ResponseEntity<Map<String, Object>> dispatchResources() {
        schedulerService.dispatchResources();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobQueueSize", schedulerService.getJobQueue().size());
        result.put("readyQueueSize", schedulerService.getReadyQueue().size());
        return ResponseEntity.ok(result);
    }
}
