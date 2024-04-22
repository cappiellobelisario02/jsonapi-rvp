package com.java.labs.avi.dto;

public class CourseInfoDto {
    private String classGroup;
    private String roomNumber;
    private String courseTitle;
    private String lecturer;

    public String getClassGroup() {
        return classGroup;
    }

    public void setClassGroup(String classGroup) {
        this.classGroup = classGroup;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public void setCourseTitle(String courseTitle) {
        this.courseTitle = courseTitle;
    }

    public String getLecturer() {
        return lecturer;
    }

    public void setLecturer(String lecturer) {
        this.lecturer = lecturer;
    }

    public CourseInfoDto(String classGroup,
                         String roomNumber,
                         String courseTitle,
                         String lecturer) {
        this.classGroup = classGroup;
        this.roomNumber = roomNumber;
        this.courseTitle = courseTitle;
        this.lecturer = lecturer;
    }
}
