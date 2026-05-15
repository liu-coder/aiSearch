# API 与 DTO Schema

## 统一响应

所有 `/api/**` 接口默认返回 `ApiResponse<T>`。

```json
{
  "success": true,
  "data": {},
  "message": "OK",
  "timestamp": "2026-05-13T00:00:00Z"
}
```

新增 API 时：

- 请求体使用 `@Valid`。
- DTO 使用 Jakarta Validation 表达边界。
- 异常优先交给统一异常处理。
- 如配置 `ai-search.security.api-key`，调用 `/api/**` 需要 `X-AI-Search-Api-Key`。
- 工作流调试、重跑和索引维护接口需要 `X-AI-Search-Roles` 包含 `ai-search.security.admin-role`，默认角色为 `ADMIN`。

## 搜索协议

| Schema | 来源 | 字段 |
| --- | --- | --- |
| `SearchRequest` | `ai-search-common` | `text`、`imageUrl`、`topK`、`withAnalysis` |
| `SearchResponse` | `ai-search-common` | `requestId`、`queryType`、`searchPlan`、`results`、`analysis`、`latencyMs`、`generatedAt` |
| `SearchResultItem` | `ai-search-common` | `videoId`、`segmentId`、`title`、`startTimeMs`、`endTimeMs`、`score`、`recallSources`、`evidence` |
| `SearchPlan` | `ai-search-common` | `intent`、`semanticQuery`、`filters`、`recallSources`、`sourceWeights` |
| `Evidence` | `ai-search-common` | `source`、`description`、`confidence` |
| `DialecticalAnalysis` | `ai-search-common` | `positiveEvidence`、`counterEvidence`、`uncertainties`、`conclusion` |

校验规则：

- `SearchRequest.text` 最大 500 字符。
- `SearchRequest.imageUrl` 最大 2048 字符。
- `SearchRequest.topK` 范围为 1 到 100，默认 10。
- `SearchRequest` 的 `text` 与 `imageUrl` 至少提供一个。
- `withAnalysis` 未传时默认生成分析。

## 视频上传协议

| Schema | 来源 | 字段 |
| --- | --- | --- |
| `InitiateUploadRequest` | `ai-search-common` | `fileName`、`fileSize`、`contentType`、`title` |
| `CompleteUploadRequest` | `ai-search-common` | `objectETag`、`fileSize` |
| `VideoAssetResponse` | `ai-search-common` | `videoId`、`objectKey`、`bucket`、`uploadUrl`、`status`、`nextAction` |
| `VideoUploadedMessage` | `ai-search-common` | `eventId`、`videoId`、`bucket`、`objectKey`、`fileSize`、`contentType`、`occurredAt` |

校验规则：

- `InitiateUploadRequest.fileName` 必填，最大 200 字符。
- `InitiateUploadRequest.fileSize` 必须为正数。
- `contentType` 最大 100 字符。
- `title` 最大 200 字符。
- `CompleteUploadRequest.fileSize` 必须为正数。

## Worker 工作流协议

| Schema | 来源 | 字段 |
| --- | --- | --- |
| `VideoProcessingStatusResponse` | `ai-search-worker-service` | `videoId`、`stages`、`segments`、`segmentArtifacts` |
| `StageTaskView` | `ai-search-worker-service` | `stage`、`status`、`sequence`、`attempts`、`failureType`、`failureReason` |
| `SegmentView` | `ai-search-worker-service` | `segmentId`、`startTimeMs`、`endTimeMs` |
| `VideoIndexingTaskPlan` | `ai-search-worker-service` | `eventId`、`videoId`、`stageTasks` |
| `MediaProcessingPlan` | `ai-search-worker-service` | `durationMs`、`segmentDurationMs`、`strategyName`、`segments` |
| `VideoSegmentPlan` | `ai-search-worker-service` | `segmentId`、`startTimeMs`、`endTimeMs`、`keyFrameTimeMs` |
| `IndexSegmentDocument` | `ai-search-worker-service` | `videoId`、`segmentId`、`title`、`startTimeMs`、`endTimeMs`、`indexVersion`、`asrText`、`ocrText`、`caption`、`embedding`、`imageEmbedding` |

## 模型网关协议

| Schema | 来源 | 字段 |
| --- | --- | --- |
| `EmbeddingRequest` | `ai-search-model-service` | `input`、`sourceType` |
| `VisionRequest` | `ai-search-model-service` | `imageUrl`、`prompt` |
| `AsrRequest` | `ai-search-model-service` | `fileUrl` |
| `RerankRequest` | `ai-search-model-service` | `query`、`documents`、`topN` |
| `RerankResult` | `ai-search-model-service` | `index`、`score`、`document` |
| `AnalysisRequest` | `ai-search-model-service` | `query`、`evidence` |
| `TimedTextSegment` | `ai-search-common` | `startTimeMs`、`endTimeMs`、`text`、`speakerId` |

规则：

- `EmbeddingRequest.input`、`VisionRequest.imageUrl`、`AsrRequest.fileUrl`、`RerankRequest.query` 必填。
- `EmbeddingRequest.sourceType` 未传时按 `text` 处理。
- `RerankRequest.topN` 未传或小于等于 0 时返回全部候选，但不能超过 `documents.size()`。

## 核心枚举

- `VideoAssetStatus`: `INITIATED`、`UPLOADED`、`PROCESSING`、`READY`、`FAILED`
- `WorkflowStage`: `UPLOADED`、`TRANSCODING`、`FRAME_EXTRACTING`、`ASR_PROCESSING`、`OCR_PROCESSING`、`CAPTIONING`、`EMBEDDING`、`INDEXING`、`READY`、`FAILED`
- `StageTaskStatus`: `PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED`
- `StageFailureType`: `STORAGE`、`MEDIA`、`MODEL_RATE_LIMIT`、`MODEL_TIMEOUT`、`MODEL_RESPONSE`、`SEARCH_INDEX`、`CONFIGURATION`、`UNKNOWN`
- `QueryType`: `TEXT`、`IMAGE`、`MIXED`
- `RecallSource`: `KEYWORD`、`TEXT_VECTOR`、`IMAGE_VECTOR`、`SEGMENT_VECTOR`、`OCR`、`ASR`、`METADATA`
- `SearchIntent`: `SEMANTIC_VIDEO_SEARCH`、`SIMILAR_IMAGE_SEARCH`、`MIXED_EVIDENCE_SEARCH`、`EXACT_ENTITY_SEARCH`

## HTTP Endpoint

| 服务 | 方法 | 路径 | 请求 | 响应 |
| --- | --- | --- | --- | --- |
| search | `POST` | `/api/search` | `SearchRequest` | `ApiResponse<SearchResponse>` |
| video | `POST` | `/api/videos/uploads` | `InitiateUploadRequest` | `ApiResponse<VideoAssetResponse>` |
| video | `POST` | `/api/videos/{videoId}/complete` | `CompleteUploadRequest` | `ApiResponse<VideoAssetResponse>` |
| worker | `GET` | `/api/workflows/video-indexing/stages` | 无 | `ApiResponse<List<WorkflowStage>>` |
| worker | `GET` | `/api/workflows/video-indexing/videos/{videoId}` | 无 | `ApiResponse<VideoProcessingStatusResponse>` |
| worker | `GET` | `/api/workflows/video-indexing/videos/{videoId}/slice-plan` | 无 | `ApiResponse<VideoSlicePlan>` |
| worker | `GET` | `/api/workflows/video-indexing/videos/{videoId}/artifacts` | 无 | `ApiResponse<Map<String,String>>` |
| worker | `GET` | `/api/workflows/video-indexing/videos/{videoId}/segments/artifacts` | 无 | `ApiResponse<Map<String,Map<String,String>>>` |
| worker | `GET` | `/api/workflows/video-indexing/videos/{videoId}/segments/{segmentId}` | 无 | `ApiResponse<SegmentEvidence>` |
| worker | `POST` | `/api/workflows/video-indexing/videos/{videoId}/stages/{stage}/rerun` | 无 | `ApiResponse<Void>` |
| worker | `POST` | `/api/workflows/video-indexing/videos/{videoId}/rebuild-index` | 无 | `ApiResponse<Void>` |
| worker | `POST` | `/api/workflows/video-indexing/videos/{videoId}/delete-index` | 无 | `ApiResponse<Void>` |
| model | `GET` | `/api/models/embedding?text=` | query | `ApiResponse<List<Double>>` |
| model | `POST` | `/api/models/embeddings` | `EmbeddingRequest` | `ApiResponse<List<Double>>` |
| model | `POST` | `/api/models/image-embedding` | `VisionRequest` | `ApiResponse<List<Double>>` |
| model | `POST` | `/api/models/asr` | `AsrRequest` | `ApiResponse<String>` |
| model | `POST` | `/api/models/asr-segments` | `AsrRequest` | `ApiResponse<List<TimedTextSegment>>` |
| model | `POST` | `/api/models/ocr` | `VisionRequest` | `ApiResponse<String>` |
| model | `POST` | `/api/models/caption` | `VisionRequest` | `ApiResponse<String>` |
| model | `POST` | `/api/models/rerank` | `RerankRequest` | `ApiResponse<List<RerankResult>>` |
| model | `POST` | `/api/models/analysis` | `AnalysisRequest` | `ApiResponse<String>` |

管理员角色要求：

- `/api/workflows/video-indexing/videos/{videoId}/slice-plan`
- `/api/workflows/video-indexing/videos/{videoId}/artifacts`
- `/api/workflows/video-indexing/videos/{videoId}/segments/artifacts`
- `/api/workflows/video-indexing/videos/{videoId}/stages/{stage}/rerun`
- `/api/workflows/video-indexing/videos/{videoId}/rebuild-index`
- `/api/workflows/video-indexing/videos/{videoId}/delete-index`
