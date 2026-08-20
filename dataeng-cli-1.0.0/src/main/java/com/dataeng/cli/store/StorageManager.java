package com.dataeng.cli.store;

import com.dataeng.cli.exception.DataEngException;
import com.dataeng.cli.exception.ErrorCode;
import com.dataeng.cli.model.PaperRecord;
import com.dataeng.cli.util.DateUtil;
import com.dataeng.cli.util.JsonUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 存储管理：
 *  - 原始响应落盘：&lt;root&gt;/raw/&lt;source&gt;/raw-&lt;yyyyMMddHHmmss&gt;.xml|json
 *  - 清洗后记录落盘：&lt;root&gt;/processed/&lt;source&gt;/&lt;yyyy&gt;/&lt;MM&gt;/&lt;yyyyMMddHHmmss&gt;.json
 */
public class StorageManager {

    private final Path root;

    public StorageManager(String outputDir) {
        this.root = Paths.get(outputDir).toAbsolutePath();
    }

    public Path getRoot() {
        return root;
    }

    /** 保存原始响应（fetch 与 sync 均可调用）。 */
    public Path saveRaw(String source, String query, String rawBody) {
        String ext = source.equalsIgnoreCase("arxiv") ? ".xml" : ".json";
        Path file = root.resolve("raw").resolve(source)
                .resolve("raw-" + DateUtil.formatArxiv(DateUtil.nowUtc()) + ext);
        writeUtf8(file, rawBody);
        return file;
    }

    /** 保存记录列表为 JSON 数组文件（按年月分区）。 */
    public Path saveProcessed(String source, List<PaperRecord> records) {
        String now = DateUtil.formatArxiv(DateUtil.nowUtc());
        String yyyy = now.substring(0, 4);
        String mm = now.substring(4, 6);
        Path file = root.resolve("processed").resolve(source)
                .resolve(yyyy).resolve(mm)
                .resolve(now + ".json");
        writeUtf8(file, JsonUtil.toPrettyJson(records));
        return file;
    }

    /** 递归收集目录下所有 .json 文件（供 validate 使用），跳过 raw/ 原始响应目录。 */
    public static List<Path> listJsonFiles(Path dir) {
        List<Path> files = new ArrayList<>();
        if (dir == null || !Files.exists(dir)) {
            return files;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".json"))
                    .filter(p -> !isUnderRawDir(p))
                    .sorted()
                    .forEach(files::add);
        } catch (IOException e) {
            throw new DataEngException(ErrorCode.IO_ERROR, "扫描目录失败 " + dir + ": " + e.getMessage(), e);
        }
        return files;
    }

    /** 判断文件是否位于 raw/ 原始响应目录下（raw 与 processed 内容重复，validate 只应统计 processed）。 */
    private static boolean isUnderRawDir(Path p) {
        String s = p.normalize().toString().replace('\\', '/');
        return s.contains("/raw/") || s.startsWith("raw/");
    }

    private static void writeUtf8(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new DataEngException(ErrorCode.IO_ERROR, "写入文件失败 " + file + ": " + e.getMessage(), e);
        }
    }
}
