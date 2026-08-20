# dataeng-cli — 科研公共数据源采集与集成 CLI 工具

> 数据工程师笔试题实现。一个 Java CLI 工具，用于从科研公共数据源（arXiv）采集文献数据，
> 支持**原始数据拉取（fetch）**、**watermark 增量同步（sync）**、**数据质量校验（validate）**，
> 并附带**定时调度演示（schedule）**、**离线 Mock 数据源**、Dockerfile / Makefile、结构化日志等加分项。

---

## 1. 项目简介

dataeng-cli 覆盖"采集 → 增量同步 → 清洗校验"的完整数据链路：

| 命令 | 作用 | 对应评分点 |
| --- | --- | --- |
| `fetch` | 按关键词/ID 拉取原始数据，原始响应落盘 | 采集 |
| `sync` | 基于本地 watermark 增量拉取，按唯一 ID 幂等去重 | 增量同步 |
| `validate` | 数据质量校验（完整率/重复率/schema/过期），输出含 pass/comment 的报告 | 质量校验 |
| `demo` | **一键演示**：mock 源跑通 fetch → sync(幂等) → validate 全链路 | 快速上手 |

技术栈：**Java 8 + picocli（CLI）+ OkHttp（HTTP 限流/重试）+ Jackson（JSON）+ sqlite-jdbc（watermark/去重）+ logback（结构化日志）+ JUnit 5**。

---

## 2. 技术选型

### 2.1 数据源：为什么选 arXiv API？

笔试题要求从 PubChem PUG REST / UniProt REST API / NCBI E-utilities / arXiv API 中任选其一，对比结论：

| 对比项 | PubChem / UniProt / NCBI | **arXiv API（选择）** |
| --- | --- | --- |
| API Key | 部分需要（NCBI 建议、PubChem 限流严格） | **完全免费，无需任何 Key** |
| 认证成本 | 需要注册/配额申请 | 零成本，开箱即用 |
| 时间区间查询 | 语义较弱，增量难度大 | **原生支持 `submittedDate:[from TO to]`**，天然适配 watermark 增量同步 |
| 响应格式 | 多样（JSON/XML/ASN.1） | 统一 Atom XML，结构简单 |
| 限流策略 | 严格（多为 3 次/秒） | 宽松但仍需限流，适合演示"限流 + 指数退避重试" |

因此选择 **arXiv（export.arxiv.org）** 作为真实数据源；同时实现 **Mock 数据源**（`--mock`），
保证无网络环境也能进行**可复现**的完整演示与自动化测试。

### 2.2 其余组件选型

- **picocli**：Java 8 兼容、子命令/`--help`/参数校验开箱即用；
- **OkHttp**：连接池、超时控制；配合自研 `RateLimiter`（固定速率令牌）与**指数退避重试**（含 429 `Retry-After` 尊重）；
- **sqlite-jdbc**：单文件本地状态库，存储 `watermark` 游标 + `seen_records` 去重表（含内容哈希，可识别"更新"）；
- **Jackson**：统一 JSON 序列化/反序列化；
- **logback**：结构化日志统一输出到 **stderr**，不污染 stdout 的结果/JSON 输出；
- **Maven shade**：打包单文件可执行 fat jar。

---

## 3. 环境变量配置

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DATAENG_TIMEOUT_MS` | `20000` | HTTP 连接/读取超时（毫秒） |
| `DATAENG_MAX_RETRIES` | `3` | 网络失败 / 5xx / 429 的重试次数 |
| `DATAENG_RATE_PER_SECOND` | `5` | 每秒最大请求数（限流） |
| `DATAENG_USER_AGENT` | `dataeng-cli/1.0.0 ...` | HTTP User-Agent |
| `DATAENG_LOG_LEVEL` | `INFO` | 日志级别 DEBUG/INFO/WARN/ERROR |

示例：

```bash
export DATAENG_RATE_PER_SECOND=3
export DATAENG_MAX_RETRIES=5
```

---

## 4. 安装 / 构建

### 4.1 环境要求

- JDK 8+
- Maven 3.6+（或直接使用下方 `java` 直调方式）

### 4.2 构建

```bash
# 方式一：mvn
mvn -q package -DskipTests        # 仅打包
mvn -q test                        # 运行单元测试

# 方式二：宿主机 mvn 脚本异常时，用 java 直调 Launcher（本机已验证）
"$JAVA_HOME/bin/java" -classpath "$MAVEN_HOME/boot/plexus-classworlds-*.jar" \
  "-Dclassworlds.conf=$MAVEN_HOME/bin/m2.conf" \
  "-Dmaven.home=$MAVEN_HOME" \
  "-Dmaven.multiModuleProjectDirectory=." \
  org.codehaus.plexus.classworlds.launcher.Launcher -B package

# 方式三：Makefile
make build        # 打包
make test         # 测试
make demo         # 打包 + 一键全链路演示

# 方式四：Docker（加分项）
make docker       # docker build -t dataeng-cli:1.0.0 .
docker run --rm dataeng-cli:1.0.0 --help
```

产物：`target/dataeng-cli.jar`（可执行 fat jar）。

### 4.3 运行入口

```bash
# 直接运行 fat jar
java -jar target/dataeng-cli.jar --help

# 或使用启动脚本（自动定位 jar）
bin/dataeng-cli --help        # Unix / Git Bash
bin\dataeng-cli.cmd --help    # Windows CMD

# 或 Docker
docker run --rm -v "$PWD/data:/data" dataeng-cli:1.0.0 fetch --source arxiv --query flink --output /data
```

---

## 5. 快速开始：一条命令跑通全链路



```bash
java -jar target/dataeng-cli.jar demo
# Windows 也可: bin\dataeng-cli.cmd demo
```

内置 `demo` 子命令自动依次执行（全程离线 mock，结果可复现）：

1. `fetch` —— 拉取原始数据并落盘（`demo-data/raw/`）
2. `sync #1` —— 首次增量同步（`demo-data/processed/` + SQLite watermark）
3. `sync #2` —— 同窗口重跑，**应全部"跳过"**，验证幂等去重
4. `validate` —— 数据质量校验（PASS），JSON 报告落盘 `demo-data/quality-report.json`
5. 打印产出目录结构

加 `--schedule` 可追加定时调度演示：`java -jar target/dataeng-cli.jar demo --schedule`

---

## 6. CLI 命令说明

### 6.1 全局

```bash
dataeng-cli --help
dataeng-cli --version
dataeng-cli fetch --help      # 每个子命令都支持 --help
```

**退出码约定**：

| 退出码 | 含义 |
| --- | --- |
| 0 | 成功 |
| 1 | 未预期错误 |
| 2 | 参数缺失/不合法、不支持的数据源 |
| 3 | 数据源不可达 / 网络失败（重试后） |
| 4 | 查询无结果 |
| 5 | 响应格式异常 / 解析失败 |
| 6 | 本地文件读写失败 |
| 7 | 本地状态库（watermark/去重）失败 |
| 8 | 校验未通过（存在 ERROR 级问题） |

### 6.2 `fetch` —— 按关键词/ID 拉取原始数据

```
用法: dataeng-cli fetch --source <arxiv|mock> --query <关键词> --output <目录> [选项]
选项:
  --source <name>      数据源: arxiv | mock（必填）
  --query <query>      查询关键词或 ID（必填）
  --output <dir>       输出目录（默认 data）
  --max-results <n>    最大返回条数（默认 10）
  --since <date>       起始时间 yyyy-MM-dd 或 ISO（可选）
  --until <date>       结束时间 yyyy-MM-dd 或 ISO（可选）
  --mock               强制使用离线模拟数据源
```

行为：调用数据源 API（限流 + 超时 + 指数退避重试），**原始响应落盘**
`<output>/raw/<source>/raw-<时间戳>.xml|json`，解析后的记录写入
`<output>/processed/<source>/<yyyy>/<MM>/<时间戳>.json`。

错误处理（均有明确报错与退出码）：数据源不可达（3）、查询无结果（4）、参数缺失（2）、响应格式异常（5）。

### 6.3 `sync` —— 基于 watermark 的增量同步（幂等）

```
用法: dataeng-cli sync --source <arxiv|mock> [--since <watermark>] [--output <dir>] [选项]
选项:
  --source <name>      数据源: arxiv | mock（必填）
  --since <watermark>  显式指定增量起点（可选；默认读取 SQLite 中的 watermark，首次为近 30 天）
  --output <dir>       输出目录（默认 data）
  --max-results <n>    单次增量窗口最大拉取条数（默认 100）
  --state-db <path>    SQLite 状态库路径（默认 <output>/.dataeng/state.db）
  --mock               强制使用离线模拟数据源
```

增量机制：
1. **watermark 游标**：`since` 优先级为 `--since` > SQLite 已存 watermark > 近 30 天；
2. 拉取 `[since, now]` 时间窗口（arXiv 用 `submittedDate` 区间，无需关键词）；
3. **幂等去重**：按 `(source, id)` 判定——未见过 = **新增**；已见且内容哈希相同 = **跳过**；已见但内容变化 = **更新**；
4. **watermark 推进**到窗口内最大发布时间，避免下次重复拉取。

输出：`新增 / 更新 / 跳过` 三组计数 + 已见总数，验证幂等（重跑应全部"跳过"）。

### 6.4 `validate` —— 数据质量校验

```
用法: dataeng-cli validate <data_dir> [--format text|json] [--output result.json]
```

校验项：
1. **必填字段完整率**（`id / title / published / authors / summary`，阈值 95%）；
2. **唯一 ID 重复率**（阈值 1%）；
3. **schema 字段类型/格式**（字符串非空、数组元素非空、日期可解析）；
4. **过期记录**（published 超过 5 年，WARN 级）。

输出 JSON 报告结构（含 `pass` / `comment`）：

```json
{
  "pass": true,
  "comment": "通过：记录数 8，必填字段完整率最低 100.0%，重复率 0.0%，schema 类型/格式错误 0，过期记录 0",
  "totalRecords": 8,
  "duplicateCount": 0,
  "duplicateRate": 0.0,
  "fieldCompleteness": { "id": 1.0, "title": 1.0, "published": 1.0, "authors": 1.0, "summary": 1.0 },
  "checks": ["必填字段完整率", "唯一 ID 重复率", "schema 字段类型/格式", "过期记录"],
  "issues": []
}
```

兼容性：目录不存在 / 空目录 / 非法 JSON 文件均不崩溃，给出明确 comment 与 WARN 级问题。
退出码：0 = 通过；8 = 未通过（存在 ERROR 级问题）。

### 6.5 `schedule` —— 定时调度演示（加分项）

```
用法: dataeng-cli schedule --source <arxiv|mock> [--interval <秒>] [--max-runs <次数>] ...
```

周期性执行增量 sync，输出每次运行的窗口与 `新增/更新/跳过` 计数，直观展示 watermark 游标推进。
生产环境可将该循环交给 cron / 工作流调度器（如 Airflow、DolphinScheduler）调用 `sync`。

---

## 7. 示例输入输出

### 7.1 离线全流程（推荐先跑这个）

```bash
java -jar target/dataeng-cli.jar demo
```

一键依次演示：`fetch(mock)` → `sync #1` → `sync #2(同窗口幂等，应全部跳过)` → `validate(text)` → `validate(json 落盘)` → 目录结构。

预期输出节选：

```
################ 3) sync #2 —— 同窗口幂等重跑（应全部跳过） ################
== sync mock ==
  增量窗口  : 2026-08-10T00:00:00 -> 2026-08-20T08:4x:xx
  拉取条数  : 3
  新增      : 0
  更新      : 0
  跳过(去重): 3
  已见总数  : 8
  耗时      : xxx ms
  状态库    : demo-data/.dataeng/state.db
  -> 幂等验证通过：窗口内记录全部命中去重
```

### 7.2 真实 arXiv 拉取（需要网络）

```bash
# fetch：关键词检索
java -jar target/dataeng-cli.jar fetch --source arxiv --query "Flink interval join" \
  --output data --max-results 5

# sync：近 30 天全量增量（无关键词，仅时间窗口；默认最多 100 条，可加大 --max-results）
java -jar target/dataeng-cli.jar sync --source arxiv --output data --max-results 200

# validate
java -jar target/dataeng-cli.jar validate data/processed --format json --output data/quality-report.json
```

### 7.3 错误示例

```bash
# 参数缺失 → 退出码 2
java -jar target/dataeng-cli.jar fetch --source arxiv
# 错误[PARAM_MISSING] (参数缺失或不合法): --query 不能为空

# 数据源不可达（把域名改错）→ 退出码 3
# 错误[SOURCE_UNREACHABLE] (数据源不可达或请求失败): ...

# 查询无结果 → 退出码 4
java -jar target/dataeng-cli.jar fetch --source mock --query "zzz_no_such_zzz" --mock
# 错误[NO_RESULTS] (查询无结果): 查询 ... 没有返回任何结果
```

---

## 8. 已实现功能清单

- [x] `fetch`：关键词检索、原始响应落盘、处理 JSON 按年月分区
- [x] `sync`：watermark 游标（SQLite）、唯一 ID 幂等去重、新增/更新/跳过计数、内容哈希识别更新
- [x] `validate`：必填字段完整率 / 重复率 / schema 类型格式 / 过期记录，JSON 报告含 pass/comment
- [x] 限流（固定速率）+ 超时 + 指数退避重试 + 429 Retry-After
- [x] 清晰错误体系（错误码 + 稳定退出码），空目录/非法文件健壮处理
- [x] `--help` 全部子命令支持
- [x] 加分项：`--mock` 离线模拟源、`validate --output result.json`、`schedule` 定时调度演示、
      Dockerfile / Makefile、logback 结构化日志、日期兼容多格式解析
- [x] 单元测试 5 个测试类（Mock 确定性、arXiv XML 解析、watermark/去重、校验器、同步幂等）
- [x] 内置 `demo` 子命令一键全链路演示（替代 shell 脚本，跨平台一致）

## 9. 已知问题与限制

1. **arXiv 同步上限**：`sync --source arxiv` 无关键词时窗口为近 30 天全量论文（约数万条），
   受 `--max-results` 上限约束，默认 100 条只是窗口内采样；完整增量请调大 `--max-results` 或缩短 `--since` 窗口。
2. **watermark 单调推进**：watermark 取"窗口内最大发布时间"，若某记录发布时间晚于其入库时间，
   极端场景下可能被后续窗口重复纳入；已由 seen_records 去重兜底，不会重复入库。
3. **arXiv 分页**：已实现 100/页的分页拉取；超大窗口建议拆分为更细粒度调度。
4. **并发**：状态库为单连接串行写，多进程同时 sync 同一状态库未做文件锁（生产可换 PostgreSQL/MySQL）。
5. **Windows 控制台编码**：JDK 8 默认按系统编码（GBK）输出中文。`bin/` 启动脚本已强制 `-Dfile.encoding=UTF-8`；直接 `java -jar` 时如遇中文乱码，请追加 `-Dfile.encoding=UTF-8`。

## 10. 项目结构

```
dataeng-cli/
├── pom.xml                     # Maven 构建（Java 8，shade fat jar）
├── Makefile / Dockerfile       # 构建辅助 / 容器化
├── bin/
│   ├── dataeng-cli             # Unix 启动脚本
│   └── dataeng-cli.cmd         # Windows 启动脚本
├── src/main/java/com/dataeng/cli/
│   ├── cli/                    # CliApp 入口 + fetch / sync / validate / schedule / demo 命令
│   ├── http/                   # HttpExecutor(重试退避) + RateLimiter
│   ├── model/                  # PaperRecord / FetchResult / SyncResult / QualityReport
│   ├── service/                # SyncService（增量同步编排）
│   ├── source/                 # DataSource 接口 + ArxivSource + MockSource + SourceFactory
│   ├── store/                  # StorageManager + WatermarkManager(SQLite 去重)
│   ├── util/                   # DateUtil / JsonUtil / Config
│   ├── validate/               # DataValidator + SchemaDefinition
│   └── exception/              # ErrorCode + DataEngException
├── src/main/resources/logback.xml   # 结构化日志(stderr)
└── src/test/java/...           # 单元测试
```

## 11. 演示视频与联系方式

- **演示视频**：见提交说明附件 / 网盘链接（包含：安装构建、三命令完整演示、项目结构讲解）。
- **姓名**：____
- **联系方式**：____
