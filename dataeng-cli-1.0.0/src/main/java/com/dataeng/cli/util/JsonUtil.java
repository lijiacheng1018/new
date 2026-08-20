package com.dataeng.cli.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dataeng.cli.exception.DataEngException;
import com.dataeng.cli.exception.ErrorCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON 工具：统一 ObjectMapper 配置。
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private JsonUtil() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String toJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new DataEngException(ErrorCode.PARSE_ERROR, "JSON 序列化失败: " + e.getMessage(), e);
        }
    }

    public static String toPrettyJson(Object o) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new DataEngException(ErrorCode.PARSE_ERROR, "JSON 序列化失败: " + e.getMessage(), e);
        }
    }

    public static JsonNode readTree(byte[] bytes) {
        try {
            return MAPPER.readTree(bytes);
        } catch (IOException e) {
            throw new DataEngException(ErrorCode.PARSE_ERROR, "JSON 解析失败: " + e.getMessage(), e);
        }
    }

    public static void writeToFile(Path file, Object o) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(file, toPrettyJson(o).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new DataEngException(ErrorCode.IO_ERROR, "写入文件失败 " + file + ": " + e.getMessage(), e);
        }
    }
}
