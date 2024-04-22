package com.java.labs.avi.service;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class RequestCounterService {
    private final AtomicLong requestCount = new AtomicLong();

    public void incrementCount() {
        requestCount.incrementAndGet();
    }

    public long getRequestCount() {
        return requestCount.get();
    }
}
