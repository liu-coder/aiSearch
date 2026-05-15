# AI Search 架构说明

## 项目定位

AI Search 是一个基于 Spring Cloud Alibaba 的 AI 视频检索系统，支持视频上传、离线视频理解、片段级索引构建、在线混合召回、重排、证据构建和辩证分析。

核心原则：

- 复杂视频理解前置到离线 worker。
- 在线检索链路保持轻量、快速、可解释。
- 检索结果以视频片段为最小粒度。
- LLM 分析必须基于明确检索证据，避免脱离证据生成判断。

## 技术栈

- Java 21
- Spring Boot 3.3.x
- Spring Cloud 2023.x
- Spring Cloud Alibaba
- Maven 多模块工程，根模块 `com.aisearch:ai-search:0.1.0-SNAPSHOT`
- JPA 实体持久化，Flyway 管理 schema
- RocketMQ 消息队列
- MinIO 对象存储
- MySQL 8.0 关系数据
- Elasticsearch 文本检索
- Milvus 向量检索，默认维度 `1024`，默认度量 `COSINE`
- Nacos discovery/config
- Actuator + Prometheus

## 模块边界

| 模块 | 职责 | 默认端口 |
| --- | --- | --- |
| `ai-search-common` | 公共 DTO、枚举、响应结构、鉴权、trace、跨服务契约 | 无 |
| `ai-search-gateway` | Spring Cloud Gateway 统一入口、服务路由、网关能力 | `18080` |
| `ai-search-search-service` | 查询理解、混合召回、融合、重排、证据构建、辩证分析编排 | `18081` |
| `ai-search-video-service` | 视频上传初始化、完成确认、视频资产状态、outbox 事件发布 | `18082` |
| `ai-search-worker-service` | 离线视频索引工作流、阶段任务、产物、ES/Milvus 写入 | `18083` |
| `ai-search-model-service` | embedding、ASR、OCR、caption、rerank、analysis 模型网关 | `18084` |

新增模块规则：

- 在根 `pom.xml` 的 `<modules>` 中登记。
- 包名遵循 `com.aisearch.<bounded-context>`。
- 服务模块默认包含 actuator、Nacos discovery/config、Prometheus registry。
- 对外 REST 响应使用 `ApiResponse<T>` 包装。

## 分层约定

| 层 | 包路径 | 规则 |
| --- | --- | --- |
| API 层 | `*.api` | 只做 HTTP 路由、参数校验、响应包装；业务流程委托 application 层。 |
| Application 层 | `*.application` | 编排用例、领域流程、外部端口接口和稳定业务服务。 |
| Domain 层 | `*.domain` | JPA 实体、领域枚举和领域状态变化方法。 |
| Infrastructure 层 | `*.infrastructure` | MinIO、RocketMQ、ES、Milvus、模型 HTTP 客户端、配置属性、Repository。 |
| Common 契约层 | `ai-search-common` | 跨服务共享 DTO、枚举、消息协议、统一响应和 Web 基础设施。 |

禁止事项：

- API 层直接实现业务流程。
- `ai-search-common` 依赖任何具体服务模块。
- application/domain 层直接依赖具体外部客户端实现。

## 在线检索链路

```text
SearchController
  -> SearchUseCase
  -> QueryUnderstandingService
  -> HybridRecallOrchestrator
  -> CandidateMergeService
  -> RerankService
  -> EvidenceService
  -> LlmAnalysisService
```

链路约束：

- `SearchRequest` 入口必须完成参数校验。
- 查询理解负责生成 `QueryIntent`、召回来源和权重。
- 查询理解优先使用规则解析兜底；模型增强要求输出 JSON 对象，字段包括 `rewrite`、`intent`、`person`、`scene`、`startDate`、`endDate`、`sourceWeights`，解析失败不得阻断主链路。
- 召回适配器只负责外部搜索后端交互，不承载业务编排。
- 结果必须保留 `RecallSource` 和 `Evidence`。
- 辩证分析仅基于证据生成。

## 离线视频索引链路

```text
VideoUploadService
  -> EventOutboxPublisher
  -> RocketMqVideoProcessingEventPublisher
  -> VideoUploadedConsumer
  -> VideoIndexingWorkflowService
  -> VideoProcessingTaskExecutor
  -> StageProcessor
  -> ElasticsearchIndexWriter / MilvusVectorIndexWriter
```

链路约束：

- video-service 先写 `video_asset` 和 `event_outbox`。
- outbox 发布 `VideoUploadedMessage` 到 RocketMQ。
- worker 消费消息后生成 `VideoIndexingTaskPlan`。
- 阶段任务按 `WorkflowStage` 顺序执行。
- 同一 `(event_id, stage)` 只能对应一条阶段任务。
- 重建索引前必须先按 `videoId` 清理旧 ES/Milvus 片段。

## 外部索引结构

### Elasticsearch

- 默认索引：`ai_video_segments`
- 写入字段：`videoId`、`segmentId`、`title`、`startTimeMs`、`endTimeMs`、`indexVersion`、`asrText`、`ocrText`、`caption`
- 默认检索权重：`title^3`、`asrText^2`、`ocrText^2`、`caption`、`tags`

### Milvus

- 默认 collection：`ai_video_segments`
- 主键字段：`segmentId`
- 文本向量字段：`embedding`
- 图片向量字段：`imageEmbedding`
- 默认维度：`1024`
- 默认度量：`COSINE`
- 写入字段：`videoId`、`segmentId`、`title`、`startTimeMs`、`endTimeMs`、`indexVersion`、`embedding`、`imageEmbedding`

索引字段名必须与 `SearchDocumentMapper` 兼容；如新增 snake_case 字段或改名，必须同步更新搜索适配器和测试。

## 配置 Schema

| 前缀 | 类 | 关键字段 |
| --- | --- | --- |
| `ai-search.models` | `ModelProviderProperties` | `provider`、`embeddingDimension`、`http.*`、`dashscope.*` |
| `ai-search.search` | `SearchBackendProperties` | `elasticsearch.*`、`milvus.*`、`model.endpoint`、`cache.*` |
| `ai-search.worker` | `WorkerPipelineProperties` | `workspace`、`embeddingDimension`、`initializeIndexOnStartup`、`maxAttempts`、`runningTimeoutMs`、`model.*`、`storage.*`、`mediaStrategy.*`、`elasticsearch.*`、`milvus.*` |
| `ai-search.storage.minio` | `MinioProperties` | `endpoint`、`accessKey`、`secretKey`、`rawBucket`、`presignedUploadExpireMinutes` |
| `ai-search.security.api-key` | `ApiKeyAuthFilter` | 非空时启用 `/api/**` API Key 鉴权 |
| `ai-search.security.admin-role` | `ApiKeyAuthFilter` | 工作流调试、重跑和索引维护接口要求的管理员角色，默认 `ADMIN` |
| `ai-search.alert` | `AiAlertProperties` | `enabled`、`analyzerProvider`、`analyzerApiKey`、`analyzerEndpoint`、`analyzerModel`、`dingtalkAccessToken`、`dingtalkSecret`、`deduplicateWindowSeconds`、`maxAlertsPerMinute` |

配置变更规则：

- 本地开发默认使用 deterministic 模型能力，保证测试稳定。
- DashScope 密钥只能通过环境变量或外部配置提供，不能写入仓库。
- AI 告警公共配置优先放入 Nacos `ai-search-common.yaml`；钉钉机器人 token 使用本机变量 `dingtalk_access_token` 或兼容变量 `DINGTALK_ACCESS_TOKEN` 注入，不能写入仓库。
- 新增配置项时同步更新 `application.yml`、配置属性类、文档和测试。

`ai-search.worker.model` 生产重点配置：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `endpoint` | `http://localhost:18084` | model-service 地址 |
| `max-concurrent-calls` | `4` | 单 worker 模型调用并发 |
| `qps-limit` | `8` | 单 worker 模型调用 QPS 上限 |
| `max-attempts` | `3` | 单次模型 HTTP 调用最大尝试次数 |
| `initial-backoff-ms` | `200` | 指数退避初始等待时间 |
| `cache-type` | `local` | `local` 进程内缓存，`redis` 分布式缓存 |
| `cache-max-entries` | `1000` | 本地缓存最大条目数 |
| `cache-ttl-seconds` | `86400` | Redis 缓存 TTL |
| `cache-key-prefix` | `ai-search:model:` | Redis 缓存 key 前缀 |

`ai-search.search.cache` 生产重点配置：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `false` | 是否启用 Redis 搜索响应缓存 |
| `ttl-seconds` | `60` | 搜索响应缓存 TTL |
| `key-prefix` | `ai-search:search:` | Redis 缓存 key 前缀 |

## 生产观测指标

| 指标 | 来源 | 标签 | 说明 |
| --- | --- | --- | --- |
| `ai_search_model_call_total` | `ModelGatewayClient` | `path`, `result` | 模型调用成功/失败计数 |
| `ai_search_model_call_duration` | `ModelGatewayClient` | `path` | 模型调用耗时 |
| `ai_search_worker_stage_task_total` | `VideoProcessingTaskExecutor` | `stage`, `result`, `failureType` | worker 阶段任务成功、重试、失败计数 |
| `ai_search_worker_stage_task_duration` | `VideoProcessingTaskExecutor` | `stage` | worker 阶段任务耗时 |

Prometheus 告警规则模板位于 `deploy/prometheus/ai-search-alerts.yml`，覆盖 API 5xx、API 延迟、worker 阶段失败/重试、worker 慢任务、模型调用失败率和模型调用延迟。

AI 钉钉告警链路位于 `ai-search-common/src/main/java/com/aisearch/alert`，由 `GlobalExceptionHandler` 和 `AiAlertLogbackAppender` 触发，经过 Redis 去重/限流、DashScope 或 DeepSeek 兼容 Chat Completions 异常分析后推送钉钉机器人。公共 Nacos 配置示例位于 `deploy/nacos/ai-search-common.yaml`。

本地验证建议：先将 `deploy/nacos/ai-search-common.yaml` 发布为 Nacos `ai-search-common.yaml`，再启动任一 Web 服务触发 500/502 异常。验证点包括 Nacos 配置加载成功、Redis 出现 `ai-search:alert:*` 去重/限流 key、服务日志出现 `[AiAlert] 钉钉推送完成`，以及钉钉群收到 Markdown 告警卡片。

告警建议：

- `ai_search_worker_stage_task_total{result="failed"}` 持续增长时告警。
- `failureType="MODEL_RATE_LIMIT"` 或 `failureType="MODEL_TIMEOUT"` 增长时检查 DashScope 配额、MinIO URL 可达性和网络。
- 阶段耗时 P95 超过视频时长或业务阈值时触发慢任务告警。
