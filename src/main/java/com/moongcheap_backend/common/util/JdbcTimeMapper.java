package com.moongcheap_backend.common.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * PostgreSQL TIMESTAMPTZ 컬럼을 LocalDateTime으로 안전하게 매핑하기 위한 헬퍼.
 * postgresql-jdbc 42.6.0+ 에서 rs.getObject(name, LocalDateTime.class)는
 * TIMESTAMPTZ 컬럼에 대해 예외를 던진다 (lossy conversion 방지 목적).
 * OffsetDateTime으로 먼저 받은 뒤 서버 로컬 오프셋 기준 LocalDateTime으로 변환한다.
 */
public final class JdbcTimeMapper {

    private JdbcTimeMapper() {}

    public static LocalDateTime toLocalDateTime(ResultSet rs, String columnLabel) throws SQLException {
        OffsetDateTime odt = rs.getObject(columnLabel, OffsetDateTime.class);
        return odt == null ? null : odt.toLocalDateTime();
    }
}
