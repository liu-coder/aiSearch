# 工具能力沉淀

本文档沉淀当前项目中可复用的工具类、工具方法和基础设施能力。项目目前没有统一的 `utils` 包，工具能力分散在各模块的 DTO、application、infrastructure 和 common web 组件中。新增代码时应优先复用这些现有能力，避免重复实现相似逻辑。

## 使用原则

- 优先复用已有工具能力，再考虑新增工具类。
- 工具能力应放在最贴近业务边界的位置；只有跨模块稳定复用时才沉淀到 `ai-search-common`。
- 不要创建泛化的“大而全” `Utils` 类；优先使用具名清晰的小类或领域服务。
- 涉及解析、序列化、字段映射、限流、重试、幂等、鉴权和错误响应时，先检查本文档。

## 公共 Web 基础设施

### `TraceIdFilter`

位置：`ai-search-common/src/main/java/com/aisearch/common/web/TraceIdFilter.java`

职责：

- 读取或生成 `X-Trace-Id`。
- 将 traceId 写入 `MDC`，便于日志串联。
- 将 traceId 写回响应头。

使用规则：

- 新增 HTTP 服务模块时复用 common 中的过滤器。
- 排查上传、搜索、worker 和模型调用链路时优先查看 `X-Trace-Id`。
- 不要在业务代码中重复生成独立 traceId。

### `GlobalExceptionHandler`

位置：`ai-search-common/src/main/java/com/aisearch/common/web/GlobalExceptionHandler.java`

职责：

- 将参数校验错误、业务状态错误、下游调用错误和未知异常转换为统一 `ApiResponse`。
- `MethodArgumentNotValidException` 和 `ConstraintViolationException` 返回 `400`。
- `IllegalStateException` 返回 `409`。
- `RestClientException` 返回 `502`。
- 未知异常返回 `500`。

使用规则：

- 新增接口时不要在 Controller 中手写重复错误响应。
- 可预期的业务输入问题优先抛 `IllegalArgumentException`。
- 状态冲突、资源不存在或流程状态异常优先抛 `IllegalStateException`。

### `ApiKeyAuthFilter`

位置：`ai-search-common/src/main/java/com/aisearch/common/web/ApiKeyAuthFilter.java`

职责：

- 当 `ai-search.security.api-key` 非空时保护 `/api/**`。
- 读取请求头 `X-AI-Search-Api-Key`。
- 对工作流调试和重跑接口追加管理员角色校验，读取 `X-AI-Search-Roles` 并匹配 `ai-search.security.admin-role`。
- 未配置 key 时保持本地调试兼容。

使用规则：

- 新增 `/api/**` 接口默认受该过滤器保护。
- 不要在单个 Controller 中重复实现 API Key 校验。
- `slice-plan`、`artifacts`、`rerun`、`rebuild-index`、`delete-index` 等内部接口上线前必须配置管理员角色隔离。
- 如需新增豁免路径，必须评估调试便利性与生产风险。

### AI 告警组件

位置：`ai-search-common/src/main/java/com/aisearch/alert`

职责：

- `AiAlertService` 作为异步编排入口，捕获服务异常后执行去重、AI 分析和钉钉推送。
- `AlertDeduplicator` 优先使用 Redis 做 5 分钟窗口去重和单服务限流，Redis 不可用时降级到进程内窗口，避免影响业务。
- `AiExceptionAnalyzer` 调用 DashScope 或 DeepSeek 的 OpenAI 兼容 Chat Completions 生成根因、影响范围、修复建议和严重级别；未配置 API Key 或调用失败时返回降级分析。
- `DingTalkNotifier` 使用 `dingtalk-access-token` 组装钉钉机器人 webhook，并按需使用 `dingtalk-secret` 加签。
- `AiAlertLogbackAppender` 捕获 ERROR 级别且带异常的日志，`GlobalExceptionHandler` 捕获 502/500 响应异常，两路触发依赖去重避免重复告警。

使用规则：

- 公共配置优先放入 Nacos `ai-search-common.yaml`，本地示例位于 `deploy/nacos/ai-search-common.yaml`。
- 默认 `analyzer-provider=dashscope`，复用 `DASHSCOPE_API_KEY`、DashScope `chat/completions` endpoint 和 `qwen-plus`；如切换 DeepSeek，设置 `analyzer-provider=deepseek`、`analyzer-api-key=${DEEPSEEK_API_KEY:}`、`analyzer-endpoint=https://api.deepseek.com/chat/completions`、`analyzer-model=deepseek-chat`。
- 钉钉机器人 token 使用环境变量 `DINGTALK_ACCESS_TOKEN` 或本机兼容变量 `dingtalk_access_token` 注入，不要提交真实 token。
- 新增会产生大量预期异常的路径时，把对应异常类加入 `ai-search.alert.ignored-exceptions`。
- 告警链路必须保持异步和失败隔离，不要在业务线程中同步等待 AI 分析或钉钉推送。
- 本地用 `spring-boot:run` 单独启动服务前，如改过 common，先运行 `mvn -pl ai-search-common install -DskipTests`，避免服务加载旧的 common 快照导致 Logback appender 类找不到。
- `ai-search-common` 不应引入 `spring-boot-starter-data-redis`，否则会把 Redis 自动配置和健康检查传递给不需要 Redis 的服务；需要 Redis 类型时只依赖 `spring-data-redis`。

### `SearchResponseCache`

位置：

- `ai-search-search-service/src/main/java/com/aisearch/search/application/SearchResponseCache.java`
- `ai-search-search-service/src/main/java/com/aisearch/search/infrastructure/cache/RedisSearchResponseCache.java`
- `ai-search-search-service/src/main/java/com/aisearch/search/infrastructure/cache/NoOpSearchResponseCache.java`

职责：

- 抽象搜索响应缓存能力，缓存 key 基于 `text`、`imageUrl`、`topK`、`withAnalysis` 归一化后计算 SHA-256。
- 默认 `NoOpSearchResponseCache` 不缓存，保证本地行为稳定。
- 配置 `ai-search.search.cache.enabled=true` 后启用 Redis 缓存，读写失败不阻断主搜索链路。
- 命中缓存时刷新 `requestId`、`latencyMs`、`generatedAt`，避免对外复用旧请求元数据。

使用规则：

- 在线搜索缓存必须通过 `SearchResponseCache`，不要在 Controller 或召回适配器中新增旁路缓存。
- 修改 `SearchRequest` 或 `SearchResponse` 字段时同步检查缓存 key 与 JSON 序列化兼容性。
- 启用分析结果缓存时需确认 `withAnalysis` 已纳入 key，避免分析开关串结果。

## DTO 规范化工具方法

### `SearchRequest.normalizedTopK()`

位置：`ai-search-common/src/main/java/com/aisearch/common/search/SearchRequest.java`

职责：

- `topK` 未传时返回默认值 `10`。
- 与 `@Min(1)`、`@Max(100)` 校验配合使用。

使用规则：

- 搜索链路中不要直接读取 nullable 的 `topK` 做计算。
- 排序、召回扩展和分页逻辑统一使用 `normalizedTopK()`。

### `SearchRequest.needsAnalysis()`

位置：`ai-search-common/src/main/java/com/aisearch/common/search/SearchRequest.java`

职责：

- `withAnalysis` 未传时默认返回 `true`。

使用规则：

- 是否生成辩证分析统一走该方法。
- 不要在搜索用例里重复写 `withAnalysis == null || withAnalysis`。

### `EmbeddingRequest.normalizedSourceType()`

位置：`ai-search-model-service/src/main/java/com/aisearch/model/application/EmbeddingRequest.java`

职责：

- `sourceType` 为空时按 `text` 处理。

使用规则：

- embedding provider 构造模型请求或确定性向量 key 时统一使用该方法。
- 新增 `image`、`video-frame` 等来源时保持向后兼容。

### `RerankRequest.normalizedTopN()`

位置：`ai-search-model-service/src/main/java/com/aisearch/model/application/RerankRequest.java`

职责：

- `topN` 未传或小于等于 0 时返回全部候选数量。
- 返回值不超过 `documents.size()`。

使用规则：

- 模型精排请求不要直接信任原始 `topN`。
- 新增 rerank provider 时统一使用该方法。

## 搜索映射与查询理解

### `SearchDocumentMapper`

位置：`ai-search-search-service/src/main/java/com/aisearch/search/infrastructure/SearchDocumentMapper.java`

职责：

- 将 Elasticsearch/Milvus 返回的松散 `Map<String,Object>` 映射为 `CandidateSegment`。
- 字段别名兼容 camelCase 与 snake_case：
  - `videoId` / `video_id`
  - `segmentId` / `segment_id` / `id`
  - `title` / `videoTitle` / `video_title`
  - `startTimeMs` / `start_time_ms` / `startMs`
  - `endTimeMs` / `end_time_ms` / `endMs`

使用规则：

- 新增召回适配器时优先复用该 mapper。
- 修改 ES/Milvus 字段名时必须同步检查该 mapper。
- 不要在多个 adapter 中复制字段别名解析逻辑。

### `QueryUnderstandingService` 内部解析能力

位置：`ai-search-search-service/src/main/java/com/aisearch/search/application/QueryUnderstandingService.java`

职责：

- 轻量 token 化：去除首尾空白，合并连续空白。
- 结构化过滤提取：`tag:`、`author:`、`type:`。
- 日期过滤提取：`YYYY-MM-DD 到 YYYY-MM-DD`、`20xx年`。
- 意图推断：图片相似、混合证据、精确实体、语义搜索。
- `semanticQuery` 归一化：移除过滤语法和“找/搜索/查询/检索/帮我找”等操作词。
- 模型查询理解结果解析：`rewrite=`、`intent=`、`person=`、`scene=`、`startDate=`、`endDate=`。
- 模型查询理解结果解析也支持 JSON 对象：`rewrite`、`intent`、`person`、`scene`、`startDate`、`endDate`、`sourceWeights`。

使用规则：

- 新增搜索过滤语法时在该服务集中扩展。
- 不要在 Controller 或 RecallAdapter 中重复解析查询文本。
- 查询理解失败应降级到规则结果，不能阻断搜索主链路。
- 生产模型提示应要求“只输出 JSON 对象”，但解析层必须兼容旧的 `key=value` 文本格式。

## Worker 媒体与片段工具

### `TimedTextAligner`

位置：`ai-search-worker-service/src/main/java/com/aisearch/worker/application/TimedTextAligner.java`

职责：

- 将带时间戳的 ASR 句子对齐到视频片段时间范围。
- 仅拼接与目标片段时间有重叠的 ASR 文本。
- ASR 分段为空时返回 fallback 文本。

使用规则：

- 构造片段级 `ASR_TEXT` 或 `INDEX_TEXT` 时优先使用该工具。
- 不要把整段视频 ASR 文本直接灌入每个片段的 embedding。

### `MediaProcessingStrategy`

位置：`ai-search-worker-service/src/main/java/com/aisearch/worker/application/MediaProcessingStrategy.java`

职责：

- 根据视频时长、文件大小和内容边界生成 `MediaProcessingPlan`。
- 支持短视频密集切片、中等视频均衡切片、长视频稀疏切片。
- 支持大文件限制关键帧数量。
- 支持内容感知边界：镜头变化和静音点。
- 生成稳定的 `segmentId` 与 `keyFrameTimeMs`。

使用规则：

- 新增切片策略时在该类集中扩展。
- 不要在阶段处理器中手写切片边界。
- 调整策略配置时同步测试 `MediaProcessingStrategyTest`。

### `MediaProcessingService`

位置：`ai-search-worker-service/src/main/java/com/aisearch/worker/application/MediaProcessingService.java`

职责：

- 下载或复用原始视频本地工作文件。
- 使用 `ffprobe` 探测视频时长。
- 使用 FFmpeg 抽取音频和关键帧。
- 检测镜头变化和静音边界。
- 上传处理产物到 MinIO processed bucket。
- 清洗本地文件名中的非法字符。

使用规则：

- 媒体命令执行、超时处理和输出解析统一放在该服务中。
- 业务阶段不要直接启动 FFmpeg/ffprobe 进程。
- 本地文件名必须经过 `safeFileName` 类似规则处理。

### `DefaultStageProcessor` 内部序列化与降级工具

位置：`ai-search-worker-service/src/main/java/com/aisearch/worker/application/DefaultStageProcessor.java`

职责：

- `serializeFrames` / `parseFrames`：处理 `FRAME_MANIFEST`。
- `serializeAsrSegments` / `parseAsrSegments`：处理 `ASR_SEGMENTS`。
- `segmentText`：从多行 `segmentId: text` 中提取片段文本。
- `sanitize`：清理换行和 `|`，避免破坏轻量分隔格式。
- `safeAsrSegments` / `safeVisionText`：模型失败时写入 `MODEL_DEGRADATION` 并降级。

使用规则：

- 修改 artifact payload 格式时必须同步解析和序列化逻辑。
- 当前 payload 是轻量文本格式；如复杂度上升，优先迁移为结构化 JSON，并补测试。
- 模型阶段失败能降级时，应记录 `MODEL_DEGRADATION`，不要静默吞掉异常。

### `FailureClassifier`

位置：`ai-search-worker-service/src/main/java/com/aisearch/worker/application/FailureClassifier.java`

职责：

- 将底层异常归一为 `StageProcessingException`。
- 映射 `StageFailureType`：
  - 模型超时：`MODEL_TIMEOUT`
  - 模型限流：`MODEL_RATE_LIMIT`
  - 模型响应异常：`MODEL_RESPONSE`
  - 对象存储异常：`STORAGE`
  - FFmpeg/媒体处理异常：`MEDIA`
  - ES/Milvus 索引异常：`SEARCH_INDEX`
  - 配置异常：`CONFIGURATION`
  - 其他：`UNKNOWN`

使用规则：

- 新增外部系统或新失败类型时优先扩展该分类器。
- 阶段调度器依赖该分类结果决定是否可重试。

## 模型调用与向量工具

### `ModelGatewayClient`

位置：`ai-search-worker-service/src/main/java/com/aisearch/worker/infrastructure/model/ModelGatewayClient.java`

职责：

- Worker 调用 model-service 的统一客户端。
- 支持 ASR、ASR segments、OCR、caption、text embedding、image embedding。
- 内置并发限制、QPS 节流、重试、指数退避、LRU 风格缓存和 Micrometer 指标。
- 模型响应缓存通过 `ModelResponseCache` 抽象，可使用本地缓存或 Redis 分布式缓存。
- 校验 `ApiResponse.success`，只向业务层返回 `data`。

使用规则：

- Worker 阶段调用模型时必须走该客户端。
- 不要在阶段处理器中直接拼 model-service HTTP 请求。
- 新增模型能力时同步考虑缓存 key、限流、重试、指标和返回格式校验。

### `ModelResponseCache`

位置：

- `ai-search-worker-service/src/main/java/com/aisearch/worker/infrastructure/model/ModelResponseCache.java`
- `ai-search-worker-service/src/main/java/com/aisearch/worker/infrastructure/model/LocalModelResponseCache.java`
- `ai-search-worker-service/src/main/java/com/aisearch/worker/infrastructure/model/RedisModelResponseCache.java`

职责：

- 抽象 worker 模型响应缓存能力。
- `local` 模式使用进程内 LRU 风格缓存，适合本地和单实例。
- `redis` 模式使用 `StringRedisTemplate` 保存 JSON payload，适合多 worker 实例共享缓存。
- Redis 缓存读写失败不阻断主链路。

使用规则：

- worker 模型调用缓存必须通过 `ModelResponseCache`，不要在 `ModelGatewayClient` 中新增第二套缓存。
- 多实例生产环境建议配置 `ai-search.worker.model.cache-type=redis`。
- 新增缓存字段时同步检查 JSON 序列化兼容性和缓存 TTL。

### `VideoProcessingTaskExecutor` 阶段指标

位置：`ai-search-worker-service/src/main/java/com/aisearch/worker/application/VideoProcessingTaskExecutor.java`

职责：

- 暴露阶段任务计数指标：`ai_search_worker_stage_task_total`。
- 标签：`stage`、`result`、`failureType`。
- 暴露阶段任务耗时指标：`ai_search_worker_stage_task_duration`。

使用规则：

- 生产告警优先基于 `result=failed`、`result=retry`、`failureType` 和阶段耗时配置。
- 新增阶段终态或失败类型时同步检查指标标签和告警规则。

### `DashScopeModelClient`

位置：`ai-search-model-service/src/main/java/com/aisearch/model/infrastructure/DashScopeModelClient.java`

职责：

- 封装 DashScope ASR、OCR、caption、analysis、rerank 协议。
- ASR 使用异步任务提交和轮询。
- 解析 `transcription_url`、`transcripts.sentences`、`begin_time`、`end_time`、`speaker_id`。
- 兼容 chat 响应中的字符串或多段 content。

使用规则：

- DashScope 协议细节只应集中在该客户端。
- 密钥必须来自配置或环境变量，不得写入代码。
- 新增 DashScope 能力时优先复用 `requiredApiKey`、chat 文本提取和 ASR 时间片解析风格。

### 确定性向量生成器

位置：

- `ai-search-model-service/src/main/java/com/aisearch/model/infrastructure/DeterministicEmbeddingProvider.java`
- `ai-search-worker-service/src/main/java/com/aisearch/worker/application/DeterministicEmbeddingGenerator.java`

职责：

- 基于 SHA-256 生成稳定伪向量。
- 按配置维度循环填充向量。
- 支持本地开发和测试在无模型密钥时稳定运行。

使用规则：

- 本地测试默认使用 deterministic provider。
- 不要把确定性向量当作生产检索质量方案。
- 调整默认向量维度时同步 ES/Milvus 配置和测试。

## 新增工具能力沉淀流程

新增或重构工具能力时按以下顺序判断位置：

1. 仅单个类内部使用：保留为私有方法或私有 record。
2. 单个模块内多处复用：沉淀为包内可见工具类或领域服务。
3. 多个服务模块复用且不依赖具体服务：沉淀到 `ai-search-common`。
4. 依赖外部系统协议：放在对应模块的 `infrastructure` 层。
5. 依赖业务流程和状态机：放在对应模块的 `application` 层。

工具能力变更时必须同步：

- 本文档。
- 相关规则文件：根目录 `AGENTS.md` 与 `.cursorrules`。
- 受影响单元测试或集成测试。
