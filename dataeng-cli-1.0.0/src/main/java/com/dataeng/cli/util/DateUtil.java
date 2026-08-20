package com.dataeng.cli.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * 日期工具：兼容多种输入格式，输出统一 ISO 字符串。
 * 支持的解析格式：
 *   yyyy-MM-dd
 *   yyyy-MM-dd HH:mm:ss
 *   yyyy-MM-ddTHH:mm:ss
 *   yyyy-MM-ddTHH:mm:ssZ / +08:00 等带时区
 *   yyyyMMddHHmmss（arXiv 区间格式）
 */
public final class DateUtil {

    public static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** arXiv submittedDate 区间格式: YYYYMMDDHHMM */
    public static final DateTimeFormatter ARXIV = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private static final DateTimeFormatter[] PATTERNS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            ARXIV,
            ISO,
            DateTimeFormatter.ISO_DATE
    };

    private DateUtil() {
    }

    /** 当前 UTC 时间 */
    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public static String toIso(LocalDateTime t) {
        return t == null ? null : t.format(ISO);
    }

    public static String formatArxiv(LocalDateTime t) {
        return t == null ? null : t.format(ARXIV);
    }

    /**
     * 宽松解析为 LocalDateTime（统一视为 UTC）。
     * 解析失败抛 IllegalArgumentException。
     */
    public static LocalDateTime parse(String s) {
        if (s == null) {
            throw new IllegalArgumentException("日期为空");
        }
        String v = s.trim();
        if (v.isEmpty()) {
            throw new IllegalArgumentException("日期为空");
        }
        // 带时区偏移（Z / +08:00）
        if (v.endsWith("Z") || v.matches(".*[+-]\\d{2}:?\\d{2}$")) {
            try {
                return OffsetDateTime.parse(v).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                // 继续尝试
            }
        }
        for (DateTimeFormatter f : PATTERNS) {
            try {
                return LocalDateTime.parse(v, f);
            } catch (DateTimeParseException ignored) {
                // 尝试下一种
            }
        }
        // 仅日期
        try {
            return LocalDate.parse(v).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            // 继续
        }
        throw new IllegalArgumentException("无法解析日期: " + s);
    }

    /** 是否可解析 */
    public static boolean isParseable(String s) {
        try {
            parse(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 距今是否超过 staleYears 年（用于"过期记录"检查） */
    public static boolean isStale(String iso, int staleYears) {
        LocalDateTime t;
        try {
            t = parse(iso);
        } catch (Exception e) {
            return false;
        }
        return t.isBefore(nowUtc().minusYears(staleYears));
    }

    /** 格式化为终端可读: 2024-01-15 08:30 */
    public static String human(LocalDateTime t) {
        return t == null ? "-" : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).format(t);
    }
}
