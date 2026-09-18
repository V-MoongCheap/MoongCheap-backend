package com.moongcheap_backend.payments.application;

import com.moongcheap_backend.common.exception.BusinessException;
import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.member.domain.Member;
import com.moongcheap_backend.payments.domain.CustomerKey;
import com.moongcheap_backend.payments.infrastructure.CustomerKeyRepository;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerKeyService {

    private static final int CUSTOMER_KEY_LENGTH = 50;
    private static final int KEY_CREATE_MAX_ATTEMPTS = 5;
    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL_CHARACTERS = "_=.@";
    private static final String ALLOWED_CHARACTERS =
        UPPERCASE + LOWERCASE + DIGITS + SPECIAL_CHARACTERS;

    private final SecureRandom random = new SecureRandom();

    private final CustomerKeyRepository customerKeyRepository;

    public void createCustomerKey(Member member) {
        if (customerKeyRepository.existsById(member.getId())) {
            return;
        }

        for (int attempt = 1; attempt <= KEY_CREATE_MAX_ATTEMPTS; attempt++) {
            CustomerKey customerKey = new CustomerKey(member, createCustomerKey());

            try {
                customerKeyRepository.saveAndFlush(customerKey);
                return;
            } catch (DataIntegrityViolationException exception) {
                if (customerKeyRepository.existsById(member.getId())) {
                    return;
                }

                if (attempt == KEY_CREATE_MAX_ATTEMPTS) {
                    throw new BusinessException(ErrorCode.CUSTOMER_KEY_ISSUE_FAILED);
                }
            }
        }
    }

    // 소비자 식별키 발급: 영문 대소문자, 숫자, 허용 특수문자를 각각 포함하는 50자 문자열
    private String createCustomerKey() {
        char[] key = new char[CUSTOMER_KEY_LENGTH];
        key[0] = randomCharacter(UPPERCASE);
        key[1] = randomCharacter(LOWERCASE);
        key[2] = randomCharacter(DIGITS);
        key[3] = randomCharacter(SPECIAL_CHARACTERS);

        for (int i = 4; i < key.length; i++) {
            key[i] = randomCharacter(ALLOWED_CHARACTERS);
        }

        for (int i = key.length - 1; i > 0; i--) {
            int index = random.nextInt(i + 1);
            char temporary = key[i];
            key[i] = key[index];
            key[index] = temporary;
        }

        return new String(key);
    }

    private char randomCharacter(String characters) {
        return characters.charAt(random.nextInt(characters.length()));
    }

}
