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
- MinIO 临时访问 URL 可被 DashScope 访问；如果本地 `localhost` 不能公网访问，需要配置可访问的对象网关地址。

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
- ES 和 Milvus 均写入片段索引
- 搜索接口可召回对应 `segmentId`
