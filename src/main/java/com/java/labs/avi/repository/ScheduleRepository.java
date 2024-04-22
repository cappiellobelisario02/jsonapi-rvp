package com.java.labs.avi.repository;

import com.java.labs.avi.model.Schedule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    @Query(value = "SELECT s.* FROM schedules s "
            + "JOIN groups_table g ON s.group_id = g.id WHERE g.name = :groupName "
            + "AND s.day_of_week = :dayOfWeek "
            + "AND s.week_number = :weekNumber "
            + "AND s.num_subgroup = :numSubgroup", nativeQuery = true)
    List<Schedule> findByGroupNameAndDayOfWeekAndWeekNumberAndNumSubgroup(
            @Param("groupName") String groupName,
            @Param("dayOfWeek") String dayOfWeek,
            @Param("weekNumber") int weekNumber,
            @Param("numSubgroup") int numSubgroup);

    @Query(value = "SELECT s.* FROM schedule s "
            + "JOIN groups_table g ON s.group_id = g.id "
            + "WHERE g.name = :groupName "
            + "AND s.day_of_week = :dayOfWeek "
            + "AND s.week_number = :weekNumber "
            + "AND s.num_subgroup = :numSubgroup "
            + "AND s.start_time = :startTime "
            + "AND s.end_time = :endTime", nativeQuery = true)
    List<Schedule> findByCriteria(
            @Param("groupName") String groupName,
            @Param("dayOfWeek") String dayOfWeek,
            @Param("weekNumber") int weekNumber,
            @Param("numSubgroup") int numSubgroup,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime);


    List<Schedule> findByAuditoriumId(Long id);
}
