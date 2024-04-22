package com.java.labs.avi.service;

import com.java.labs.avi.model.Auditorium;
import com.java.labs.avi.repository.AuditoriumRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class AuditoriumService {
    private final AuditoriumRepository auditoriumRepository;

    @Autowired
    public AuditoriumService(AuditoriumRepository auditoriumRepository) {
        this.auditoriumRepository = auditoriumRepository;
    }

    public List<Auditorium> findAll() {
        return auditoriumRepository.findAll();
    }
}
