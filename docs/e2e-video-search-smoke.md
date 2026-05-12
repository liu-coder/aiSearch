# AI 搜索端到端联调手册

## 目标

验证真实视频从上传到可检索的完整链路：

1. video-service 初始化上传并写入 `video_asset`。
2. MinIO 保存原始视频对象。
3. completeUpload 发布 RocketMQ 事件。
4. worker-service 执行内容感知切片、ASR、OCR、Caption、Embedding。
5. worker-service 写入 `video_segment`、`video_segment_artifact`、ES、Milvus。
6. search-service 能通过文本查询召回对应片段。

## 前置条件

- Docker 基础环境已启动：MySQL、Redis、Nacos、RocketMQ、Milvus、Elasticsearch、MinIO。
- `DASHSCOPE_API_KEY` 已配置到 model-service 运行环境。
- 本机可执行 `ffmpeg` 和 `ffprobe`，或在 `ai-search.ffmpeg.command` / `ffprobe-command` 指向正确路径。
- MinIO 临时访问 URL 可被 DashScope 访问；如果本地 `localhost` 不能公网访问，需要把 `ai-search.worker.storage.endpoint` 配成公网或内网专线可访问的对象网关地址。
- 如果生产配置了 `ai-search.security.api-key`，所有 `/api/**` 请求需携带 `X-AI-Search-Api-Key`；每个响应会带 `X-Trace-Id`，排查时用它串联 upload、worker、model 和 search 日志。

## 检查点

### 1. 服务健康

```powershell
Invoke-RestMethod http://localhost:18080/actuator/health
Invoke-RestMethod http://localhost:18081/actuator/health
Invoke-RestMethod http://localhost:18082/actuator/health
Invoke-RestMethod http://localhost:18083/actuator/health
Invoke-RestMethod http://localhost:18084/actuator/health
```

### 2. 上传视频

调用 `POST /api/videos/uploads/initiate` 获取预签名上传地址，上传样例视频后调用 `POST /api/videos/uploads/complete`。

### 3. 等待处理完成

查询 `video_asset.status` 应从 `PROCESSING` 变为 `READY`；如果变为 `FAILED`，查看：

- `video_processing_stage_task.failure_type`
- `video_processing_stage_task.failure_reason`
- `video_segment`
- `video_segment_artifact`

### 4. 搜索验证

调用 `POST /api/search`，确认返回结果包含片段级：

- `videoId`
- `segmentId`
- `startTimeMs`
- `endTimeMs`
- ASR/OCR/Caption 证据

## 通过标准

- `video_asset.status = READY`
- 至少生成 1 条 `video_segment`
- 每条关键片段至少具备 `ASR_TEXT`、`OCR_TEXT` 或 `CAPTION` 中的一类证据
- `GET /api/workflows/video-indexing/videos/{videoId}/segments/{segmentId}` 能返回该片段的 `startTimeMs`、`endTimeMs`、ASR/OCR/Caption 和 `keyFrameUrl`
- ES 和 Milvus 均写入片段索引
- 搜索接口可召回对应 `segmentId`
- 重建索引前会先清理旧 ES/Milvus 片段，避免重复片段残留

## 生产化检查

- 模型调用限流：确认 `ai-search.worker.model.max-concurrent-calls`、`qps-limit`、`max-attempts` 和 `initial-backoff-ms` 符合 DashScope 配额。
- 模型缓存：相同片段的 ASR/OCR/Caption/Embedding 请求会按请求体缓存，避免重跑时重复扣费。
- MinIO URL：用 worker 产物中的 `AUDIO_URL` 或 `FRAME_URL` 在 DashScope 所在网络环境执行访问测试，重点验证 DNS、签名有效期和防火墙。
- Flyway：真实 MySQL 环境执行 worker 迁移时，确认 `V2__video_segments_and_failure_classification.sql` 一次性创建 `video_segment`、`video_segment_artifact` 并添加 `failure_type`；仓库已移除重复的 `V2` 迁移文件。
