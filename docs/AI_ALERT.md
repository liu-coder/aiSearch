# AI 钉钉预警接入与验证

本文档记录 AI Search 的异常预警链路配置、启动验证和排障流程。

## 链路说明

异常来源：

- `GlobalExceptionHandler` 捕获 500/502 类接口异常。
- `AiAlertLogbackAppender` 捕获带异常对象的 `ERROR` 日志。

处理链路：

```text
异常触发
  -> AiAlertService
  -> AlertDeduplicator（Redis 去重/限流，本地兜底）
  -> AiExceptionAnalyzer（DashScope/DeepSeek OpenAI 兼容接口）
  -> DingTalkNotifier（钉钉 Markdown 推送）
```

## Nacos 公共配置

公共配置优先放入 Nacos `ai-search-common.yaml`。仓库示例位于：

```text
deploy/nacos/ai-search-common.yaml
```

默认使用阿里 DashScope 做 AI 解析：

```yaml
ai-search:
  alert:
    enabled: true
    analyzer-provider: dashscope
    analyzer-api-key: ${DASHSCOPE_API_KEY:${DEEPSEEK_API_KEY:}}
    analyzer-endpoint: https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
    analyzer-model: qwen-plus
    dingtalk-endpoint: https://oapi.dingtalk.com/robot/send
    dingtalk-access-token: ${dingtalk_access_token:${DINGTALK_ACCESS_TOKEN:}}
    dingtalk-secret: ${DINGTALK_SECRET:${dingtalk_secret:}}
```

切换 DeepSeek：

```yaml
ai-search:
  alert:
    analyzer-provider: deepseek
    analyzer-api-key: ${DEEPSEEK_API_KEY:}
    analyzer-endpoint: https://api.deepseek.com/chat/completions
    analyzer-model: deepseek-chat
```

发布配置：

```powershell
$content = Get-Content -Raw deploy\nacos\ai-search-common.yaml
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8848/nacos/v1/cs/configs" `
  -Body @{ dataId="ai-search-common.yaml"; group="DEFAULT_GROUP"; content=$content; type="yaml" }
```

## 环境变量

必需：

```text
dingtalk_access_token
DASHSCOPE_API_KEY
```

如果钉钉机器人开启加签，还需要：

```text
dingtalk_secret
```

DeepSeek provider 需要：

```text
DEEPSEEK_API_KEY
```

`dingtalk_access_token` 推荐只填写 webhook 中 `access_token=` 后面的值。代码也兼容误填完整 webhook。

## 本地验证流程

1. 安装 common 快照，避免单模块启动读取旧包：

```powershell
mvn -pl ai-search-common install -DskipTests
```

2. 重启目标服务，例如 search-service：

```powershell
mvn -pl ai-search-search-service spring-boot:run
```

3. 确认启动日志：

```text
[Nacos Config] Load config[dataId=ai-search-common.yaml, group=DEFAULT_GROUP] success
```

4. 直连钉钉验证 token 和加签：

```powershell
# 使用 dingtalk_access_token 和 dingtalk_secret 组装请求，返回 errcode=0 表示机器人可用。
```

5. 触发项目预警。可临时停止 model-service 后调用搜索接口，让下游模型调用产生 502：

```powershell
$body = @{ text="触发AI告警测试"; topK=1; withAnalysis=$false } | ConvertTo-Json -Compress
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:18081/api/search" `
  -ContentType "application/json" `
  -Body $body
```

通过标准：

- HTTP 返回 `502`，消息为“下游服务调用失败”。
- Redis 出现 `ai-search:alert:dedupe:*` 和 `ai-search:alert:rate:<service-name>`。
- 服务日志出现：

```text
[AiAlert] AI 分析完成 service=<service-name> provider=dashscope severity=<P0/P1/P2>
[AiAlert] 钉钉推送完成 service=<service-name> severity=<P0/P1/P2>
```

- 钉钉群收到 Markdown 预警卡片。

验证后恢复被停止的服务：

```powershell
mvn -pl ai-search-model-service spring-boot:run
Invoke-RestMethod http://localhost:18084/actuator/health
```

## 常见问题

`errcode=300005, errmsg=token is not exist`：

- 当前 `dingtalk_access_token` 不是该机器人的有效 token。
- 重新复制自定义机器人 webhook 中 `access_token=` 后面的值。
- 更新环境变量，重新发布 Nacos 配置并重启服务。

没有 AI 分析结果：

- 检查 `DASHSCOPE_API_KEY` 或 `DEEPSEEK_API_KEY` 是否存在。
- 未配置分析 API Key 时，告警仍推送，但使用降级分析。

单模块启动找不到 `AiAlertLogbackAppender`：

- 先运行 `mvn -pl ai-search-common install -DskipTests`。

model-service 健康检查 Redis `NOAUTH`：

- `ai-search-common` 不应引入 `spring-boot-starter-data-redis`。
- 公共告警组件只依赖 `spring-data-redis` 类型，避免把 Redis 自动配置传递给不需要 Redis 的服务。
