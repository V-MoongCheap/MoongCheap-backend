package com.moongcheap_backend.auth.unit.application.success;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.auth.application.ProviderUnlinkChunkService;
import com.moongcheap_backend.auth.application.ProviderUnlinkWorker;
import com.moongcheap_backend.auth.domain.PendingProviderUnlink;
import com.moongcheap_backend.auth.infrastructure.oauth.GoogleOAuth2Client;
import com.moongcheap_backend.auth.infrastructure.oauth.KakaoOAuth2Client;
import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.member.domain.SocialProvider;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProviderUnlinkWorkerSuccessTest {

    @Mock private ProviderUnlinkChunkService chunkService;
    @Mock private KakaoOAuth2Client kakaoOAuth2Client;
    @Mock private GoogleOAuth2Client googleOAuth2Client;
    @Mock private EncryptionService encryptionService;

    @InjectMocks
    private ProviderUnlinkWorker worker;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(worker, "chunkSize", 20);
        ReflectionTestUtils.setField(worker, "maxRetry", 8);
        ReflectionTestUtils.setField(worker, "hideDurationMs", 300_000L);
    }

    @Nested
    @DisplayName("tick - 성공 흐름")
    class TickSuccessTest {

        @Test
        void kakao_row는_admin_key로_unlink_후_markDone() {
            PendingProviderUnlink p = mock(PendingProviderUnlink.class);
            when(p.getId()).thenReturn(1L);
            when(p.getProvider()).thenReturn(SocialProvider.KAKAO);
            when(p.getProviderId()).thenReturn("kakao-123");
            when(chunkService.claim(eq(20), any(Duration.class))).thenReturn(List.of(p));

            worker.tick();

            verify(kakaoOAuth2Client).unlink("kakao-123");
            verify(googleOAuth2Client, never()).revoke(any());
            verify(chunkService).markDone(1L);
            verify(chunkService, never()).markFailed(anyLong(), anyInt());
        }

        @Test
        void google_row는_refresh_token_복호화_후_revoke_및_markDone() {
            PendingProviderUnlink p = mock(PendingProviderUnlink.class);
            when(p.getId()).thenReturn(2L);
            when(p.getProvider()).thenReturn(SocialProvider.GOOGLE);
            when(p.getRevocationTokenEnc()).thenReturn("enc-token");
            when(chunkService.claim(eq(20), any(Duration.class))).thenReturn(List.of(p));
            when(encryptionService.decrypt("enc-token")).thenReturn("plain-refresh-token");

            worker.tick();

            verify(googleOAuth2Client).revoke("plain-refresh-token");
            verify(kakaoOAuth2Client, never()).unlink(any());
            verify(chunkService).markDone(2L);
            verify(chunkService, never()).markFailed(anyLong(), anyInt());
        }

        @Test
        void claim_결과가_비면_아무것도_호출하지_않는다() {
            when(chunkService.claim(eq(20), any(Duration.class))).thenReturn(List.of());

            worker.tick();

            verify(kakaoOAuth2Client, never()).unlink(any());
            verify(googleOAuth2Client, never()).revoke(any());
            verify(chunkService, never()).markDone(anyLong());
            verify(chunkService, never()).markFailed(anyLong(), anyInt());
        }
    }

    @Nested
    @DisplayName("tick - 실패 처리")
    class TickFailureTest {

        @Test
        void kakao_호출이_예외를_던지면_markFailed_로_재시도_스케줄() {
            PendingProviderUnlink p = mock(PendingProviderUnlink.class);
            when(p.getId()).thenReturn(3L);
            when(p.getProvider()).thenReturn(SocialProvider.KAKAO);
            when(p.getProviderId()).thenReturn("kakao-fail");
            when(chunkService.claim(eq(20), any(Duration.class))).thenReturn(List.of(p));
            doThrow(new RuntimeException("network")).when(kakaoOAuth2Client).unlink("kakao-fail");

            worker.tick();

            verify(chunkService).markFailed(3L, 8);
            verify(chunkService, never()).markDone(anyLong());
        }

        @Test
        void google_호출이_예외를_던지면_markFailed_로_재시도_스케줄() {
            PendingProviderUnlink p = mock(PendingProviderUnlink.class);
            when(p.getId()).thenReturn(4L);
            when(p.getProvider()).thenReturn(SocialProvider.GOOGLE);
            when(p.getRevocationTokenEnc()).thenReturn("enc-token");
            when(chunkService.claim(eq(20), any(Duration.class))).thenReturn(List.of(p));
            when(encryptionService.decrypt("enc-token")).thenReturn("plain");
            doThrow(new RuntimeException("network")).when(googleOAuth2Client).revoke("plain");

            worker.tick();

            verify(chunkService).markFailed(4L, 8);
            verify(chunkService, never()).markDone(anyLong());
        }

        @Test
        void 한_row가_실패해도_다른_row는_계속_처리한다() {
            PendingProviderUnlink failing = mock(PendingProviderUnlink.class);
            when(failing.getId()).thenReturn(5L);
            when(failing.getProvider()).thenReturn(SocialProvider.KAKAO);
            when(failing.getProviderId()).thenReturn("bad");
            doThrow(new RuntimeException("network")).when(kakaoOAuth2Client).unlink("bad");

            PendingProviderUnlink succeeding = mock(PendingProviderUnlink.class);
            when(succeeding.getId()).thenReturn(6L);
            when(succeeding.getProvider()).thenReturn(SocialProvider.KAKAO);
            when(succeeding.getProviderId()).thenReturn("good");

            when(chunkService.claim(eq(20), any(Duration.class)))
                .thenReturn(List.of(failing, succeeding));

            worker.tick();

            verify(chunkService).markFailed(5L, 8);
            verify(chunkService).markDone(6L);
        }
    }
}
