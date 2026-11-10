# 三端智能业务助手

本模块在现有 Vue + Java / Spring Boot + H2 / MySQL 项目旁增加两个 Java 17 服务，使用 Spring Boot 3.5.7、Spring AI 1.1.8 和 Streamable HTTP MCP。仅查询数据，不执行交易和审批。支付渠道仍为原项目的模拟实现，本次未接入真实支付。

## 页面在哪里

登录任意身份，点击右下角「✦ AI」。顾客可查询附近盲盒、本人订单和退款资格；商家可查询本店库存、订单、售后及统计；管理员可查询待审核入驻、平台介入及订单统计。回答下方的「已查询」来自 Java 业务后端实际执行的工具记录。刷新后不恢复聊天历史，切换账号清空本次会话。

## 配置

1. 需要 JDK 17 或更新版本、Maven 和前端所需的 Node 环境。
2. 打开 `ai-services/local.ps1`；若文件不存在，复制 `local.example.ps1` 为同目录的 `local.ps1`。
3. 在 `local.ps1` 中填写支持工具调用的模型 API Key、网关根地址、模型名称。默认 DeepSeek 配置仅为服务地址，没有附带账号或密钥；需要自行注册、开通和充值。不要把密钥发到聊天或提交到 Git。
4. 网关地址填写根地址，例如 `https://api.deepseek.com`，不要在末尾重复加 `/v1/chat/completions`。
5. `local.ps1` 及构建缓存已加入 Git 忽略。启动脚本只对当前进程选择 PATH 中 java 对应的 JDK，不修改系统 JAVA_HOME；也可在 local.ps1 显式设置 JAVA_HOME。若 java 是系统转发程序，请显式填写真实 JDK 路径。

## 启动（所有终端先进入项目根目录）

首次编译：

```powershell
.\ai-services\start.ps1 build
```

随后分别在四个终端运行并保持打开：

```powershell
# 终端1：业务后端
npm run server
# 终端2：MCP 工具服务，8180
.\ai-services\start.ps1 mcp
# 终端3：模型与 MCP 客户端，8280；应在 MCP 启动成功后运行
.\ai-services\start.ps1 assistant
# 终端4：前端
npm run dev
```

浏览器访问 http://localhost:5173 。已有服务占用端口时不要重复启动；改过 Java 业务代码后需在原终端 Ctrl+C 并重新运行。若只运行旧的前后端，业务页面仍可使用，AI 提示智能服务未连接。

若 PowerShell 策略阻止脚本，可在当前终端使用 `powershell -ExecutionPolicy Bypass -File .\ai-services\start.ps1 build`，其余动作把 build 换为 mcp 或 assistant，不需要修改全局执行策略。

## IDEA

通过 Maven 面板添加 `ai-services/pom.xml` 为 Maven 项目，项目及 Maven 的 JDK 均选17或更新版本。Maven 用户设置可选择本目录 `maven-settings.xml`（官方中央仓库）。先运行 BusinessApplication，再运行 AssistantApplication。直接在 IDEA 运行时不会读取 local.ps1，需要在 AssistantApplication 运行配置的环境变量中设置 ZHX_AI_KEY、ZHX_AI_BASE、ZHX_AI_MODEL。

## 数据与权限

浏览器发送最多10条会话消息至 Java 业务后端；业务后端从登录态取得身份，为本次请求生成120秒有效的随机能力票据。Java 聊天服务将票据捕获在本次工具对象内，模型只能填写查询名称和筛选条件。MCP 工具服务转发查询到 Java 业务后端，业务后端再次检查账号状态、角色、订单或门店归属。请求结束立即销毁票据。票据和模型密钥不会返回浏览器，工具结果不包含密码和取货凭证。两个 Java 服务仅监听127.0.0.1，适用于本机运行；跨机器部署需额外配置 TLS、服务间认证及限流。

查询结果金额为分，模型展示为元。顾客盲盒范围默认15公里，预算为硬约束；结果最多20条并携带总数。本店订单统计区分今日和累计，今日以北京时间计算。退款条件与现有接口一致，最终提交仍会再次校验。

## 验证与答辩

- 顾客提问“附近20元以内有面包盲盒吗”，对照首页价格、库存和距离。
- 顾客提问“我的订单还能退款吗”，对照订单详情，不能查询别人的订单。
- 商家提问“今天经营情况怎么样”和“还有多少待取货订单”，对照订单列表。
- 管理员提问“待审核入驻和待介入售后各有多少”，对照对应管理页面。
- 修改库存或新增订单，再次提问，验证使用最新数据。
- 未启动 Java 或未配置密钥应显示连接/配置状态，不返回伪装为模型的本地回答。
- 使用 JDK 17，在 `server-java` 目录执行 `mvn test` 验证业务与 AI 查询权限；`npm run build` 验证前端；AI 服务编译使用上面的 build 命令。

实际云端工具选择和回答效果必须在有效密钥及可用模型下验收。未配置密钥时可检查 Java 启动与 MCP 工具连接，不能据此认定真实模型链路已通过。

## 验证范围

Java 业务后端的 AI 工具测试覆盖身份权限与查询；完整 Spring AI → MCP → Java 业务查询链路以及真实模型回答，需要三个服务均启动后单独验收。历史 Node 集成测试不能作为当前 Java 版本已通过端到端测试的证明。
