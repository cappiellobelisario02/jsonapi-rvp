package com.java.labs.avi.controller;

import com.java.labs.avi.dto.ScheduleDto;
import com.java.labs.avi.exception.BadRequestException;
import com.java.labs.avi.model.Schedule;
import com.java.labs.avi.service.RequestCounterService;
import com.java.labs.avi.service.ScheduleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;



@RestController
@RequestMapping("/schedule")
public class ScheduleController {
    private final ScheduleService scheduleService;
    private final RequestCounterService requestCounterService;

    @Autowired
    public ScheduleController(
            ScheduleService scheduleService,
            RequestCounterService requestCounterService) {
        this.scheduleService = scheduleService;
        this.requestCounterService = requestCounterService;
    }

    @GetMapping
    public ResponseEntity<List<ScheduleDto>> getScheduleForDayOfWeek(
            @RequestParam @NotBlank @Pattern(
                    regexp = "\\d+",
                    message = "Group number must be numeric")
            String groupNumber,
            @RequestParam @NotBlank String dayOfWeek,
            @RequestParam @Min(1) int targetWeekNumber,
            @RequestParam @Min(0) int numSubgroup) {

        List<String> allowedDaysOfWeek = Arrays.asList(
                "Monday",
                "Tuesday",
                "Wednesday",
                "Thursday",
                "Friday",
                "Saturday");

        if (!allowedDaysOfWeek.contains(dayOfWeek)) {
            throw new BadRequestException("Day of week is not valid");
        }

        List<ScheduleDto> scheduleDtos = scheduleService.getScheduleByGroupDayWeekAndSubgroup(
                groupNumber,
                dayOfWeek,
                targetWeekNumber,
                numSubgroup);
        return ResponseEntity.ok(scheduleDtos);
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<ScheduleDto>> createOrUpdateSchedules(
            @Valid @RequestBody List<ScheduleDto> scheduleDtos) {
        List<Schedule> schedules = scheduleDtos.stream()
                .map(scheduleService::convertToEntity)
                .toList();
        List<Schedule> savedSchedules = scheduleService.saveAll(schedules);
        List<ScheduleDto> savedScheduleDtos = scheduleService.convertToDto(savedSchedules);
        return ResponseEntity.ok(savedScheduleDtos);
    }

    @GetMapping("/count")
    public ResponseEntity<Long> getRequestCount() {
        return ResponseEntity.ok(requestCounterService.getRequestCount());
    }

    @PostMapping
    public ResponseEntity<ScheduleDto> createSchedule(@RequestBody ScheduleDto scheduleDto) {
        Schedule schedule = scheduleService.convertToEntity(scheduleDto);
        ScheduleDto createdScheduleDto = scheduleService.createSchedule(schedule);
        return new ResponseEntity<>(createdScheduleDto, HttpStatus.CREATED);
    }


    @PutMapping("/{id}")
    public ResponseEntity<ScheduleDto> updateSchedule(
            @PathVariable Long id,
            @RequestBody ScheduleDto scheduleDetails) {
        ScheduleDto updatedScheduleDto = scheduleService.updateSchedule(id, scheduleDetails);
        return ResponseEntity.ok(updatedScheduleDto);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ScheduleDto> patchSchedule(
            @PathVariable Long id,
            @RequestBody Map<String,
                    Object> updates) {
        ScheduleDto updatedScheduleDto = scheduleService.patchSchedule(id, updates);
        return ResponseEntity.ok(updatedScheduleDto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<HttpStatus> deleteSchedule(@PathVariable Long id) {
        scheduleService.deleteSchedule(id);
        return new ResponseEntity<>(
                HttpStatus.NO_CONTENT);
    }

    @DeleteMapping("/auditorium/{id}")
    public ResponseEntity<HttpStatus> deleteAuditorium(@PathVariable Long id) {
        scheduleService.deleteAuditorium(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @DeleteMapping("/group/{id}")
    public ResponseEntity<HttpStatus> deleteGroup(@PathVariable Long id) {
        scheduleService.deleteGroup(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @DeleteMapping("/subject/{id}")
    public ResponseEntity<HttpStatus> deleteSubject(@PathVariable Long id) {
        scheduleService.deleteSubject(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

}
