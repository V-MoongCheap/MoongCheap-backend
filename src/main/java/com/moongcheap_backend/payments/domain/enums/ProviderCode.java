package com.moongcheap_backend.payments.domain.enums;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProviderCode {

    // 국내 카드사
    CARD_IBK_BC(Type.CARD, "3K", "기업 BC"),
    CARD_GWANGJU(Type.CARD, "46", "광주은행"),
    CARD_LOTTE(Type.CARD, "71", "롯데카드"),
    CARD_KDB(Type.CARD, "30", "한국산업은행"),
    CARD_BC(Type.CARD, "31", "BC카드"),
    CARD_SAMSUNG(Type.CARD, "51", "삼성카드"),
    CARD_SAEMAUL(Type.CARD, "38", "새마을금고"),
    CARD_SHINHAN(Type.CARD, "41", "신한카드"),
    CARD_SHINHYEOP(Type.CARD, "62", "신협"),
    CARD_CITI(Type.CARD, "36", "씨티카드"),
    CARD_WOORI_BC(Type.CARD, "33", "우리BC카드"),
    CARD_WOORI(Type.CARD, "W1", "우리카드"),
    CARD_POST(Type.CARD, "37", "우체국예금보험"),
    CARD_SAVING_BANK(Type.CARD, "39", "저축은행중앙회"),
    CARD_JEONBUK(Type.CARD, "35", "전북은행"),
    CARD_JEJU(Type.CARD, "42", "제주은행"),
    CARD_KAKAO_BANK(Type.CARD, "15", "카카오뱅크"),
    CARD_K_BANK(Type.CARD, "3A", "케이뱅크"),
    CARD_TOSS_BANK(Type.CARD, "24", "토스뱅크"),
    CARD_HANA(Type.CARD, "21", "하나카드"),
    CARD_HYUNDAI(Type.CARD, "61", "현대카드"),
    CARD_KB(Type.CARD, "11", "KB국민카드"),
    CARD_NH(Type.CARD, "91", "NH농협카드"),
    CARD_SUHYUP(Type.CARD, "34", "Sh수협은행"),

    // 해외 카드사
    CARD_DINERS(Type.CARD, "6D", "다이너스 클럽"),
    CARD_MASTER(Type.CARD, "4M", "마스터카드"),
    CARD_UNIONPAY(Type.CARD, "3C", "유니온페이"),
    CARD_AMEX(Type.CARD, "7A", "아메리칸 익스프레스"),
    CARD_JCB(Type.CARD, "4J", "JCB"),
    CARD_VISA(Type.CARD, "4V", "VISA"),

    // 은행
    BANK_KYONGNAM(Type.BANK, "39", "경남은행"),
    BANK_GWANGJU(Type.BANK, "34", "광주은행"),
    BANK_LOCAL_NONGHYEOP(Type.BANK, "12", "단위농협"),
    BANK_BUSAN(Type.BANK, "32", "부산은행"),
    BANK_SAEMAUL(Type.BANK, "45", "새마을금고"),
    BANK_SANLIM(Type.BANK, "64", "산림조합"),
    BANK_SHINHAN(Type.BANK, "88", "신한은행"),
    BANK_SHINHYEOP(Type.BANK, "48", "신협"),
    BANK_CITI(Type.BANK, "27", "씨티은행"),
    BANK_WOORI(Type.BANK, "20", "우리은행"),
    BANK_POST(Type.BANK, "71", "우체국예금보험"),
    BANK_SAVING(Type.BANK, "50", "저축은행중앙회"),
    BANK_JEONBUK(Type.BANK, "37", "전북은행"),
    BANK_JEJU(Type.BANK, "35", "제주은행"),
    BANK_KAKAO(Type.BANK, "90", "카카오뱅크"),
    BANK_K(Type.BANK, "89", "케이뱅크"),
    BANK_TOSS(Type.BANK, "92", "토스뱅크"),
    BANK_HANA(Type.BANK, "81", "하나은행"),
    BANK_HSBC(Type.BANK, "54", "홍콩상하이은행"),
    BANK_BOA(Type.BANK, "60", "Bank of America"),
    BANK_IBK(Type.BANK, "03", "IBK기업은행"),
    BANK_KB(Type.BANK, "06", "KB국민은행"),
    BANK_IM(Type.BANK, "31", "iM뱅크"),
    BANK_KDB(Type.BANK, "02", "한국산업은행"),
    BANK_NH(Type.BANK, "11", "NH농협은행"),
    BANK_SC(Type.BANK, "23", "SC제일은행"),
    BANK_SUHYUP(Type.BANK, "07", "Sh수협은행"),
    BANK_SUHYUP_CENTRAL(Type.BANK, "30", "수협중앙회"),

    // 증권사
    SEC_KYOBO(Type.SECURITIES, "S8", "교보증권"),
    SEC_DAISHIN(Type.SECURITIES, "SE", "대신증권"),
    SEC_MERITZ(Type.SECURITIES, "SK", "메리츠증권"),
    SEC_MIRAE_ASSET(Type.SECURITIES, "S5", "미래에셋증권"),
    SEC_BOOKOOK(Type.SECURITIES, "SM", "부국증권"),
    SEC_SAMSUNG(Type.SECURITIES, "S3", "삼성증권"),
    SEC_SHINYOUNG(Type.SECURITIES, "SN", "신영증권"),
    SEC_SHINHAN(Type.SECURITIES, "S2", "신한금융투자"),
    SEC_YUANTA(Type.SECURITIES, "S0", "유안타증권"),
    SEC_EUGENE(Type.SECURITIES, "SJ", "유진투자증권"),
    SEC_KAKAO_PAY(Type.SECURITIES, "SQ", "카카오페이증권"),
    SEC_KIWOOM(Type.SECURITIES, "SB", "키움증권"),
    SEC_TOSS(Type.SECURITIES, "ST", "토스증권"),
    SEC_KOREA_FOSS(Type.SECURITIES, "SR", "한국포스증권"),
    SEC_HANA(Type.SECURITIES, "SH", "하나금융투자"),
    SEC_IM(Type.SECURITIES, "S9", "아이엠증권"),
    SEC_KOREA_INVESTMENT(Type.SECURITIES, "S6", "한국투자증권"),
    SEC_HANHWA(Type.SECURITIES, "SG", "한화투자증권"),
    SEC_HYUNDAI_MOTOR(Type.SECURITIES, "SA", "현대차증권"),
    SEC_DB(Type.SECURITIES, "SI", "DB금융투자"),
    SEC_KB(Type.SECURITIES, "S4", "KB증권"),
    SEC_DAOL(Type.SECURITIES, "SP", "다올투자증권"),
    SEC_LIG(Type.SECURITIES, "SO", "LIG투자증권"),
    SEC_NH(Type.SECURITIES, "SL", "NH투자증권"),
    SEC_SK(Type.SECURITIES, "SD", "SK증권");

    private final Type type;
    private final String code;
    private final String displayName;

    public static ProviderCode fromCode(Type type, String code) {
        if (type == null || code == null || code.isBlank()) {
            throw new IllegalArgumentException("기관 유형과 코드는 필수입니다.");
        }

        return Arrays.stream(values())
            .filter(provider -> provider.type == type)
            .filter(provider -> provider.code.equalsIgnoreCase(code))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "지원하지 않는 기관 코드입니다: " + type + "/" + code));
    }

    @Override
    public String toString() {
        return displayName;
    }

    public enum Type {
        CARD,
        BANK,
        SECURITIES
    }
}
