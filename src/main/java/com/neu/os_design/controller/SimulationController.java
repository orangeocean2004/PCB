package com.neu.os_design.controller;

import com.neu.os_design.model.MemoryBlock;
import com.neu.os_design.model.PCB;
import com.neu.os_design.service.MemoryService;
import com.neu.os_design.service.SchedulerService;
import com.neu.os_design.service.SystemResourceService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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

        status.put("runningProcess", schedulerService.getRunningProcess());
        status.put("pendingQueue", schedulerService.getPendingQueue());
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

        // 分拆的A、B、C阻塞队列（供前端拆分渲染）
        List<PCB> blockQueueA = schedulerService.getBlockQueueA();
        List<PCB> blockQueueB = schedulerService.getBlockQueueB();
        List<PCB> blockQueueC = schedulerService.getBlockQueueC();

        status.put("blockQueueA", blockQueueA);
        status.put("blockQueueB", blockQueueB);
        status.put("blockQueueC", blockQueueC);

        return ResponseEntity.ok(status);
    }

    @GetMapping("/processes/{pid}")
    public ResponseEntity<?> getProcess(@PathVariable int pid) {
        for (PCB pcb : schedulerService.getPendingQueue()) {
            if (pcb.getPid() == pid) return ResponseEntity.ok(pcb);
        }
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
    public ResponseEntity<?> submitProcess(@RequestBody Map<String, Object> body) {
        int totalTime = readInt(body, "totalTime", 5);
        int priority = readInt(body, "priority", 1);
        int needA = readInt(body, "needA", 0);
        int needB = readInt(body, "needB", 0);
        int needC = readInt(body, "needC", 0);
        int memoryNeed = readInt(body, "memoryNeed", 64);
        Integer submitTime = readOptionalInt(body, "submitTime");
        String submitClock = normalizeClock(readString(body, "submitClock", null));

        String invalidMessage = validateResourceCapacity(needA, needB, needC);
        if (invalidMessage != null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("message", invalidMessage);
            return ResponseEntity.badRequest().body(result);
        }

        PCB pcb = submitTime == null
                ? schedulerService.submitProcess(totalTime, priority, needA, needB, needC, memoryNeed)
                : schedulerService.submitProcessAt(submitTime, submitClock,
                    totalTime, priority, needA, needB, needC, memoryNeed);
        return ResponseEntity.ok(pcb);
    }

    @PostMapping("/processes/batch")
    public ResponseEntity<Map<String, Object>> submitBatchProcesses(@RequestBody List<Map<String, Object>> body) {
        List<PCB> submitted = new ArrayList<>();
        List<Map<String, Object>> rejected = new ArrayList<>();
        Integer baseClockMinute = findEarliestClockMinute(body);

        for (int index = 0; index < body.size(); index++) {
            Map<String, Object> item = body.get(index);
            int totalTime = readInt(item, "totalTime", 5);
            int priority = readInt(item, "priority", 1);
            int needA = readInt(item, "needA", 0);
            int needB = readInt(item, "needB", 0);
            int needC = readInt(item, "needC", 0);
            int memoryNeed = readInt(item, "memoryNeed", 64);
            Integer submitTime = readOptionalInt(item, "submitTime");
            String submitClock = normalizeClock(readString(item, "submitClock", null));

            String invalidMessage = validateResourceCapacity(needA, needB, needC);
            if (invalidMessage != null) {
                Map<String, Object> rejectedItem = new LinkedHashMap<>();
                rejectedItem.put("index", index);
                rejectedItem.put("message", invalidMessage);
                rejectedItem.put("request", item);
                rejected.add(rejectedItem);
                continue;
            }

            if (submitTime == null) {
                Integer clockMinute = parseClockMinute(submitClock);
                if (clockMinute != null && baseClockMinute != null) {
                    submitTime = schedulerService.getCurrentTime() + Math.max(0, clockMinute - baseClockMinute);
                } else {
                    submitTime = schedulerService.getCurrentTime();
                }
            }

            submitted.add(schedulerService.submitProcessAt(submitTime, submitClock,
                    totalTime, priority, needA, needB, needC, memoryNeed));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submitted", submitted);
        result.put("rejected", rejected);
        result.put("submittedCount", submitted.size());
        result.put("rejectedCount", rejected.size());
        result.put("pendingQueueSize", schedulerService.getPendingQueue().size());
        result.put("jobQueueSize", schedulerService.getJobQueue().size());
        result.put("readyQueueSize", schedulerService.getReadyQueue().size());
        return ResponseEntity.ok(result);
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


    private int readInt(Map<String, Object> body, String key, int defaultValue) {
        Integer value = readOptionalInt(body, key);
        return value == null ? defaultValue : value;
    }

    private Integer readOptionalInt(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) return null;
            try {
                return Integer.parseInt(trimmed);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String readString(Map<String, Object> body, String key, String defaultValue) {
        Object value = body.get(key);
        if (value == null) return defaultValue;
        String text = value.toString().trim();
        return text.isEmpty() ? defaultValue : text;
    }

    private Integer findEarliestClockMinute(List<Map<String, Object>> processes) {
        Integer earliest = null;
        for (Map<String, Object> process : processes) {
            if (readOptionalInt(process, "submitTime") != null) {
                continue;
            }
            Integer clockMinute = parseClockMinute(readString(process, "submitClock", null));
            if (clockMinute != null && (earliest == null || clockMinute < earliest)) {
                earliest = clockMinute;
            }
        }
        return earliest;
    }

    private String normalizeClock(String value) {
        Integer minuteOfDay = parseClockMinute(value);
        if (minuteOfDay == null) return null;
        int hour = minuteOfDay / 60;
        int minute = minuteOfDay % 60;
        return String.format("%02d:%02d", hour, minute);
    }

    private Integer parseClockMinute(String value) {
        if (value == null) return null;
        String normalized = value.trim().replace('：', ':');
        String[] parts = normalized.split(":");
        if (parts.length != 2) return null;
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return null;
            }
            return hour * 60 + minute;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String validateResourceCapacity(int needA, int needB, int needC) {
        if (resourceService.checkResourcesWithinTotal(needA, needB, needC)) {
            return null;
        }

        return "资源请求超过系统总量: A=" + needA + "/" + resourceService.getTotalA()
                + ", B=" + needB + "/" + resourceService.getTotalB()
                + ", C=" + needC + "/" + resourceService.getTotalC();
    }
}
