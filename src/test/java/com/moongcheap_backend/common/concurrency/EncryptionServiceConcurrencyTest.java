package com.moongcheap_backend.common.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.moongcheap_backend.common.crypto.EncryptionService;
import com.moongcheap_backend.support.concurrency.AbstractConcurrencyTest;
import com.moongcheap_backend.support.concurrency.ConcurrencyRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("동시성 6-2: EncryptionService thread-safety")
class EncryptionServiceConcurrencyTest extends AbstractConcurrencyTest {

    @Autowired private EncryptionService encryptionService;

    @Test
    @DisplayName("50 스레드에서 encrypt-decrypt 라운드트립이 모두 정확하게 복원된다")
    void encryptDecryptRoundtripUnderConcurrency() throws Exception {
        int threadCount = 50;
        ConcurrencyRunner.Result result = ConcurrencyRunner.run(threadCount, idx -> {
            String plain = "user-data-" + idx + "-" + System.nanoTime();
            String cipher = encryptionService.encrypt(plain);
            String decrypted = encryptionService.decrypt(cipher);
            return plain.equals(decrypted);
        });

        assertThat(result.success()).isEqualTo(threadCount);
        assertThat(result.failure()).isEqualTo(0);
    }
}
