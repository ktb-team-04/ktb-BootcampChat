package com.ktb.chatapp.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class AuthLoadShedderTest {

    @Test
    void rejectsImmediatelyWhenAllPermitsAreInUse() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuthLoadShedder shedder = new AuthLoadShedder(2, registry);

        assertThat(shedder.tryAcquire()).isTrue();
        assertThat(shedder.tryAcquire()).isTrue();
        assertThat(shedder.tryAcquire()).isFalse();
        assertThat(registry.get("auth.requests.active").gauge().value()).isEqualTo(2);
        assertThat(registry.get("auth.requests.rejected").counter().count()).isEqualTo(1);

        shedder.release();
        assertThat(shedder.tryAcquire()).isTrue();
    }
}
