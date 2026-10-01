package com.moongcheap_backend.common.unit.crypto.success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moongcheap_backend.common.crypto.EncryptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.encrypt.TextEncryptor;

@ExtendWith(MockitoExtension.class)
class EncryptionServiceSuccessTest {

    @Mock
    private TextEncryptor textEncryptor;

    @InjectMocks
    private EncryptionService encryptionService;

    @Nested
    @DisplayName("encrypt - 성공")
    class EncryptTest {

        @Test
        void 유효한_평문을_암호화한다() {
            when(textEncryptor.encrypt("plainText")).thenReturn("cipherText");

            String result = encryptionService.encrypt("plainText");

            verify(textEncryptor).encrypt("plainText");
            assertThat(result).isEqualTo("cipherText");
        }

        @Test
        void null을_암호화한다() {
            String result = encryptionService.encrypt(null);

            verify(textEncryptor, never()).encrypt(any());
            assertThat(result).isNull();
        }

        @Test
        void 빈_문자열_blank를_암호화한다() {
            String result = encryptionService.encrypt("   ");

            verify(textEncryptor, never()).encrypt(any());
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("decrypt - 성공")
    class DecryptTest {

        @Test
        void 유효한_암호문을_복호화한다() {
            when(textEncryptor.decrypt("cipherText")).thenReturn("plainText");

            String result = encryptionService.decrypt("cipherText");

            verify(textEncryptor).decrypt("cipherText");
            assertThat(result).isEqualTo("plainText");
        }

        @Test
        void null을_복호화한다() {
            String result = encryptionService.decrypt(null);

            verify(textEncryptor, never()).decrypt(any());
            assertThat(result).isNull();
        }

        @Test
        void 빈_문자열_blank를_복호화한다() {
            String result = encryptionService.decrypt("   ");

            verify(textEncryptor, never()).decrypt(any());
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("maskBusinessNumber - 성공")
    class MaskBusinessNumberTest {

        @Test
        void _10자리_사업자번호_digits를_마스킹한다() {
            String result = encryptionService.maskBusinessNumber("1698100227");

            assertThat(result).isEqualTo("169-81-00***");
        }

        @Test
        void null을_마스킹한다() {
            String result = encryptionService.maskBusinessNumber(null);

            assertThat(result).isEqualTo("***-**-*****");
        }

        @Test
        void _9자리_digits를_마스킹한다() {
            String result = encryptionService.maskBusinessNumber("123456789");

            assertThat(result).isEqualTo("***-**-*****");
        }

        @Test
        void _11자리_digits를_마스킹한다() {
            String result = encryptionService.maskBusinessNumber("12345678901");

            assertThat(result).isEqualTo("***-**-*****");
        }
    }

    @Nested
    @DisplayName("maskPhoneNumber - 성공")
    class MaskPhoneNumberTest {

        @Test
        void _11자리_핸드폰_번호를_마스킹한다() {
            String result = encryptionService.maskPhoneNumber("01012345678");

            assertThat(result).isEqualTo("010-****-5678");
        }

        @Test
        void _10자리_전화번호를_마스킹한다() {
            String result = encryptionService.maskPhoneNumber("0212345678");

            assertThat(result).isEqualTo("021-***-5678");
        }

        @Test
        void 하이픈이_포함된_11자리_번호를_마스킹한다() {
            String result = encryptionService.maskPhoneNumber("010-1234-5678");

            assertThat(result).isEqualTo("010-****-5678");
        }

        @Test
        void _8자리_미만_전화번호를_마스킹한다() {
            String result = encryptionService.maskPhoneNumber("1234567");

            assertThat(result).isEqualTo("****");
        }

        @Test
        void null_전화번호를_마스킹한다() {
            String result = encryptionService.maskPhoneNumber(null);

            assertThat(result).isEqualTo("****");
        }

        @Test
        void _8자리_이상이지만_10_11자리가_아닌_번호를_마스킹한다() {
            String result = encryptionService.maskPhoneNumber("12345678");

            assertThat(result).isEqualTo("123****5678");
        }
    }
}
