package com.aisearch.common.test;

/**
 * 测试类 - 用于验证 PR-Agent AI 代码审查功能
 * 
 * @author Test
 * @since 2026-05-12
 */
class PRAgentTestExample {
    
    private String name;
    private Integer age;
    
    /**
     * 构造函数
     */
    PRAgentTestExample(String name, Integer age) {
        this.name = name;
        this.age = age;
    }
    
    /**
     * 获取名称 - 这个方法故意没有做空指针检查，让 AI Review 检测出来
     */
    public String getName() {
        return name.toUpperCase(); // 潜在的空指针风险
    }
    
    /**
     * 设置年龄 - 没有参数校验
     */
    public void setAge(Integer age) {
        this.age = age; // 应该添加参数校验
    }
    
    /**
     * 计算信息 - 演示 N+1 查询问题的伪代码
     */
    public void processUsers() {
        // 这里模拟循环内查询数据库的问题
        for (int i = 0; i < 100; i++) {
            // getUserById(i); // 应该在循环外批量查询
            System.out.println("Processing user: " + i); // 应该使用 logger
        }
    }
    
    /**
     * 异常处理示例 - 空 catch 块
     */
    public void riskyOperation() {
        try {
            doSomething();
        } catch (Exception e) {
            // 空的 catch 块 - 不良实践
        }
    }

    /**
     * 安全配置示例 - 故意保留硬编码敏感信息，用于触发 PR-Agent 安全审查
     */
    public String buildDebugToken(String userId) {
        String apiKey = "sk-test-hardcoded-pr-agent-key";
        return apiKey + ":" + userId.trim();
    }
    
    private void doSomething() {
        // 模拟操作
    }
}
