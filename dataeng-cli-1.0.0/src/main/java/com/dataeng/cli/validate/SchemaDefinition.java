package com.dataeng.cli.validate;

import java.util.Arrays;
import java.util.List;

/**
 * 记录 schema 定义：必填字段、类型/格式规则、阈值。
 */
public final class SchemaDefinition {

    /** 必填字段（空/空白视为缺失） */
    public static final List<String> REQUIRED_FIELDS =
            Arrays.asList("id", "title", "published", "authors", "summary");

    /** 字段 -> 期望类型（用于 schema 类型检查） */
    public static final List<String> STRING_FIELDS =
            Arrays.asList("id", "title", "published", "summary", "primary_category", "doi", "link", "updated");

    /** 数组字段（元素须为非空字符串） */
    public static final List<String> ARRAY_STRING_FIELDS =
            Arrays.asList("authors", "categories");

    /** 必填字段完整率阈值：低于则 FAIL */
    public static final double MIN_COMPLETENESS = 0.95;

    /** 重复率阈值：高于则 FAIL */
    public static final double MAX_DUPLICATE_RATE = 0.01;

    /** 记录超过 N 年未更新视为"过期"（WARN 级） */
    public static final int STALE_YEARS = 5;

    private SchemaDefinition() {
    }
}
