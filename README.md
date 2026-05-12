# AI 视频检索系统

这是一个基于 Spring Cloud Alibaba 的 AI 视频检索项目。当前已经实现网关、搜索服务、视频服务、离线 worker 服务、模型服务和公共契约模块，并开始接入真实上传链路。

## 模块

- `ai-search-common`：公共 DTO、枚举、API 响应结构。
- `ai-search-gateway`：统一入口，默认端口 `18080`。
- `ai-search-search-service`：文字/图片检索编排，默认端口 `18081`。
- `ai-search-video-service`：视频上传入口和资产边界，默认端口 `18082`。
- `ai-search-worker-service`：离线视频索引工作流边界，默认端口 `18083`。
- `ai-search-model-service`：模型网关边界，默认端口 `18084`。

## 本地环境

依赖环境复用：

```powershell
docker compose -f E:\workspace\docker\docker-compose.yml up -d
```

默认连接：

- Nacos: `localhost:8848`
- Redis: `localhost:6379`
- MinIO: `localhost:9000`
- Milvus: `localhost:19530`
- Elasticsearch: `localhost:9200`
- RocketMQ: `localhost:9876`

## 构建与测试

```powershell
mvn test
```

## 当前垂直切片

搜索接口：

```http
POST http://localhost:18081/api/search
Content-Type: application/json

{
  "text": "新能源车 发布会",
  "topK": 5,
  "withAnalysis": true
}
```

搜索服务现在使用 `HybridRecallOrchestrator` 编排多路召回。`LocalFixtureRecallAdapter` 仅在 `local/test` profile 下提供演示候选；默认 profile 已接入 Elasticsearch 和 Milvus REST 适配器：

- `ElasticsearchRecallAdapter`：负责 `KEYWORD`、`ASR`、`OCR`、`METADATA`
- `MilvusRecallAdapter`：负责 `TEXT_VECTOR`、`IMAGE_VECTOR`、`SEGMENT_VECTOR`
- `ModelEmbeddingClient`：调用 `http://localhost:18084/api/models/embedding` 生成查询向量

后续还需要补齐：

- `MetadataFilterAdapter`
- `RedisSearchCache`
- `DashScopeModelGateway`

视频上传初始化接口：

```http
POST http://localhost:18082/api/videos/uploads
Content-Type: application/json

{
  "fileName": "demo.mp4",
  "fileSize": 10485760,
  "contentType": "video/mp4",
  "title": "演示视频"
}
```

该接口会生成 MinIO 预签名上传 URL，并写入 MySQL 视频资产记录。

上传完成确认接口：

```http
POST http://localhost:18082/api/videos/{videoId}/complete
Content-Type: application/json

{
  "objectETag": "optional-etag",
  "fileSize": 10485760
}
```

该接口会校验 MinIO 对象是否存在，更新视频状态为 `PROCESSING`，并通过事件端口进入离线处理流程。

RocketMQ 链路：

- Video Service 通过 `RocketMqVideoProcessingEventPublisher` 发送 `video.uploaded`
- Worker Service 通过 `VideoUploadedConsumer` 消费 `video.uploaded`
- 消息体协议为 `VideoUploadedMessage`
- Worker 收到消息后会通过 `VideoProcessingTaskStore` 幂等创建 `video_processing_stage_task` 阶段任务，后续处理器按阶段领取并推进状态
- `VideoProcessingTaskExecutor` 会轮询 PENDING 任务，按阶段顺序执行 `StageProcessor`
- `INDEXING` 阶段会把片段文档写入 Elasticsearch，并把向量写入 Milvus

当前内置阶段处理器使用视频资产元数据生成 ASR/OCR/Caption 兜底产物，并用确定性向量保证本地链路可跑。生产环境需要继续替换为真实处理器：

- FFmpeg 转码/抽帧处理器
- ASR 处理器
- OCR 处理器
- Caption 处理器
- Embedding 模型处理器

Worker 启动时会通过 `SearchIndexInitializer` 初始化 Elasticsearch index 和 Milvus collection。任务执行支持 `RUNNING` 超时回收、失败重试和最大重试次数控制。

模型服务支持两种 provider：

- `ai-search.models.provider=deterministic`：本地确定性向量，默认启用。
- `ai-search.models.provider=http`：转发到外部 embedding 服务，通过 `ai-search.models.http.endpoint/model/api-key` 配置。
- `ai-search.models.provider=dashscope`：使用阿里云百炼 DashScope 兼容 embeddings 接口，通过 `ai-search.models.dashscope.api-key` 配置 API Key。

当前阿里模型选型：

| 能力 | 默认模型 | 用途 |
| --- | --- | --- |
| 文本向量 | `text-embedding-v4` | 查询文本、ASR/OCR/Caption 文本向量化 |
| 图片/多模态向量 | `multimodal-embedding-v1` | 图片搜视频、关键帧向量化 |
| ASR | `paraformer-v2` | 视频语音转写 |
| OCR | `qwen-vl-ocr` | 视频帧文字识别 |
| 视觉描述 | `qwen3-vl-flash` | 关键帧 caption |
| 辩证分析 | `qwen-plus` | 汇总证据、输出正反分析 |
| 精排 | `gte-rerank-v2` | 多路召回候选 rerank |

## 设计说明

详细架构见：

- `remind.md`
- `docs/project-architecture.md`
- `docs/development-roadmap.md`
