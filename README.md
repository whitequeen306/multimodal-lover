# MultiModal Lover — 多模态虚拟恋人

二次元风格的多模态AI虚拟恋人。支持文本+图片对话，AI能"看到"图片内容并给出温暖关怀。

## 技术栈
- **前端**: Vue 3 + Element Plus + Pinia
- **后端**: Spring Boot 3.3 + MyBatis-Plus + Sa-Token
- **AI**: 阿里云 DashScope（VL: qwen3-vl-plus, Chat: qwen-plus）
- **存储**: 七牛云 Kodo
- **数据库**: MySQL 8 + Redis

## 快速启动

### 1. 配置环境变量
```bash
cp .env.example .env
# 编辑 .env，填入 DASHSCOPE_API_KEY 和七牛云配置
```

### 2. Docker 启动
```bash
docker compose up -d --build
```

访问 http://localhost:8080

### 3. 本地开发

**后端**:
```bash
cd backend
# 确保 MySQL 和 Redis 运行中
mvn -pl virtual-lover-app spring-boot:run
```

**前端**:
```bash
cd frontend
npm install
npm run dev
```

访问 http://localhost:5173

## API 文档
启动后访问 http://localhost:8080/doc.html

## 项目结构
```
├── backend/              # Spring Boot 多模块后端
│   ├── virtual-lover-common/     # 工具类
│   ├── virtual-lover-dao/        # 数据库
│   ├── virtual-lover-storage/    # 七牛云Kodo
│   ├── virtual-lover-security/   # 鉴权
│   ├── virtual-lover-ai/         # AI 对话+识图
│   ├── virtual-lover-service/    # 业务逻辑
│   ├── virtual-lover-web/        # Controller
│   └── virtual-lover-app/        # 启动模块
├── frontend/             # Vue 3 前端
├── docker-compose.yml
└── .env
```
