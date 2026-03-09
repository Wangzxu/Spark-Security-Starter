# Spark Security Starter

`Spark Security Starter` 是一个基于 Spring Boot 3 + Spring Security 6 + Vue 3 的轻量级、高安全性的前后端分离认证脚手架。它实现了企业级项目中常见的 JWT 双 Token 机制、无感刷新、黑名单拦截、版本号强制下线等高级安全特性。

## 🌟 核心特性

### 1. 双 Token 认证架构
系统抛弃了传统的单 Token 易过期或不安全的缺点，采用了更加严谨的 `AccessToken` + `RefreshToken` 机制：
*   **AccessToken (短效)**：默认 30 分钟过期，存储在客户端。用于每次请求的身份携带，不经过数据库查询，直接通过 JWT 签名校验，保证极高的接口响应速度。
*   **RefreshToken (长效)**：默认 7 天过期，保存在服务端的 Redis 中。当 AccessToken 过期时，前端通过它进行无感刷新，换取新的一对 Token，极大提升用户体验。

### 2. 多重安全拦截防线
在底层的 `JwtAuthenticationFilter` 中，系统构建了严密的三道拦截防线：
1.  **黑名单校验 (Token Blacklist)**：结合 **布隆过滤器 (Bloom Filter)** 与 Redis 实现的高效黑名单机制。
    *   **第一道防线**：请求到达时，首先通过布隆过滤器快速判断 Token 是否可能在黑名单中。若布隆过滤器判断不存在，则直接放行，无需查询 Redis，极大减轻了缓存压力。
    *   **第二道防线**：若布隆过滤器判断可能存在（存在误判率），则进一步查询 Redis 进行二次确认，确保拦截的准确性。
    *   **自动维护 (Double Buffering)**：系统采用双缓冲机制（Double Buffering）进行布隆过滤器的自动刷新。在重建过滤器时，先在临时过滤器中加载数据，完成后原子性地替换旧过滤器，确保在刷新期间黑名单拦截功能零停机，杜绝安全空窗期。
2.  **封禁状态校验 (Account Status)**：每次受保护请求都会检查用户的最新状态（`status`），一旦管理员将用户设为禁用（`0`），用户当前的请求会被立即拒绝。
3.  **版本号强制下线机制 (Token PV)**：巧妙地利用 `pv`（Password Version）字段。用户的每次关键变更（如修改密码）都会导致数据库中 `pv` 的自增。由于签发的 AccessToken 载荷（Payload）中绑定了旧的 `pv`，一旦校验发现与数据库不一致，Token 将立刻失效。这提供了一种极其优雅的“全设备强制下线”能力。

### 3. 上下文解耦
*   **UserContext (ThreadLocal)**：在认证成功后，系统会将解析出的用户信息封装存入 `ThreadLocal` 中。后续的 Service 业务层可以随时通过 `UserContext.getUserId()` 零侵入地获取当前操作人，无需层层传递参数。

### 4. 健壮的前端网络层
前端基于 Vue 3 + Axios 打造：
*   **并发安全的无感刷新**：在 Axios 的响应拦截器中完美处理了 401 状态码。当多个并发请求同时发现 Token 过期时，前端会挂起其他请求，只发出一次刷新请求。刷新成功后，自动重放刚才挂起的请求队列，对用户完全透明。
*   **完善的交互反馈**：结合 Element Plus，对登录、注册、修改密码、系统踢出等场景均有友好的交互反馈。

## 🛠️ 技术栈

**后端 (Backend)**
*   Java 17
*   Spring Boot 3.2.x
*   Spring Security 6.x
*   MyBatis-Plus 3.5.x
*   jjwt 0.11.5
*   Redis (Spring Data Redis)
*   Redisson (Bloom Filter)
*   MySQL 8.0

**前端 (Frontend)**
*   Vue 3 (Composition API)
*   Vite
*   Vue Router
*   Axios
*   Element Plus

## 📂 核心流程时序简述

### 1. 登录流程
1. 前端发送用户名和密码。
2. 后端验证通过，初始化该用户的 `pv`（版本号）。
3. 签发含有 `pv` 的 `AccessToken` 和常规的 `RefreshToken`。
4. 将 `RefreshToken` 存入 Redis (`refresh_token:username`)。
5. 前端保存双 Token。

### 2. 无感刷新流程
1. 前端请求受保护接口，后端抛出 `ExpiredJwtException`。
2. 后端捕获异常，优雅返回 `401` JSON 响应。
3. 前端拦截器捕获 `401`，锁定请求队列，发起 `/api/auth/refresh` 请求。
4. 后端验证传来的 `RefreshToken` 是否与 Redis 中的匹配。
5. 验证成功，颁发新 Token 对，更新 Redis；前端拿到新 Token，重放刚才的业务请求。

### 3. 修改密码流程 (强制下线验证)
1. 前端请求 `/api/user/change-password`。
2. 后端校验旧密码成功后，更新新密码，并将数据库中的 `pv` 字段 `+1`。
3. 后端删除 Redis 中的 `RefreshToken`。
4. 此时，该用户在所有其他设备上的 `AccessToken` 内含的是旧的 `pv`，再次请求时会被过滤器拦截失效；同时因为 Redis 里的 `RefreshToken` 被删，它们也无法再触发无感刷新，从而实现了**真·全设备强制下线**。

## 🚀 快速启动

1.  **数据库准备**
    *   创建数据库 `spark_security`。
    *   执行 `db/schema.sql` 脚本完成建表（包含 `sys_user` 及其所需字段，特别是 `pv` 字段）。
2.  **环境准备**
    *   启动本地的 MySQL 和 Redis 服务。
    *   确认 `application.yml` 中的数据源及 Redis 连接信息正确。
3.  **启动后端**
    *   运行 `SparkSecurityApplication.java`。后端服务默认跑在 `8080` 端口。
4.  **启动前端**
    *   进入 `frontend` 目录。
    *   执行 `npm install`。
    *   执行 `npm run dev` 即可访问前端页面进行测试。

## ⚙️ 如何从零搭建类似应用

如果你想在自己的新项目中引入类似的认证架构，可以参考以下核心步骤：

### 1. 依赖引入 (pom.xml)
除了常规的 `spring-boot-starter-web` 外，你需要引入以下关键依赖：
*   `spring-boot-starter-security`: Spring Security 核心。
*   `spring-boot-starter-data-redis`: 用于存储 RefreshToken 和黑名单。
*   `redisson-spring-boot-starter`: 用于实现布隆过滤器。
*   `jjwt-api`, `jjwt-impl`, `jjwt-jackson`: JSON Web Token 的生成与解析。
*   `mybatis-plus-spring-boot3-starter`: 数据库操作（可替换为你熟悉的 ORM）。

### 2. 用户实体与数据库设计
在你的用户表中，至少需要包含以下字段：
*   `username` (唯一)
*   `password` (加密存储)
*   `status` (用户状态，用于封禁)
*   `pv` (Password Version，版本号，用于强制下线)

### 3. JWT 工具类封装
编写一个 `JwtUtils`，实现以下核心功能：
*   `generateToken`: 生成短效 AccessToken，并将用户的 `pv` 放入 Claims（载荷）中。
*   `generateRefreshToken`: 生成长效 RefreshToken。
*   `extractUsername` / `extractPv` / `extractExpiration`: 从 Token 中解析关键信息。
*   `isTokenValid`: 验证 Token 签名和是否过期。

### 4. 核心安全配置 (SecurityConfig)
*   配置 `SecurityFilterChain`，禁用 CSRF 和 Session（`SessionCreationPolicy.STATELESS`），因为我们完全依赖 JWT。
*   配置白名单（如 `/api/auth/**` 等登录注册接口），其余接口全部拦截。
*   将自定义的 `JwtAuthenticationFilter` 添加到 `UsernamePasswordAuthenticationFilter` 之前。

### 5. 自定义认证过滤器 (JwtAuthenticationFilter)
这是整个架构的心脏，你需要在这个 Filter 的 `doFilterInternal` 中实现以下逻辑：
1.  **提取 Token**：从 `Authorization` Header 中提取 `Bearer ` 后的 Token。
2.  **黑名单校验**：
    *   先查布隆过滤器，若不存在直接放行。
    *   若存在，再去 Redis 查询该 Token 是否存在于黑名单中，如果在，直接返回 401。
3.  **解析 Token**：提取 `username` 并查询数据库（或缓存）获取当前 `UserDetails`。
4.  **状态校验**：检查用户 `status` 是否被禁用。
5.  **版本号校验**：提取 Token 中的 `pv`，与数据库中查出的最新 `pv` 比对，若不一致则判定 Token 失效（已强制下线）。
6.  **上下文注入**：校验全部通过后，将用户信息塞入 `SecurityContextHolder` 和自定义的 `ThreadLocal`（如 `UserContext`）中，最后放行（`filterChain.doFilter`）。
7.  **异常捕获**：务必使用 `try-catch` 捕获 `ExpiredJwtException`，以便返回优雅的 401 JSON 响应，触发前端的无感刷新。并且在 `finally` 块中清理 `ThreadLocal` 防内存泄漏。

### 6. 核心业务逻辑
*   **登录**：校验密码 -> 查询/初始化用户 `pv` -> 生成双 Token -> 将 RefreshToken 存入 Redis -> 返回双 Token 给前端。
*   **无感刷新**：接收 RefreshToken -> 校验其是否与 Redis 中存储的一致 -> 重新生成双 Token 并覆盖 Redis -> 返回新 Token。
*   **登出**：接收当前 AccessToken 和 RefreshToken -> 从 Redis 删除 RefreshToken -> 将 AccessToken 加入 Redis 黑名单并设置 TTL 为其剩余有效期 -> 同步加入布隆过滤器。
*   **修改密码/踢人**：修改密码逻辑 -> 将数据库用户 `pv` 增加 -> 删除 Redis 中的 RefreshToken。

### 7. 前端 Axios 拦截器适配
*   **请求拦截**：在 Header 中自动带上 `Authorization: Bearer <AccessToken>`。
*   **响应拦截**：
    *   捕获 `401` 状态码。
    *   利用一个布尔变量（如 `isRefreshing`）防止并发请求导致多次刷新。
    *   挂起后续 401 请求（放入队列），携带本地的 RefreshToken 调用后端的 `/refresh` 接口。
    *   刷新成功后，更新本地 Token，并重放队列中的请求。如果刷新失败，则强制跳转到登录页。

## 📝 配置文件说明 (application.yml)

由于安全原因（如密码、密钥），通常在开源或提交代码至 GitHub 时，会使用 `.gitignore` 忽略后端的配置文件 `src/main/resources/application.yml`。

如果你 clone 了本项目，请在 `src/main/resources/` 目录下手动创建 `application.yml` 文件，并参考以下模板填入你自己的环境配置：

```yaml
server:
  port: 8080 # 服务启动端口

spring:
  application:
    name: spark-security
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    # 请修改为你的 MySQL 连接地址和库名
    url: jdbc:mysql://localhost:3306/spark_security?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root     # 你的数据库用户名
    password: serect   # 你的数据库密码
  data:
    redis:
      host: localhost  # Redis 服务器地址
      port: 6379       # Redis 端口号
      password:        # Redis 密码，若无则留空
  redis:
    # 存入 Redis 的 Key 前缀，建议使用应用名作为隔离
    key-pre: ${spring.application.name}:auth

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl # 开启控制台打印 SQL 语句日志
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: auto # 数据库主键自增策略

bloom-filter:
  expected-insertions: 1000 # 期望插入数量
  false-probability: 0.01   # 误判率

jwt:
  # 务必修改为一个足够复杂且至少长度为 256-bit 的 Base64 编码密钥！
  # 可以使用网站在线生成: https://www.allkeysgenerator.com/Random/Security-Encryption-Key-Generator.aspx
  secret: 404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
  
  # AccessToken 的过期时间 (单位: 毫秒)。建议设置得短一些，比如半小时。
  # 示例中的 1800000ms = 30 分钟
  expiration: 1800000 
  
  # RefreshToken 的过期时间 (单位: 毫秒)。建议设置得长一些，比如七天。
  # 示例中的 604800000ms = 7 天
  refresh-expiration: 604800000 
```