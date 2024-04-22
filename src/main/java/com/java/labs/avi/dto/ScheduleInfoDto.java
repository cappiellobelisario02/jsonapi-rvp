package com.java.labs.avi.dto;

public class ScheduleInfoDto {
    private String weekday;
    private int subgroupIndex;
    private int weekOrdinal;
    private String sessionStart;
    private String sessionEnd;

    public String getWeekday() {
        return weekday;
    }

    public void setWeekday(String weekday) {
        this.weekday = weekday;
    }

    public int getSubgroupIndex() {
        return subgroupIndex;
    }

    public void setSubgroupIndex(int subgroupIndex) {
        this.subgroupIndex = subgroupIndex;
    }

    public int getWeekOrdinal() {
        return weekOrdinal;
    }

    public void setWeekOrdinal(int weekOrdinal) {
        this.weekOrdinal = weekOrdinal;
    }

    public String getSessionStart() {
        return sessionStart;
    }

    public void setSessionStart(String sessionStart) {
        this.sessionStart = sessionStart;
    }

    public String getSessionEnd() {
        return sessionEnd;
    }

    public void setSessionEnd(String sessionEnd) {
        this.sessionEnd = sessionEnd;
    }

    public ScheduleInfoDto(String weekday,
                           int subgroupIndex,
                           int weekOrdinal,
                           String sessionStart,
                           String sessionEnd) {
        this.weekday = weekday;
        this.subgroupIndex = subgroupIndex;
        this.weekOrdinal = weekOrdinal;
        this.sessionStart = sessionStart;
        this.sessionEnd = sessionEnd;
    }
}
