# AI 视频检索系统项目阐述

## 项目目标

本项目是一个生产导向的 AI 视频检索系统，目标是支持用户通过文字或图片检索视频，并返回相关视频片段、命中证据和辩证分析结果。

当前开发阶段优先搭建可演进的微服务骨架和第一条在线搜索垂直链路。MinIO、MySQL、RocketMQ 入口已经接入代码边界；Elasticsearch、Milvus 和模型服务后续通过召回/模型适配器补齐，生产 profile 不返回本地演示数据。

## 架构策略

系统采用“在线混合召回 + 多阶段重排 + 离线深度理解”的架构。

在线链路负责快速响应：

- 查询理解
- 多路召回
- 候选融合
- 规则/模型重排
- 证据构建
- LLM 辩证分析

离线链路负责视频理解：

- 视频上传
- FFmpeg 转码、抽帧、切片
- ASR 字幕提取
- OCR 画面文字识别
- Caption 视觉描述生成
- Embedding 向量生成
- Elasticsearch 和 Milvus 索引构建

## 技术选型

| 类型 | 技术 | 说明 |
| --- | --- | --- |
| 微服务框架 | Spring Boot 3.3.x | 服务开发基础框架 |
| 微服务体系 | Spring Cloud 2023.x | 网关、服务治理、配置治理基础 |
| Alibaba 生态 | Spring Cloud Alibaba | 对接 Nacos 等国内常用组件 |
| 网关 | Spring Cloud Gateway | 统一入口、路由、后续限流鉴权 |
| 注册/配置 | Nacos 2.x | 服务注册与配置中心 |
| 消息队列 | RocketMQ 5.x | 视频上传后的异步处理事件 |
| 向量存储 | Milvus | 文本、图片、视频片段向量召回 |
| 对象存储 | MinIO | Milvus 默认对象存储，同时可按 bucket 复用存业务视频文件 |
| 文本检索 | Elasticsearch | 标题、字幕、OCR、caption 等关键词召回 |
| 结构化存储 | MySQL | 视频资产、任务状态、模型调用记录 |
| 半结构化存储 | MongoDB | OCR、ASR、caption、模型原始结果 |
| 缓存 | Redis | 搜索缓存、查询向量缓存、元数据缓存 |
| 可观测 | Prometheus + Grafana | 指标采集与展示 |
| 链路追踪 | OpenTelemetry / SkyWalking | 分布式调用链路追踪 |
| 限流熔断 | Sentinel | 接口级限流、熔断、降级 |
| 视频处理 | FFmpeg | 转码、抽帧、切片、封面生成 |
| 测试 | JUnit 5 + Surefire | 单元测试与构建验证 |
| 覆盖率 | JaCoCo | 后续生成覆盖率报告 |

## 服务拆分

### ai-search-common

公共契约模块，不单独启动。

已实现内容：

- 统一响应结构：`ApiResponse`
- 搜索请求/响应模型：`SearchRequest`、`SearchResponse`
- 搜索执行计划：`SearchPlan`
- 搜索结果项：`SearchResultItem`
- 命中证据：`Evidence`
- 辩证分析模型：`DialecticalAnalysis`
- 查询类型：`QueryType`
- 召回来源：`RecallSource`
- 视频上传请求/响应：`InitiateUploadRequest`、`CompleteUploadRequest`、`VideoAssetResponse`
- 视频状态：`VideoAssetStatus`
- 离线工作流阶段：`WorkflowStage`

### ai-search-gateway

统一 API 网关，默认端口 `18080`。

职责：

- 统一暴露后端服务入口
- 路由到搜索、视频、worker、模型服务
- 后续接入鉴权、限流、灰度路由和统一日志

已实现内容：

- 启动类：`GatewayApplication`
- 路由配置：`application.yml`

当前路由：

| 路径 | 目标服务 |
| --- | --- |
| `/api/search/**` | `http://localhost:18081` |
| `/api/videos/**` | `http://localhost:18082` |
| `/api/workflows/**` | `http://localhost:18083` |
| `/api/models/**` | `http://localhost:18084` |

### ai-search-search-service

在线搜索服务，默认端口 `18081`。

职责：

- 对外提供文字/图片检索接口
- 编排查询理解、多路召回、候选融合、重排、证据构建和辩证分析
- 后续接入 Elasticsearch、Milvus、Redis 和模型服务

已实现内容：

- 启动类：`SearchServiceApplication`
- Controller：`SearchController`
- 主用例：`SearchUseCase`
- 查询理解：`QueryUnderstandingService`
- 召回编排接口：`RecallOrchestrator`
- 单路召回适配器接口：`RecallAdapter`
- 混合召回编排器：`HybridRecallOrchestrator`
- 本地演示召回适配器：`LocalFixtureRecallAdapter`，仅在 `local/test` profile 启用
- 候选融合：`CandidateMergeService`
- 规则重排：`RerankService`
- 证据构建：`EvidenceService`
- 辩证分析占位实现：`LlmAnalysisService`
- 内部领域模型：`QueryIntent`、`CandidateSegment`
- 查询意图类型：`SearchIntent`
- 测试：`SearchUseCaseTest`

接口清单：

| 接口名 | 方法 | 路径 | 入参 | 出参 | 当前状态 |
| --- | --- | --- | --- | --- | --- |
| 搜索接口 | `POST` | `/api/search` | `SearchRequest` | `ApiResponse<SearchResponse>` | 已实现，返回意图、过滤条件和召回计划；生产环境需要配置真实召回适配器 |

请求示例：

```json
{
  "text": "新能源车 发布会",
  "imageUrl": null,
  "topK": 5,
  "withAnalysis": true
}
```

处理链路：

```text
SearchController
  -> SearchUseCase
  -> QueryUnderstandingService：识别 TEXT / IMAGE / MIXED，生成 SearchIntent、semanticQuery、filters、recallSources
  -> HybridRecallOrchestrator：按 recallSources 调用关键词、文本向量、图片向量、OCR、ASR、元数据等适配器
  -> CandidateMergeService
  -> RerankService
  -> EvidenceService
  -> LlmAnalysisService
```

搜索响应中的 `searchPlan` 示例：

```json
{
  "intent": "EXACT_ENTITY_SEARCH",
  "semanticQuery": "新能源车 发布会",
  "filters": {},
  "recallSources": ["KEYWORD", "TEXT_VECTOR", "ASR", "OCR", "METADATA"]
}
```

后续真实适配器落点：

- `ElasticsearchRecallAdapter`：已接入 REST `_search`，负责 `KEYWORD`、`ASR`、`OCR`、`METADATA`
- `MilvusRecallAdapter`：已接入 REST `/v2/vectordb/entities/search`，负责 `TEXT_VECTOR`、`IMAGE_VECTOR`、`SEGMENT_VECTOR`
- `ModelEmbeddingClient`：已接入模型服务 `/api/models/embedding`，为 Milvus 查询生成向量
- `MetadataFilterAdapter`
- `RedisSearchCache`
- `ModelAnalysisClient`

当前默认搜索后端配置：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `ai-search.search.elasticsearch.endpoint` | `http://localhost:9200` | Elasticsearch 地址 |
| `ai-search.search.elasticsearch.index` | `ai_video_segments` | 视频片段倒排索引 |
| `ai-search.search.milvus.endpoint` | `http://localhost:19530` | Milvus REST 地址 |
| `ai-search.search.milvus.collection-name` | `ai_video_segments` | 视频片段向量 collection |
| `ai-search.search.milvus.vector-field` | `embedding` | 向量字段名 |
| `ai-search.search.model.endpoint` | `http://localhost:18084` | 模型服务地址 |

### ai-search-video-service

视频资产服务，默认端口 `18082`。

职责：

- 视频上传初始化和完成确认
- 视频资产元数据管理
- 通过 MinIO 预签名 URL 上传视频文件
- 上传完成后校验对象是否存在，并通过事件端口进入离线处理

已实现内容：

- 启动类：`VideoServiceApplication`
- Controller：`VideoController`
- 上传应用服务：`VideoUploadService`
- MinIO 上传端口：`ObjectStorageService`
- MinIO 实现：`MinioObjectStorageService`
- MySQL 实体：`VideoAssetEntity`
- MySQL 仓储：`VideoAssetRepository`
- 上传完成事件端口：`VideoProcessingEventPublisher`
- RocketMQ 事件实现：`RocketMqVideoProcessingEventPublisher`
- 本地降级事件实现：`LoggingVideoProcessingEventPublisher`，仅在 `local` profile 启用
- 初始化上传时生成 MinIO 预签名上传 URL，并保存视频资产记录
- 上传完成确认时校验 MinIO 对象是否存在，更新资产状态为 `PROCESSING`，发布视频上传完成事件

接口清单：

| 接口名 | 方法 | 路径 | 入参 | 出参 | 当前状态 |
| --- | --- | --- | --- | --- | --- |
| 初始化视频上传 | `POST` | `/api/videos/uploads` | `InitiateUploadRequest` | `ApiResponse<VideoAssetResponse>` | 已实现，生成 MinIO 预签名上传 URL 并落库 |
| 完成视频上传 | `POST` | `/api/videos/{videoId}/complete` | `CompleteUploadRequest` | `ApiResponse<VideoAssetResponse>` | 已实现，校验 MinIO 对象、更新状态、发布事件端口 |

请求示例：

```json
{
  "fileName": "demo.mp4",
  "fileSize": 10485760,
  "contentType": "video/mp4",
  "title": "演示视频"
}
```

后续真实适配器落点：

- `VideoAssetRepository`：已接入 Spring Data JPA，后续补充查询和索引状态字段

### ai-search-worker-service

离线处理服务，默认端口 `18083`。

职责：

- 承载视频处理状态机
- 消费 RocketMQ 上传事件
- 编排 FFmpeg、ASR、OCR、Caption、Embedding、Indexing
- 后续负责失败重试和补偿

已实现内容：

- 启动类：`WorkerServiceApplication`
- Controller：`WorkflowController`
- 工作流阶段定义服务：`WorkflowPlanService`
- RocketMQ 消费者：`VideoUploadedConsumer`
- 离线索引工作流入口：`VideoIndexingWorkflowService`
- 离线任务持久化端口：`VideoProcessingTaskStore`
- MySQL 阶段任务实体：`VideoProcessingStageTaskEntity`
- MySQL 阶段产物实体：`VideoProcessingArtifactEntity`
- Worker 只读视频资产实体：`VideoAssetReadEntity`
- MySQL 阶段任务仓储：`VideoProcessingStageTaskRepository`
- JPA 任务计划存储：`JpaVideoProcessingTaskStore`
- 阶段任务状态：`StageTaskStatus`
- 阶段任务执行器：`VideoProcessingTaskExecutor`
- 阶段处理器接口：`StageProcessor`
- 默认阶段处理器：`DefaultStageProcessor`
- ES 索引写入端口与实现：`SearchIndexWriter`、`ElasticsearchIndexWriter`
- Milvus 向量写入端口与实现：`VectorIndexWriter`、`MilvusVectorIndexWriter`
- ES/Milvus 初始化器：`SearchIndexInitializer`
- 确定性向量生成器：`DeterministicEmbeddingGenerator`

接口清单：

| 接口名 | 方法 | 路径 | 入参 | 出参 | 当前状态 |
| --- | --- | --- | --- | --- | --- |
| 查询视频索引阶段 | `GET` | `/api/workflows/video-indexing/stages` | 无 | `ApiResponse<List<WorkflowStage>>` | 已实现 |

消息消费：

| Topic | Consumer Group | 消息类型 | 处理入口 | 当前状态 |
| --- | --- | --- | --- | --- |
| `video.uploaded` | `ai-search-worker-video-uploaded` | `VideoUploadedMessage` | `VideoUploadedConsumer -> VideoIndexingWorkflowService -> VideoProcessingTaskStore` | 已实现，按阶段落库并做事件幂等 |

阶段执行链路：

```text
VideoProcessingTaskExecutor
  -> findTop20ByStatusOrderByCreatedAtAsc(PENDING)
  -> previousStagesSucceeded(eventId, stageSequence)
  -> StageProcessor.process(task)
  -> VideoProcessingArtifactRepository upsert
  -> INDEXING 阶段写入 Elasticsearch + Milvus
```

当前内置处理器已经能把上传资产元数据、阶段产物和确定性向量写入索引，保证链路可端到端运行。ASR/OCR/Caption 的真实智能内容仍需替换为模型供应商处理器，例如 `DashScopeAsrStageProcessor`、`PaddleOcrStageProcessor`、`VisionCaptionStageProcessor`。

Worker 可靠性机制：

- `RUNNING` 任务超过 `ai-search.worker.running-timeout-ms` 会被回收。
- 未达到 `ai-search.worker.max-attempts` 的失败任务会回到 `PENDING` 等待重试。
- 达到最大重试次数后标记 `FAILED` 并保留失败原因。
- 默认启动时会初始化 ES index 和 Milvus collection，可通过 `ai-search.worker.initialize-index-on-startup=false` 关闭。

### 模型选型

模型服务通过 provider 方式隔离供应商实现，默认本地使用 `deterministic` 保证开发环境可运行；生产建议切换为 `dashscope`，统一在 Nacos 中配置 `ai-search.models.dashscope.api-key`。

| 能力 | 阿里模型 | 接入位置 |
| --- | --- | --- |
| 文本向量 | `text-embedding-v4` | `DashScopeEmbeddingProvider` |
| 图片/多模态向量 | `multimodal-embedding-v1` | 后续图片 embedding 处理器 |
| ASR | `paraformer-v2` | 后续 ASR stage processor |
| OCR | `qwen-vl-ocr` | 后续 OCR stage processor |
| Caption | `qwen3-vl-flash` | 后续 Caption stage processor |
| 辩证分析 | `qwen-plus` | 后续 `ModelAnalysisClient` |
| 精排 | `gte-rerank-v2` | 后续 `RerankService` 模型适配器 |

当前标准阶段：

```text
UPLOADED
TRANSCODING
FRAME_EXTRACTING
ASR_PROCESSING
OCR_PROCESSING
CAPTIONING
EMBEDDING
INDEXING
READY
```

后续真实适配器落点：

- `RocketMqVideoUploadedConsumer`
- `FfmpegVideoProcessor`
- `AsrTaskClient`
- `OcrTaskClient`
- `CaptionTaskClient`
- `EmbeddingTaskClient`
- `IndexingWorker`

### ai-search-model-service

模型网关服务，默认端口 `18084`。

职责：

- 统一封装国内模型供应商
- 提供 embedding、caption、OCR、ASR、rerank 和 LLM 分析能力
- 管理模型调用限流、重试、降级、缓存和成本统计

已实现内容：

- 启动类：`ModelServiceApplication`
- Controller：`ModelController`
- Embedding 边界：`EmbeddingService`
- 当前使用 SHA-256 生成确定性向量，便于本地调试和测试稳定

接口清单：

| 接口名 | 方法 | 路径 | 入参 | 出参 | 当前状态 |
| --- | --- | --- | --- | --- | --- |
| 文本 embedding 调试接口 | `GET` | `/api/models/embedding?text=...` | `text` | `ApiResponse<List<Double>>` | 已实现，当前为 stub |

后续真实适配器落点：

- `DashScopeEmbeddingClient`
- `DashScopeMultimodalClient`
- `RerankModelClient`
- `LlmAnalysisClient`
- `ModelCallLogRepository`

## 当前实现边界

当前已经实现的是“可编译、可测试、可继续扩展”的微服务骨架和搜索主流程，不是完整生产功能。

已完成：

- 多模块 Maven 工程
- Spring Boot 服务入口
- Spring Cloud Gateway 路由配置
- Nacos 配置项预留
- Actuator / Prometheus 暴露配置
- 搜索主链路内部编排
- 视频上传初始化接口
- 离线工作流阶段接口
- 模型 embedding 调试接口
- Maven 编译与 JUnit 5 测试通过

未完成但已预留位置：

- Worker 端视频工作流状态持久化
- MongoDB 半结构化分析结果持久化
- Elasticsearch 关键词召回
- Milvus 向量召回
- Redis 查询缓存
- 国内模型真实调用
- Sentinel 限流规则
- OpenTelemetry 链路追踪
- 检索质量评测集

## 本地运行与验证

编译测试：

```powershell
mvn test
```

启动搜索服务示例：

```powershell
mvn -pl ai-search-search-service spring-boot:run
```

调用搜索接口：

```http
POST http://localhost:18081/api/search
Content-Type: application/json

{
  "text": "新能源车 发布会",
  "topK": 5,
  "withAnalysis": true
}
```

## 后续开发优先级

1. Video Service 接入 MinIO，生成预签名上传 URL。
2. Video Service 接入 RocketMQ，发布 `video.uploaded`。
3. Worker Service 消费上传事件并落地工作流状态机。
4. Search Service 接入 Elasticsearch 关键词召回。
5. Search Service 接入 Milvus 向量召回。
6. Model Service 接入阿里云百炼 / 通义千问真实模型。
7. 增加 Redis 搜索缓存和查询向量缓存。
8. 建立检索评测集，持续衡量召回率、准确率、响应速度和成本。
