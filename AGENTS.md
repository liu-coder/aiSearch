# AI Search Codex 代码规则

## 交流规则

- 默认使用简体中文与用户交流。
- 代码、命令、文件路径、API 名称、类名、函数名、库名、报错原文和日志原文保留英文或原文。
- 阶段进度、结果总结、审核意见、风险提示和后续建议默认使用简体中文。

## 必读文档索引

在生成或修改代码前，按改动类型优先查看对应文档：

- 架构、模块边界、分层、在线/离线链路、配置、ES/Milvus：`docs/ARCHITECTURE.md`
- API、DTO、Endpoint、枚举和校验规则：`docs/API_SCHEMA.md`
- MySQL 表结构快照：`docs/DATABASE_SCHEMA.sql`
- 工具类、工具方法和基础设施复用规则：`docs/UTILS.md`
- 项目总体说明、启动和联调流程：`docs/README.md`

## 质量目标

- 生成或修改代码时按生产环境质量标准执行。
- 遵循现有架构、命名、包边界、依赖管理和测试风格。
- 避免硬编码、虚假数据、占位逻辑、TODO 式实现和只覆盖 happy path 的实现。
- 涉及用户输入、文件名、URL、对象存储 key、模型响应、网络请求、索引写入、鉴权、并发或持久化时，必须考虑空值、长度、非法输入、超时、重试、幂等和失败路径。
- 修改完成前运行与改动范围匹配的验证命令；无法运行时说明原因和剩余风险。

## 硬性架构规则

- API 层只做 HTTP 路由、参数校验、响应包装；业务流程委托 application 层。
- application/domain 层不得直接依赖具体外部客户端实现。
- `ai-search-common` 不得依赖任何具体服务模块。
- 对外 REST 响应使用 `ApiResponse<T>`。
- 新增 API 请求体使用 `@Valid`，DTO 使用 Jakarta Validation 表达边界。
- 新增模块必须登记到根 `pom.xml` 的 `<modules>`，包名遵循 `com.aisearch.<bounded-context>`。

## Schema 与索引规则

- 数据库真实演进必须新增 Flyway migration，禁止修改已发布迁移。
- 修改数据库 schema 时同步更新 `docs/DATABASE_SCHEMA.sql`、JPA 实体、Repository、API/调试视图和测试。
- 修改 ES/Milvus 字段时同步检查 `SearchDocumentMapper`、`docs/ARCHITECTURE.md` 和相关适配器测试。
- 重建索引前必须先按 `videoId` 清理旧 ES/Milvus 片段，避免重复残留。
- 新增或修改 API/DTO/枚举时同步更新 `docs/API_SCHEMA.md`。

## 工具能力规则

- 新增解析、规范化、字段映射、媒体处理、模型调用、限流、重试、鉴权、trace 或错误响应逻辑前，必须先查看 `docs/UTILS.md`。
- 优先复用已有工具能力；确需新增时，同步更新 `docs/UTILS.md`、本文件和 `.cursorrules`。
- 不要新增泛化的“大而全” `Utils` 类；优先使用职责明确的小工具类、领域服务或 infrastructure 客户端。
- worker 模型响应缓存统一走 `ModelResponseCache`；多实例生产优先启用 `RedisModelResponseCache`。
- 搜索响应缓存统一走 `SearchResponseCache`；生产启用 Redis 前必须设置合理 TTL 和 key 前缀。
- 查询理解模型增强优先输出 JSON 对象，必须保留规则解析兜底和 `key=value` 兼容。
- worker 阶段任务观测优先使用 `ai_search_worker_stage_task_total` / `ai_search_worker_stage_task_duration`。
- AI 钉钉告警公共配置优先放入 Nacos `ai-search-common.yaml`，钉钉 token 使用 `dingtalk_access_token` 或 `DINGTALK_ACCESS_TOKEN` 环境变量注入，告警链路保持异步和失败隔离。
- 工作流调试、重跑和索引维护接口上线时必须配置 `ai-search.security.api-key` 与 `ai-search.security.admin-role`。

## 安全与配置

- DashScope、MinIO、API Key 等密钥只能来自环境变量或外部配置，不能写入仓库。
- `DASHSCOPE_API_KEY` 用于 model-service 的视频理解、文本分析、ASR、OCR、Caption、rerank、多模态 embedding 以及默认 AI 告警分析；`DEEPSEEK_API_KEY` 可用于代码 review、CI 分析、提交信息相关工具，也可作为 AI 告警分析的 DeepSeek provider 密钥。
- 本地开发默认使用 deterministic 模型能力，保证测试稳定。
- 新增配置项时同步更新 `application.yml`、配置属性类、`docs/ARCHITECTURE.md` 和测试。
- 如配置 `ai-search.security.api-key`，`/api/**` 请求必须携带 `X-AI-Search-Api-Key`。

## 本地部署规则

- 本机中间件统一使用 `E:\workspace\docker\docker-compose.yml` 启动：

```powershell
docker compose --progress plain -f E:\workspace\docker\docker-compose.yml up -d
```

- 关键端口：Gateway `18080`，Search `18081`，Video `18082`，Worker `18083`，Model `18084`，MySQL `3306`，Redis `6379`，MinIO `9000/9001`，RocketMQ namesrv `9876`，Milvus `19530`，Nacos `8848`，Prometheus `9090`，Grafana `3000`。
- 本地 Docker 默认账号：MySQL `root/root`，Redis password `redis`，MinIO `minioadmin/minioadmin`，Grafana `admin/admin`。
- 使用 `E:\workspace\docker` 的 Redis 时，Spring 配置必须包含 `spring.data.redis.password=redis`。
- FFmpeg 本地开发由 `E:\workspace\docker` 的 `ffmpeg-tools` 容器提供；worker 默认使用 `E:\workspace\docker\bin\ffmpeg-docker.cmd` 和 `E:\workspace\docker\bin\ffprobe-docker.cmd`，工作目录保持在 `E:\workspace\docker\data\ai-search-worker`。
- 多服务共用 `ai_search` schema 且各服务使用独立 Flyway history 表时，`baseline-on-migrate: true` 必须搭配 `baseline-version: 0`，避免某服务先建表后另一个服务跳过自身 `V1`。
- 本地 Elasticsearch 镜像如因 `docker.elastic.co` 拉取失败不可用，可临时用 `AI_SEARCH_SEARCH_ELASTICSEARCH_ENABLED=false` 和 `AI_SEARCH_WORKER_INITIALIZE_INDEX_ON_STARTUP=false` 启动检索/worker；完整文本召回和 ES 索引链路仍需恢复 Elasticsearch。
- `E:\workspace\docker` 中 MinIO 健康检查应使用 `mc ready local`，RocketMQ namesrv 健康检查应使用容器内完整 `mqadmin` 路径，避免镜像缺少 `curl` 或 PATH 未包含 RocketMQ bin。

## 测试与验证

- 首选仓库级验证：`mvn test`。
- 只改单模块时可运行：`mvn -pl <module> test`。
- 修改公共契约时，优先运行受影响模块测试。
- 修改 schema、索引字段、状态机、召回融合、模型调用、鉴权或上传链路时必须补充或更新测试。
- 修改 hook 或提交模板时运行：

```powershell
D:\software\work\git\Git\bin\bash.exe scripts/test-ai-commit-hook.sh
```

## Git 与提交模板

- 提交模板：`.gitmessage`
- 当前占位符：`__AI_COMMIT_MESSAGE__`
- Husky hook 位于 `.husky`
- 保留占位符时才自动生成提交信息。
- 不要覆盖用户手写提交信息。
