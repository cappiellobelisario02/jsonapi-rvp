package com.java.labs.avi.repository;

import com.java.labs.avi.model.Auditorium;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriumRepository extends JpaRepository<Auditorium, Long> {
    Optional<Auditorium> findByNumber(String number);
}
