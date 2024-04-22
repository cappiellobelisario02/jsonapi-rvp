package com.java.labs.avi.controller;


import com.java.labs.avi.dto.ScheduleDto;
import com.java.labs.avi.service.ScheduleService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class CacheController {

    private final ScheduleService scheduleService;

    @Autowired
    public CacheController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping("/cache/schedules")
    public ResponseEntity<Map<Long, ScheduleDto>> getCacheContents() {
        Map<Long, ScheduleDto> cacheContents = scheduleService.viewCache();
        return ResponseEntity.ok(cacheContents);
    }
}
