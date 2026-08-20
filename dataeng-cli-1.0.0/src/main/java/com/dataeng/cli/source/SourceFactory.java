package com.dataeng.cli.source;

import com.dataeng.cli.exception.DataEngException;
import com.dataeng.cli.exception.ErrorCode;
import com.dataeng.cli.http.HttpExecutor;

/**
 * 数据源工厂：按名称创建数据源，支持 --mock 强制切换。
 */
public final class SourceFactory {

    private SourceFactory() {
    }

    public static DataSource create(String name, boolean forceMock) {
        if (forceMock) {
            return new MockSource(false);
        }
        if (name == null || name.trim().isEmpty()) {
            throw new DataEngException(ErrorCode.PARAM_MISSING, "--source 不能为空（支持 arxiv | mock）");
        }
        String n = name.trim().toLowerCase();
        switch (n) {
            case "arxiv":
                return new ArxivSource(new HttpExecutor());
            case "mock":
                return new MockSource(false);
            default:
                throw new DataEngException(ErrorCode.UNSUPPORTED_SOURCE,
                        "不支持的数据源: '" + name + "'（支持 arxiv | mock）");
        }
    }
}
