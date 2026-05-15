# AI 视频检索系统

AI 视频检索系统是一个基于 Spring Cloud Alibaba 的生产导向项目，目标是支持用户通过文字或图片检索视频，并返回相关视频片段、命中证据和辩证分析结果。

系统采用“在线混合召回 + 多阶段重排 + 离线深度理解”的架构：复杂的视频理解前置到离线处理，在线链路专注于轻量、快速、可解释的检索响应。

## 核心能力

- 文字搜视频、图片搜视频和混合检索。
- 返回片段级结果，包括 `videoId`、`segmentId`、`startTimeMs`、`endTimeMs`。
- 基于标题、字幕、OCR、Caption、元数据、文本向量、图片向量和片段向量进行混合召回。
- 对多路召回结果做融合、去重、聚合、重排和证据构建。
- LLM 只基于明确证据做辩证分析，避免脱离检索证据生成判断。
- 离线处理视频上传、转码、抽帧、ASR、OCR、Caption、Embedding 和索引构建。
- 支持失败重试、任务幂等、索引重建前清理旧片段、`X-Trace-Id` 链路排查。

## 技术栈

| 类型 | 技术 | 用途 |
| --- | --- | --- |
| 微服务框架 | Spring Boot 3.3.x | 服务开发基础 |
| 微服务体系 | Spring Cloud 2023.x / Spring Cloud Alibaba | 网关、注册、配置治理 |
| 网关 | Spring Cloud Gateway | 统一入口和路由 |
| 注册配置 | Nacos 2.x | 服务注册与配置中心 |
| 消息队列 | RocketMQ 5.x | 视频上传后的异步处理事件 |
| 对象存储 | MinIO | 原视频、封面、关键帧、切片文件 |
| 结构化存储 | MySQL | 视频资产、任务状态、片段、日志 |
| 半结构化存储 | MongoDB | ASR、OCR、Caption、模型原始结果 |
| 文本检索 | Elasticsearch | 关键词、字幕、OCR、Caption 召回 |
| 向量检索 | Milvus | 文本、图片、视频片段向量召回 |
| 缓存 | Redis | 搜索缓存、查询向量缓存、限流计数 |
| 视频处理 | FFmpeg | 转码、抽帧、切片、封面生成 |
| 可观测 | Prometheus / Grafana / OpenTelemetry | 指标和链路追踪 |
| 限流熔断 | Sentinel | 接口级限流、熔断、降级 |

## 模块说明

| 模块 | 默认端口 | 职责 |
| --- | --- | --- |
| `ai-search-common` | 无 | 公共 DTO、枚举、响应结构和领域契约 |
| `ai-search-gateway` | `18080` | 统一入口，路由到搜索、视频、worker、模型服务 |
| `ai-search-search-service` | `18081` | 查询理解、多路召回、融合、重排、证据和分析编排 |
| `ai-search-video-service` | `18082` | 视频上传初始化、完成确认、资产状态、上传事件发布 |
| `ai-search-worker-service` | `18083` | 离线视频索引工作流、阶段任务、产物、ES/Milvus 写入 |
| `ai-search-model-service` | `18084` | 模型网关，封装 embedding、OCR、ASR、Caption、分析能力 |

## 架构概览

```mermaid
flowchart TB
    User["用户"] --> Gateway["Spring Cloud Gateway"]
    Gateway --> Search["Search Service"]
    Gateway --> Video["Video Service"]
    Gateway --> WorkerApi["Worker Service"]
    Gateway --> Model["Model Service"]

    Video --> MinIO["MinIO"]
    Video --> MySQL["MySQL"]
    Video --> MQ["RocketMQ: video.uploaded"]

    MQ --> Worker["Video Processing Worker"]
    Worker --> FFmpeg["FFmpeg"]
    Worker --> Model
    Worker --> MySQL
    Worker --> ES["Elasticsearch"]
    Worker --> Milvus["Milvus"]
    Worker --> MongoDB["MongoDB"]

    Search --> Model
    Search --> ES
    Search --> Milvus
    Search --> Redis["Redis"]
    Search --> MySQL

    Nacos["Nacos"] -.-> Gateway
    Nacos -.-> Search
    Nacos -.-> Video
    Nacos -.-> Worker
    Nacos -.-> Model
```

在线检索链路：

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

离线索引链路：

```text
VideoUploadService
  -> RocketMqVideoProcessingEventPublisher
  -> VideoUploadedConsumer
  -> VideoIndexingWorkflowService
  -> VideoProcessingTaskExecutor
  -> StageProcessor
  -> ElasticsearchIndexWriter / MilvusVectorIndexWriter
```

## 本地环境

基础中间件复用本机 Docker Compose：

```powershell
docker compose --progress plain -f E:\workspace\docker\docker-compose.yml up -d
```

默认连接：

| 组件 | 地址 | 本地默认账号 |
| --- | --- | --- |
| Nacos | `localhost:8848` | 无 |
| MySQL | `localhost:3306` | `root/root`，数据库 `ai_search` |
| Redis | `localhost:6379` | password `redis` |
| MinIO API | `localhost:9000` | `minioadmin/minioadmin` |
| MinIO Console | `localhost:9001` | `minioadmin/minioadmin` |
| Milvus | `localhost:19530` | 无 |
| Attu | `localhost:8000` | 无 |
| Elasticsearch | `localhost:9200` | 本地开发关闭安全认证 |
| RocketMQ namesrv | `localhost:9876` | 无 |
| RocketMQ broker | `localhost:10909/10911/10912` | 无 |
| Prometheus | `localhost:9090` | 无 |
| Grafana | `localhost:3000` | `admin/admin` |
| Sentinel Dashboard | `localhost:8858` | 无 |
| OpenTelemetry Collector | `localhost:4317/4318` | 无 |
| FFmpeg Tools | Docker service `ffmpeg-tools` | 通过 `E:\workspace\docker\bin\ffmpeg-docker.cmd` / `ffprobe-docker.cmd` 调用 |

本机 Docker Compose 注意事项：

- `E:\workspace\docker` 中 MinIO 镜像默认没有 `curl`，健康检查使用 `mc ready local`。
- RocketMQ namesrv 健康检查使用容器内完整路径 `/home/rocketmq/rocketmq-5.3.2/bin/mqadmin`。
- Redis 开启密码 `redis`，本项目本地配置需包含 `spring.data.redis.password=redis`。
- 多服务共用 `ai_search` schema，并通过不同 `flyway_schema_history_*` 表管理迁移；`baseline-on-migrate: true` 必须搭配 `baseline-version: 0`，避免其他服务已建表时跳过当前服务 `V1`。
- 如果 Elasticsearch 镜像因 `docker.elastic.co` 网络问题无法拉取，可临时关闭 ES 相关启动路径，但完整关键词召回和 ES 索引链路需要恢复 Elasticsearch。
- FFmpeg 由 `ffmpeg-tools` 容器提供，本地 worker 默认通过 `E:\workspace\docker\bin\ffmpeg-docker.cmd` 和 `E:\workspace\docker\bin\ffprobe-docker.cmd` 调用；视频处理工作目录需位于 `E:\workspace\docker\data\ai-search-worker`，由 compose 挂载到容器内 `/work`。

临时无 Elasticsearch 启动示例：

```powershell
$env:AI_SEARCH_SEARCH_ELASTICSEARCH_ENABLED = "false"
$env:AI_SEARCH_WORKER_INITIALIZE_INDEX_ON_STARTUP = "false"
mvn -pl ai-search-search-service spring-boot:run
mvn -pl ai-search-worker-service spring-boot:run
```

构建与测试：

```powershell
mvn test
```

服务启动示例：

```powershell
mvn -pl ai-search-gateway spring-boot:run
mvn -pl ai-search-search-service spring-boot:run
mvn -pl ai-search-video-service spring-boot:run
mvn -pl ai-search-worker-service spring-boot:run
mvn -pl ai-search-model-service spring-boot:run
```

健康检查：

```powershell
Invoke-RestMethod http://localhost:18080/actuator/health
Invoke-RestMethod http://localhost:18081/actuator/health
Invoke-RestMethod http://localhost:18082/actuator/health
Invoke-RestMethod http://localhost:18083/actuator/health
Invoke-RestMethod http://localhost:18084/actuator/health
```

## 配置要点

模型服务 provider：

| 配置 | 说明 |
| --- | --- |
| `ai-search.models.provider=deterministic` | 默认本地确定性向量，便于开发和测试稳定 |
| `ai-search.models.provider=http` | 转发到外部 embedding 服务 |
| `ai-search.models.provider=dashscope` | 使用阿里云百炼 DashScope 兼容接口 |

DashScope 相关配置：

```yaml
ai-search:
  models:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY:}
```

其中 `DASHSCOPE_API_KEY` 用于 model-service 的文本分析、ASR、OCR、Caption、rerank 和多模态 embedding 等视频理解能力。`DEEPSEEK_API_KEY` 仅用于代码 review、CI 分析和提交信息生成相关工具，不参与视频分析链路。

AI 钉钉告警公共配置建议放入 Nacos `ai-search-common.yaml`，各服务已通过 `spring.config.import` 加载该公共配置。示例文件位于 `deploy/nacos/ai-search-common.yaml`：

```yaml
ai-search:
  alert:
    enabled: true
    analyzer-provider: dashscope
    analyzer-api-key: ${DASHSCOPE_API_KEY:${DEEPSEEK_API_KEY:}}
    analyzer-endpoint: https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
    analyzer-model: qwen-plus
    dingtalk-access-token: ${dingtalk_access_token:${DINGTALK_ACCESS_TOKEN:}}
    dingtalk-secret: ${DINGTALK_SECRET:${dingtalk_secret:}}
```

`dingtalk-access-token` 只保存钉钉机器人 URL 中的 `access_token` 值，不保存完整 webhook；本机已配置 `dingtalk_access_token` 时可直接被占位符读取。

### AI 钉钉告警配置与验证流程

1. 在 Nacos 创建或更新公共配置 `ai-search-common.yaml`，可直接使用仓库示例：

```powershell
$content = Get-Content -Raw deploy\nacos\ai-search-common.yaml
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8848/nacos/v1/cs/configs" `
  -Body @{ dataId="ai-search-common.yaml"; group="DEFAULT_GROUP"; content=$content; type="yaml" }
```

2. 确认本机或服务运行环境已配置钉钉 token。变量名优先使用本机小写变量 `dingtalk_access_token`，兼容 `DINGTALK_ACCESS_TOKEN`。如需真实 AI 根因分析，默认配置 `DASHSCOPE_API_KEY` 即可复用阿里 DashScope；也可以切换为 DeepSeek：

```yaml
ai-search:
  alert:
    analyzer-provider: deepseek
    analyzer-api-key: ${DEEPSEEK_API_KEY:}
    analyzer-endpoint: https://api.deepseek.com/chat/completions
    analyzer-model: deepseek-chat
```

未配置分析 API Key 时告警仍会推送，但使用降级分析。

3. 单模块本地启动前，如果改过 `ai-search-common`，先安装 common 到本地 Maven 仓库，避免 `spring-boot:run` 读取旧快照包：

```powershell
mvn -pl ai-search-common install -DskipTests
mvn -pl ai-search-search-service spring-boot:run
```

4. 验证公共配置是否生效。服务启动日志应出现：

```text
[Nacos Config] Load config[dataId=ai-search-common.yaml, group=DEFAULT_GROUP] success
```

5. 验证告警链路。可临时停止 `model-service` 后调用 search-service，让模型 embedding 调用产生一次 `502`：

```powershell
$body = @{ text="触发AI告警测试"; topK=1; withAnalysis=$false } | ConvertTo-Json -Compress
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:18081/api/search" `
  -ContentType "application/json" `
  -Body $body
```

预期结果：

- HTTP 响应为 `502`，消息为“下游服务调用失败”。
- Redis 出现 `ai-search:alert:dedupe:*` 和 `ai-search:alert:rate:<service-name>` key。
- 服务日志出现 `[AiAlert] 钉钉推送完成 service=<service-name> severity=P1`。
- 钉钉群收到 Markdown 告警卡片。

如果服务日志出现 `errcode=300005, errmsg=token is not exist`，说明钉钉平台未识别当前 token；重新复制自定义机器人 webhook 中 `access_token=` 后面的值，更新 `dingtalk_access_token`，再重新发布 Nacos 配置并重启服务。

6. 验证完成后恢复被停止的服务：

```powershell
mvn -pl ai-search-model-service spring-boot:run
Invoke-RestMethod http://localhost:18084/actuator/health
```

如果 model-service 健康检查出现 Redis `NOAUTH`，检查 `ai-search-common` 是否误引入了 Redis starter；公共告警组件只应依赖 `spring-data-redis` 类型，不应把 Redis 自动配置传递给不需要 Redis 的服务。

搜索与 worker 生产配置重点：

- `ai-search.search.elasticsearch.endpoint`
- `ai-search.search.milvus.endpoint`
- `ai-search.search.model.endpoint`
- `ai-search.search.cache.enabled`
- `ai-search.search.cache.ttl-seconds`
- `ai-search.worker.model.max-concurrent-calls`
- `ai-search.worker.model.qps-limit`
- `ai-search.worker.model.max-attempts`
- `ai-search.worker.model.initial-backoff-ms`
- `ai-search.worker.model.cache-type`
- `ai-search.worker.model.cache-max-entries`
- `ai-search.worker.model.cache-ttl-seconds`
- `ai-search.security.api-key`
- `ai-search.security.admin-role`

配置 `ai-search.security.api-key` 后，所有 `/api/**` 请求都需要携带：

```http
X-AI-Search-Api-Key: <your-api-key>
```

调用工作流调试、重跑和索引维护接口时，还需要管理员角色：

```http
X-AI-Search-Roles: ADMIN
```

生产搜索可启用 Redis 响应缓存：

```yaml
ai-search:
  search:
    cache:
      enabled: true
      ttl-seconds: 60
      key-prefix: ai-search:search:
```

多 worker 实例建议启用 Redis 模型缓存：

```yaml
ai-search:
  worker:
    model:
      cache-type: redis
      cache-ttl-seconds: 86400
      cache-key-prefix: ai-search:model:
```

查询理解模型增强建议输出 JSON：

```json
{
  "rewrite": "王工 车间 巡检异常",
  "intent": "EXACT_ENTITY_SEARCH",
  "person": "王工",
  "scene": "车间",
  "sourceWeights": {
    "KEYWORD": 1.3,
    "OCR": 1.2
  }
}
```

## API 示例

### 搜索接口

```http
POST http://localhost:18081/api/search
Content-Type: application/json

{
  "text": "新能源车 发布会",
  "topK": 5,
  "withAnalysis": true
}
```

### 初始化视频上传

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

接口会生成 MinIO 预签名上传 URL，并写入 MySQL 视频资产记录。

### 完成视频上传

```http
POST http://localhost:18082/api/videos/{videoId}/complete
Content-Type: application/json

{
  "objectETag": "optional-etag",
  "fileSize": 10485760
}
```

接口会校验 MinIO 对象是否存在，更新视频状态为 `PROCESSING`，并发布 `video.uploaded` 事件。

### 片段证据与索引维护

- `GET /api/workflows/video-indexing/stages`
- `GET /api/workflows/video-indexing/videos/{videoId}/slice-plan`
- `GET /api/workflows/video-indexing/videos/{videoId}/segments/artifacts`
- `GET /api/workflows/video-indexing/videos/{videoId}/segments/{segmentId}`
- `POST /api/workflows/video-indexing/videos/{videoId}/delete-index`

## 端到端联调

目标是验证真实视频从上传到可检索的完整链路：

1. video-service 初始化上传并写入 `video_asset`。
2. MinIO 保存原始视频对象。
3. completeUpload 发布 RocketMQ 事件。
4. worker-service 执行切片、ASR、OCR、Caption、Embedding。
5. worker-service 写入 `video_segment`、`video_segment_artifact`、Elasticsearch 和 Milvus。
6. search-service 能通过文本或图片查询召回对应片段。

前置条件：

- Docker 基础环境已启动。
- `DASHSCOPE_API_KEY` 已配置到 model-service 运行环境，用于文本分析、ASR、OCR、Caption、rerank 和多模态 embedding。
- Docker 中 `ffmpeg-tools` 已启动，或本机可执行 `ffmpeg` 和 `ffprobe`；本地默认配置使用 Docker wrapper。
- MinIO 临时访问 URL 可被 DashScope 访问；本地 `localhost` 不能公网访问时，需要配置可访问的对象网关地址。
- 如启用 `ai-search.security.api-key`，请求需携带 `X-AI-Search-Api-Key`。

可使用烟测脚本跑真实视频全链路：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/e2e-video-search-smoke.ps1 `
  -VideoPath E:\videos\demo.mp4 `
  -GatewayBaseUrl http://localhost:18080 `
  -SearchText "视频内容"
```

通过标准：

- `video_asset.status = READY`。
- 至少生成 1 条 `video_segment`。
- 关键片段至少具备 `ASR_TEXT`、`OCR_TEXT` 或 `CAPTION` 中的一类证据。
- 片段证据接口能返回时间范围、ASR/OCR/Caption 和 `keyFrameUrl`。
- Elasticsearch 和 Milvus 均写入片段索引。
- 搜索接口可召回对应 `segmentId`。
- 重建索引前会先清理旧 ES/Milvus 片段，避免重复残留。

## 模型选型

| 能力 | 默认模型 | 用途 |
| --- | --- | --- |
| 文本向量 | `text-embedding-v4` | 查询文本、ASR/OCR/Caption 文本向量化 |
| 图片/多模态向量 | `multimodal-embedding-v1` | 图片搜视频、关键帧向量化 |
| ASR | `paraformer-v2` | 视频语音转写 |
| OCR | `qwen-vl-ocr` | 视频帧文字识别 |
| 视觉描述 | `qwen3-vl-flash` | 关键帧 caption |
| 辩证分析 | `qwen-plus` | 汇总证据、输出正反分析 |
| 精排 | `gte-rerank-v2` | 多路召回候选 rerank |

## 开发路线

### 第一阶段：工程骨架与检索闭环

- 建立 Spring Cloud Alibaba 多模块工程。
- 提供 Gateway、Search、Video、Worker、Model 服务边界。
- 搜索服务跑通查询理解、多路召回、融合、重排、证据构建和辩证分析链路。
- 使用本地确定性能力保证接口和测试稳定。

### 第二阶段：真实中间件适配

- Video Service 接入 MinIO，创建业务 bucket。
- Video Service 接入 RocketMQ，上传完成后发送 `video.uploaded`。
- Worker Service 消费 `video.uploaded`，执行状态机。
- Search Service 接入 Redis 查询缓存。

### 第三阶段：检索引擎接入

- 接入 Elasticsearch 关键词召回。
- 接入 Milvus 文本、图片、视频片段向量召回。
- 实现 Candidate Merge 的分数归一化和来源保留。
- 建立基础评测集。

### 第四阶段：模型能力接入

- Model Service 封装阿里云百炼 / 通义千问模型调用。
- 支持文本 embedding、图片 embedding、caption、OCR、ASR、rerank。
- 接入调用缓存、限流、重试、成本统计。

### 第五阶段：生产化

- 接入 Sentinel 限流熔断。
- 接入 Prometheus、Grafana、OpenTelemetry。
- 完善异常处理、审计日志、权限过滤和租户隔离。
- 增加端到端测试与检索质量评测。

## AI 自动生成提交信息

项目通过 Husky 的 `prepare-commit-msg` hook 接入 DeepSeek，根据暂存区 diff 自动生成 Git 提交信息。

### 使用方式

1. 配置本机环境变量 `DEEPSEEK_API_KEY`。
2. 暂存需要提交的文件。
3. IDEA 提交面板中保留提交信息框里的 `__AI_COMMIT_MESSAGE__` 不变。
4. 点击提交后，hook 会调用 DeepSeek，将占位符替换为真实提交信息。

行为规则：

- 保留 `__AI_COMMIT_MESSAGE__`：自动生成提交信息。
- 手动输入提交信息：保留用户手写内容，不覆盖。
- 清空提交信息：IDEA 可能在 Git hook 执行前拦截，因此不建议清空。

### 配置流程

安装依赖并启用 Husky：

```powershell
npm install
npm run prepare
```

设置本仓库提交模板：

```powershell
git config commit.template .gitmessage
```

确认配置：

```powershell
git config --show-origin --get core.hooksPath
git config --show-origin --get commit.template
```

期望输出包含：

```text
.husky
.gitmessage
```

配置环境变量：

```powershell
[Environment]::SetEnvironmentVariable("DEEPSEEK_API_KEY", "<your-deepseek-api-key>", "User")
```

重新打开 IDEA 或终端，让新环境变量生效。

### 验证方式

运行 hook 验证脚本：

```powershell
D:\software\work\git\Git\bin\bash.exe scripts/test-ai-commit-hook.sh
```

手动验证：

```powershell
git add ai-commit-hook-manual-test.txt
git commit
```

如果使用 IDEA：

1. 打开 Commit 面板。
2. 确认提交信息框中有 `__AI_COMMIT_MESSAGE__`。
3. 不修改该占位符，直接提交。
4. 真实提交时 hook 会生成类似 `docs(readme): 更新项目文档` 的提交信息。

### 故障排查

- 如果 IDEA 提示 `Specify commit message`，说明提交模板没有加载；执行 `git config commit.template .gitmessage` 后重新打开 Commit 面板。
- 如果提示未设置 `DEEPSEEK_API_KEY`，确认用户级或机器级环境变量已配置，并重启 IDEA。
- 如果提示未找到 Python，确认 Git Bash 环境可执行 `python` 或 `python3`。
- 如果没有 staged diff，hook 会跳过生成；需要先暂存文件。

## 当前实现边界

当前项目已经具备可编译、可测试、可继续演进的微服务骨架和核心垂直链路。

已完成：

- 多模块 Maven 工程。
- Gateway、Search、Video、Worker、Model 服务入口。
- 搜索主链路内部编排。
- 视频上传初始化与完成确认。
- RocketMQ 上传事件发布与消费。
- Worker 阶段任务、产物、失败重试和索引写入边界。
- Elasticsearch 与 Milvus REST 适配器。
- 模型服务 deterministic/http/dashscope provider。
- API Key 鉴权、`X-Trace-Id` 透传、基础指标。
- Redis 搜索响应缓存。
- Prometheus 告警规则模板。
- 真实视频端到端烟测脚本。

待继续完善：

- 真实 FFmpeg 转码/抽帧处理器。
- 真实 ASR、OCR、Caption、Embedding 阶段处理器。
- 查询向量缓存。
- Sentinel 限流规则。
- OpenTelemetry 链路追踪。
- 检索质量评测集。
