package com.ktb.chatapp.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Limits CPU-heavy password work and rejects spike overflow without queuing. */
@Component
public class AuthLoadShedder {

    private final Semaphore permits;
    private final AtomicInteger active = new AtomicInteger();
    private final MeterRegistry meterRegistry;

    public AuthLoadShedder(
            @Value("${app.auth.max-concurrent-requests:4}") int maxConcurrentRequests,
            MeterRegistry meterRegistry) {
        this.permits = new Semaphore(Math.max(1, maxConcurrentRequests));
        this.meterRegistry = meterRegistry;
        Gauge.builder("auth.requests.active", active, AtomicInteger::get)
                .description("Currently executing authentication requests")
                .register(meterRegistry);
    }

    public boolean tryAcquire() {
        if (!permits.tryAcquire()) {
            meterRegistry.counter("auth.requests.rejected", "reason", "capacity").increment();
            return false;
        }
        active.incrementAndGet();
        return true;
    }

    public void release() {
        active.decrementAndGet();
        permits.release();
    }
}
