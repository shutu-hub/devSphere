# DevSphere 开发者平台
DevSphere 是一个面向开发者的现代化协作平台，集 **即时通讯 (IM)**、**对象存储**、**API 管理**、**微服务监控** 于一体，采用 **Spring Cloud + Vue3** 的前后端分离架构，支持高并发实时通信与分布式扩展。

## 一、项目特点
+ 基于微服务架构，独立部署、弹性扩展、容错性高
+ 支持私聊、群聊、文件消息、历史记录、在线状态等 IM 能力
+ 集成 OAuth2 + JWT，统一安全认证与接口授权
+ 支持 MinIO 对象存储，提供分片上传与权限控制
+ 支持 API 限流、文档生成、访问控制与统计分析
+ 可扩展的监控系统，支持日志收集与性能监控

## 二、技术栈
### 后端技术
| 分类 | 技术 |
| --- | --- |
| 核心框架 | Spring Boot 3.1.2，Spring Cloud 2022.0.3 |
| 服务注册/配置 | Nacos |
| API 网关 | Spring Cloud Gateway |
| 安全认证 | Spring Security，OAuth2，JWT |
| ORM | MyBatis Plus 3.5.3.2 |
| 消息与缓存 | RabbitMQ，Redis 6+ |
| 数据库 | MySQL 8.0 |
| 实时通信 | Netty 4.1.76.Final + WebSocket |
| 对象存储 | MinIO |
| 构建工具 | Maven 3.8+ |


### 前端技术
| 应用 | 技术 |
| --- | --- |
| 主应用 | Vue3，Vite，TypeScript，Element Plus |
| IM 聊天应用 | Vue3，Vite，TypeScript，Naive UI |


## 三、模块结构概览
```plain
DevSphere
├── shutu-commons                   # 公共依赖与通用工具组件
│   ├── dependencies                # 统一第三方依赖版本
│   ├── security                    # JWT、认证与授权配置
│   ├── dynamic-datasource          # 动态数据源切换
│   ├── swagger                     # 接口文档配置
│   ├── mybatis                     # MyBatis Plus 扩展（分页、多租户等）
│   ├── tools                       # 工具类（加密、时间、字符串、文件等）
│   └── log                         # 日志统一处理
├── shutu-auth                      # 认证授权服务
│   ├── auth-server                 # Token 颁发与 OAuth2 授权
│   └── auth-client                 # 其他服务集成认证
├── shutu-gateway                   # API 网关
├── shutu-admin                     # 服务监控
│   ├── admin-server                # 数据展示、健康监控
│   └── admin-client                # 上报指标
├── shutu-module-message            # 消息/通知模块（异步消息）
├── shutu-module-oss                # 文件上传下载模块（MinIO）
├── devSphere-chat                  # 实时聊天服务（Netty + WS）
└── dev-sphere-frontend-main        # 前端主应用
```

## 四、功能说明
### 1. 用户与权限
+ 用户注册、登录、注销
+ 基于角色的权限控制
+ Token 刷新与无状态认证
+ 个人信息与设置管理

### 2. 即时通讯 IM
+ 私聊与群聊
+ 文本、图片、文件消息
+ 在线状态实时显示
+ 离线消息存储与未读提醒
+ 历史聊天记录分页查询
+ 1v1 与群组音视频通话能力（可扩展）
+  支持发布动态、图片及代码内容，好友可点赞评论并展示个性化技术分享。  

### 3. 文件与对象存储
+ 多类型文件上传/下载
+ MinIO 分片上传与断点续传
+ 文件预览与访问权限控制

### 4. API 管理
+ REST API 文档自动生成
+ 访问频率限制与统计
+ 鉴权与访问控制

### 5. 系统监控
+ 服务健康检查
+ 实例性能指标展示
+ 异常日志收集与分析
+ 可视化监控面板

## 五、页面预览
多模式聊天： 支持好友私聊和群组聊天，消息实时同步。  

![](https://cdn.nlark.com/yuque/0/2025/png/43269348/1764247597480-a743ebd1-7045-4141-a177-701358e15e2b.png)

![](https://cdn.nlark.com/yuque/0/2025/png/43269348/1764247623302-695eb143-1d92-4ba0-9a44-e768d715e9cf.png)

![](https://cdn.nlark.com/yuque/0/2025/png/43269348/1764247710933-f79677cc-e16c-4547-9c56-6c09d8b0a9d3.png)

 实时音视频通话： 稳定流畅的 1v1 和 群聊音视频通话体验。

![](https://cdn.nlark.com/yuque/0/2025/png/43269348/1764247780849-082b9ce6-2b6e-4b86-9ab5-39e29b7865b1.png)

![](https://cdn.nlark.com/yuque/0/2025/png/43269348/1764247846834-e0c3857a-c643-474a-a5b0-435d6fc6e506.png)

群聊音视频通话

![](https://cdn.nlark.com/yuque/0/2025/png/43269348/1764248702729-16754d53-bdc9-4f1a-8b0e-1bc184e223e9.png)

![](https://cdn.nlark.com/yuque/0/2025/png/43269348/1764248712130-c529bbd0-6787-4170-9661-cd884c689628.png)

 朋友圈：用户可以发布技术心得、生活动态，支持点赞、评论及代码块高亮显示。

![](https://cdn.nlark.com/yuque/0/2025/png/43269348/1764247527433-f65f6fe5-8c41-41aa-8901-7decb8712ced.png)

个人中心页面：展示用户信息（待完善）

![](https://cdn.nlark.com/yuque/0/2025/png/43269348/1764250269924-b1bec94e-bc01-4f7c-9995-764f04311acd.png)

### 系统架构图
```latex
┌──────────────────────┐
│   前端应用           │
│  (Vue3 + ElementPlus)│
└─────────┬────────────┘
          │ HTTP/WebSocket
┌─────────▼────────────┐
│   网关服务            │
│  (Spring Cloud Gateway)│
└─────────┬────────────┘
          │
┌─────────▼────────────┐
│   服务注册与配置中心   │
│      (Nacos)         │
└─────┬───────┬────────┘
      │       │
┌─────▼──┐ ┌──▼──────┐
│ 认证服务 │ │ 管理服务 │
│ (Auth) │ │ (Admin) │
└─────┬──┘ └──┬──────┘
      │       │
┌─────▼───────▼──────┐
│   业务服务模块       │
│  - 消息服务         │
│  - 对象存储服务      │
└─────┬──────────────┘
      │
┌─────▼──────────────┐
│   实时聊天服务       │
│   (Netty + WebSocket)│
└────────────────────┘
```

## 六、部署说明
### 环境要求
| 依赖 | 版本 |
| --- | --- |
| JDK | 17+ |
| MySQL | 8.0+ |
| Redis | 6.0+ |
| RabbitMQ | 最新稳定版 |
| MinIO | 最新稳定版 |
| Nacos | 2.x |
| Node.js | 16+ |


### 部署步骤
1. **启动基础服务**

启动 MySQL、Redis、RabbitMQ、MinIO、Nacos。

2. **初始化数据库**

执行 `dev_sphere.sql` 数据库脚本。

3. **修改配置文件**

配置 `bootstrap.yml` 内数据库、Nacos、Redis 等连接信息。

4. **启动后端服务（建议顺序）**

```plain
1. shutu-auth-server
2. shutu-admin-server
3. shutu-module-message
4. shutu-module-oss
5. shutu-gateway
6. devSphere-chat
```

5. **构建前端应用**

主应用：

```plain
npm install
npm run build
```

聊天应用：

```plain
npm install
npm run build
```

### 默认访问地址
| 服务 | 地址 |
| --- | --- |
| 主系统 | [http://localhost:8081](http://localhost:8081) |
| API 文档 | [http://localhost:8081/doc.html](http://localhost:8081/doc.html) |
| Nacos 控制台 | [http://localhost:8848/nacos](http://localhost:8848/nacos) |
| WebSocket 聊天 | ws://localhost:9000 |


## 七、贡献指南
欢迎对 DevSphere 项目进行贡献！在提交 Pull Request 前，请确保:

1. 阅读代码规范文档
2. 编写相应的单元测试
3. 更新相关文档说明
4. 遵守提交信息格式规范

## 八、许可证
本项目采用 Apache License 2.0 开源许可证。

