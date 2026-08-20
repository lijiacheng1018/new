package com.dataeng.cli.store;

import com.dataeng.cli.exception.DataEngException;
import com.dataeng.cli.exception.ErrorCode;
import com.dataeng.cli.util.DateUtil;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.Optional;

/**
 * SQLite 状态管理：
 *  1) watermark 表 —— 每数据源的时间游标（增量拉取起点）；
 *  2) seen_records 表 —— 已见记录 (source, id) -> content_hash，实现幂等去重。
 *
 * 同步方法保证单连接串行化（SQLite 单写者模型）。
 */
public class WatermarkManager implements AutoCloseable {

    private final Connection conn;

    public WatermarkManager(String dbPath) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new DataEngException(ErrorCode.STORE_ERROR, "缺少 sqlite-jdbc 驱动", e);
        }
        // 确保状态库所在目录存在（默认路径位于 <output>/.dataeng/ 下）
        try {
            Path parent = Paths.get(dbPath).toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new DataEngException(ErrorCode.STORE_ERROR, "创建状态库目录失败: " + e.getMessage(), e);
        }
        try {
            this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            this.conn.setAutoCommit(true);
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS watermark("
                        + "source TEXT PRIMARY KEY,"
                        + "value TEXT NOT NULL,"
                        + "updated_at TEXT NOT NULL)");
                st.execute("CREATE TABLE IF NOT EXISTS seen_records("
                        + "source TEXT NOT NULL,"
                        + "id TEXT NOT NULL,"
                        + "content_hash TEXT NOT NULL,"
                        + "updated_at TEXT NOT NULL,"
                        + "PRIMARY KEY(source, id))");
            }
        } catch (SQLException e) {
            throw new DataEngException(ErrorCode.STORE_ERROR, "打开状态库失败 " + dbPath + ": " + e.getMessage(), e);
        }
    }

    // ---------------- watermark ----------------

    public synchronized Optional<String> getWatermark(String source) {
        String sql = "SELECT value FROM watermark WHERE source = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, source);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataEngException(ErrorCode.STORE_ERROR, "读取 watermark 失败: " + e.getMessage(), e);
        }
    }

    public synchronized void setWatermark(String source, String value) {
        String sql = "INSERT OR REPLACE INTO watermark(source, value, updated_at) VALUES(?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, source);
            ps.setString(2, value);
            ps.setString(3, DateUtil.toIso(DateUtil.nowUtc()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataEngException(ErrorCode.STORE_ERROR, "写入 watermark 失败: " + e.getMessage(), e);
        }
    }

    // ---------------- seen_records（幂等去重） ----------------

    /** 记录判定：NEW 新记录 / UPDATED 内容变化 / SKIPPED 已见且未变化 */
    public synchronized Decision classify(String source, String id, String contentHash) {
        String sql = "SELECT content_hash FROM seen_records WHERE source = ? AND id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, source);
            ps.setString(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Decision.NEW;
                }
                String old = rs.getString(1);
                return old != null && old.equals(contentHash) ? Decision.SKIPPED : Decision.UPDATED;
            }
        } catch (SQLException e) {
            throw new DataEngException(ErrorCode.STORE_ERROR, "去重判定失败: " + e.getMessage(), e);
        }
    }

    public synchronized void upsertSeen(String source, String id, String contentHash) {
        String sql = "INSERT OR REPLACE INTO seen_records(source, id, content_hash, updated_at) VALUES(?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, source);
            ps.setString(2, id);
            ps.setString(3, contentHash);
            ps.setString(4, DateUtil.toIso(DateUtil.nowUtc()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataEngException(ErrorCode.STORE_ERROR, "写入去重记录失败: " + e.getMessage(), e);
        }
    }

    public synchronized int seenCount(String source) {
        String sql = "SELECT COUNT(*) FROM seen_records WHERE source = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, source);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataEngException(ErrorCode.STORE_ERROR, "统计已见记录失败: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            conn.close();
        } catch (SQLException e) {
            // 忽略关闭异常
        }
    }

    public enum Decision {
        NEW, UPDATED, SKIPPED
    }
}
