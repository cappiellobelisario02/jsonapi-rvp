package com.java.labs.avi.dto;

public class ScheduleDto {
    private Long id;
    private CourseInfoDto courseInfo;
    private ScheduleInfoDto scheduleInfo;

    public ScheduleDto() {

    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CourseInfoDto getCourseInfo() {
        return courseInfo;
    }

    public void setCourseInfo(CourseInfoDto courseInfo) {
        this.courseInfo = courseInfo;
    }

    public ScheduleInfoDto getScheduleInfo() {
        return scheduleInfo;
    }

    public void setScheduleInfo(ScheduleInfoDto scheduleInfo) {
        this.scheduleInfo = scheduleInfo;
    }

    public ScheduleDto(Long id, CourseInfoDto courseInfo, ScheduleInfoDto scheduleInfo) {
        this.id = id;
        this.courseInfo = courseInfo;
        this.scheduleInfo = scheduleInfo;
    }
}