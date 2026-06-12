# Publish GitHub PR records after: gh auth login
# Resets master to init, then creates & merges PRs #1-#11 in order.
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

gh auth status | Out-Null

$init = "53737b9"
$prs = @(
    @{ N=1; Branch="feat/01-maven-scaffold"; Title="feat: Maven 多模块脚手架"; Body="## 功能描述`n初始化 Spring Boot 3.3 多模块 Maven 工程骨架。`n`n## 实现思路`n父 POM 统一依赖与模块声明。`n`n## 测试方式`n``````bash`ncd backend && mvn -q compile`n``````" },
    @{ N=2; Branch="feat/02-common-dao"; Title="feat: common 与 dao 模块（Flyway V1）"; Body="## 功能描述`n统一响应封装、实体与 Mapper、Flyway V1 建表。`n`n## 实现思路`nResult/ErrorCode/BusinessException；User/Character/Conversation/Message。`n`n## 测试方式`n``````bash`ncd backend && mvn -q compile -pl virtual-lover-common,virtual-lover-dao`n``````" },
    @{ N=3; Branch="feat/03-security-storage"; Title="feat: 认证安全与对象存储模块"; Body="## 功能描述`nSa-Token + BCrypt；MinIO 图片上传。`n`n## 实现思路`nSaTokenConfig + MinioStorageService。`n`n## 测试方式`n``````bash`ncd backend && mvn -q compile -pl virtual-lover-security,virtual-lover-storage`n``````" },
    @{ N=4; Branch="feat/04-ai-module"; Title="feat: AI 模块（PromptBuilder、VL、Chat SSE）"; Body="## 功能描述`nDashScope VL 识图与 Chat 流式对话。`n`n## 实现思路`nSpring AI 兼容客户端 + PromptBuilder。`n`n## 测试方式`n``````bash`ncd backend && mvn -q compile -pl virtual-lover-ai`n``````" },
    @{ N=5; Branch="feat/05-service-layer"; Title="feat: Service 层（Auth/Character/Conversation SSE）"; Body="## 功能描述`n注册登录、角色 CRUD、SSE 发消息。`n`n## 实现思路`nAuthService / CharacterService / ConversationService。`n`n## 测试方式`n``````bash`ncd backend && mvn -q compile -pl virtual-lover-service`n``````" },
    @{ N=6; Branch="feat/06-web-app"; Title="feat: REST 控制器与 Spring Boot 启动模块"; Body="## 功能描述`nAuth/Character/Conversation REST API；不含真实密钥。`n`n## 实现思路`nvirtual-lover-web + virtual-lover-app。`n`n## 测试方式`n``````bash`ncd backend && mvn -q package -pl virtual-lover-app -am -DskipTests`n``````" },
    @{ N=7; Branch="feat/07-frontend"; Title="feat: Vue 3 前端（登录/角色/聊天）"; Body="## 功能描述`nVue 3 + Element Plus 聊天 UI。`n`n## 实现思路`nVite + Axios + SSE ChatPage。`n`n## 测试方式`n``````bash`ncd frontend && npm install && npm run build`n``````" },
    @{ N=8; Branch="feat/08-docker-deploy"; Title="feat: Docker Compose 一键部署"; Body="## 功能描述`nMySQL/Redis/MinIO/后端/Nginx 前端一键启动。`n`n## 实现思路`n多阶段 Dockerfile + docker-compose。`n`n## 测试方式`n``````bash`ncp .env.example .env && docker compose up -d --build`n``````" },
    @{ N=9; Branch="fix/09-integration"; Title="fix: 前后端联调与角色人设生成"; Body="## 功能描述`n修复 API/拦截器；AI 生成动漫角色人设。`n`n## 实现思路`nAxios 解包、UTF-8、人设 Prompt。`n`n## 测试方式`n``````bash`ndocker compose up -d --build`n``````" },
    @{ N=10; Branch="feat/10-vision-pipeline"; Title="feat: VL JSON 识图流水线与 49 项测试"; Body="## 功能描述`nVL 纯 JSON 识图 → 场景策略 → Chat；49 条自动化测试。`n`n## 实现思路（原创）`nVisionResultParser、VisionUserIntentDetector、PromptBuilder 场景策略。`n`n## 第三方依赖`nSpring Boot、Spring AI、DashScope、Sa-Token、MyBatis-Plus、Flyway、MinIO。`n`n## 测试方式`n``````bash`ncd backend && mvn test`n``````" },
    @{ N=11; Branch="docs/11-readme-config"; Title="docs: README、环境模板与 Git 忽略"; Body="## 功能描述`n主文档、.env.example、.gitignore、CodeGraph 配置。`n`n## 实现思路`nREADME 评审速读 + 30 秒运行说明。`n`n## 测试方式`n按 README 执行 docker compose 可复现演示。" }
)

Write-Host "Resetting origin/master to init $init ..." -ForegroundColor Yellow
git push -f origin "${init}:master"
git fetch origin
git checkout -f master
git reset --hard origin/master

foreach ($pr in $prs) {
    Write-Host "PR #$($pr.N): $($pr.Title)" -ForegroundColor Cyan
    $bodyFile = New-TemporaryFile
    Set-Content -Path $bodyFile -Value $pr.Body -Encoding utf8
    gh pr create --base master --head $pr.Branch --title $pr.Title --body-file $bodyFile
    Remove-Item $bodyFile -Force
    gh pr merge --merge --delete-branch=false
    git pull origin master
}

Write-Host "All 11 PRs published on GitHub." -ForegroundColor Green
