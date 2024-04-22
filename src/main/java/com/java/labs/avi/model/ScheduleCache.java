package com.java.labs.avi.model;

import com.java.labs.avi.dto.ScheduleDto;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ScheduleCache {
    private final Map<Long, ScheduleDto> cache = new ConcurrentHashMap<>();

    public ScheduleDto get(Long id) {
        return cache.get(id);
    }

    public void put(Long id, ScheduleDto scheduleDto) {
        cache.put(id, scheduleDto);
    }

    public void delete(Long id) {
        cache.remove(id);
    }

    public Map<Long, ScheduleDto> getCacheContents() {
        return new HashMap<>(cache);
    }
}