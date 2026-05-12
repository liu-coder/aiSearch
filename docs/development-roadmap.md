# 开发路线

## 第一阶段：工程骨架与检索闭环

- 建立 Spring Cloud Alibaba 多模块工程。
- 提供 Gateway、Search、Video、Worker、Model 服务边界。
- 搜索服务先跑通查询理解、多路召回、融合、重排、证据构建和辩证分析链路。
- 使用 stub 召回适配器保证接口和测试稳定。

## 第二阶段：真实中间件适配

- Video Service 接入 MinIO，创建业务 bucket。已完成初始化上传和对象校验。
- Video Service 接入 RocketMQ，上传完成后发送 `video.uploaded`。已完成。
- Worker Service 消费 `video.uploaded`，执行状态机。已完成工作流入口，待持久化阶段状态。
- Search Service 接入 Redis 查询缓存。

## 第三阶段：检索引擎接入

- 接入 Elasticsearch 关键词召回。
- 接入 Milvus 文本、图片、视频片段向量召回。
- 实现 Candidate Merge 的分数归一化和来源保留。
- 建立基础评测集。

## 第四阶段：模型能力接入

- Model Service 封装阿里云百炼 / 通义千问模型调用。
- 支持文本 embedding、图片 embedding、caption、OCR、ASR、rerank。
- 接入调用缓存、限流、重试、成本统计。

## 第五阶段：生产化

- 接入 Sentinel 限流熔断。
- 接入 Prometheus、Grafana、OpenTelemetry。
- 完善异常处理、审计日志、权限过滤和租户隔离。
- 增加端到端测试与检索质量评测。
