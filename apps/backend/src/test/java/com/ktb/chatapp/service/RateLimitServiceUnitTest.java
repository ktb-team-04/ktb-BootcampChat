package com.ktb.chatapp.service;

import com.ktb.chatapp.service.ratelimit.RateLimitCounter;
import com.ktb.chatapp.service.ratelimit.RateLimitStore;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitService 단위 테스트")
class RateLimitServiceUnitTest {

    private static final String CLIENT_ID = "client-1";

    @Mock
    private RateLimitStore rateLimitStore;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService(rateLimitStore);
    }

    @Test
    @DisplayName("최초 요청은 공유 clientId 카운터를 증가시키고 남은 횟수를 반환한다")
    void checkRateLimit_FirstRequest_IncrementsSharedCounter() {
        when(rateLimitStore.incrementAndGet(CLIENT_ID, 30L))
                .thenReturn(new RateLimitCounter(1, 30));

        RateLimitCheckResult result =
                rateLimitService.checkRateLimit(CLIENT_ID, 3, Duration.ofSeconds(30));

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(2);
        assertThat(result.windowSeconds()).isEqualTo(30);
        assertThat(result.retryAfterSeconds()).isEqualTo(30);
        verify(rateLimitStore).incrementAndGet(CLIENT_ID, 30L);
    }

    @Test
    @DisplayName("원자 증가 결과가 한도 이하면 요청을 허용한다")
    void checkRateLimit_BelowLimit_AllowsRequest() {
        when(rateLimitStore.incrementAndGet(CLIENT_ID, 30L))
                .thenReturn(new RateLimitCounter(2, 20));

        RateLimitCheckResult result =
                rateLimitService.checkRateLimit(CLIENT_ID, 3, Duration.ofSeconds(30));

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(1);
        assertThat(result.retryAfterSeconds()).isEqualTo(20);
    }

    @Test
    @DisplayName("원자 증가 결과가 한도를 초과하면 요청을 차단한다")
    void checkRateLimit_OverLimit_RejectsRequest() {
        when(rateLimitStore.incrementAndGet(CLIENT_ID, 30L))
                .thenReturn(new RateLimitCounter(4, 10));

        RateLimitCheckResult result =
                rateLimitService.checkRateLimit(CLIENT_ID, 3, Duration.ofSeconds(30));

        assertThat(result.allowed()).isFalse();
        assertThat(result.remaining()).isZero();
        assertThat(result.retryAfterSeconds()).isEqualTo(10);
    }

    @Test
    @DisplayName("0초 window는 최소 1초로 정규화한다")
    void checkRateLimit_ZeroWindow_NormalizesToOneSecond() {
        when(rateLimitStore.incrementAndGet(CLIENT_ID, 1L))
                .thenReturn(new RateLimitCounter(1, 1));

        RateLimitCheckResult result = rateLimitService.checkRateLimit(CLIENT_ID, 3, Duration.ZERO);

        assertThat(result.allowed()).isTrue();
        assertThat(result.windowSeconds()).isEqualTo(1);
    }

    @Test
    @DisplayName("null window는 최소 1초로 정규화한다")
    void checkRateLimit_NullWindow_NormalizesToOneSecond() {
        when(rateLimitStore.incrementAndGet(CLIENT_ID, 1L))
                .thenReturn(new RateLimitCounter(1, 1));

        RateLimitCheckResult result = rateLimitService.checkRateLimit(CLIENT_ID, 3, null);

        assertThat(result.allowed()).isTrue();
        assertThat(result.windowSeconds()).isEqualTo(1);
    }

    @Test
    @DisplayName("저장소 실패 시 요청을 허용하고 전체 한도를 남긴다")
    void checkRateLimit_StoreFailure_FailsOpenDeterministically() {
        when(rateLimitStore.incrementAndGet(CLIENT_ID, 30L))
                .thenThrow(new IllegalStateException("store down"));

        RateLimitCheckResult result =
                rateLimitService.checkRateLimit(CLIENT_ID, 3, Duration.ofSeconds(30));

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(3);
        assertThat(result.retryAfterSeconds()).isEqualTo(30);
    }

    @Test
    @DisplayName("null clientId도 안정적인 문자열 키로 처리한다")
    void checkRateLimit_NullClientId_UsesStableKey() {
        when(rateLimitStore.incrementAndGet("null", 30L))
                .thenReturn(new RateLimitCounter(1, 30));

        RateLimitCheckResult result =
                rateLimitService.checkRateLimit(null, 3, Duration.ofSeconds(30));

        assertThat(result.allowed()).isTrue();
        verify(rateLimitStore).incrementAndGet("null", 30L);
    }
}
