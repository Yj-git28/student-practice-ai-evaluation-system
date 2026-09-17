# student-practice-ai-evaluation-system
大学生软件实训教学AI检查评价系统

## ✨ 系统功能
1. 支持本地大模型部署或云端大模型调用，提供PC Web可视化界面
2. 实训成果上传解析：支持Word/PDF/截图多文件上传，大模型自动提取内容
3. AI智能核查：校验实训要求、步骤完整性，识别逻辑漏洞，降低人工成本
4. 多维度评价管理：自定义评价指标与权重，AI自动评分 + 教师主观打分
5. 报表导出：生成实训评价报表，支持PDF/Excel导出，内置可视化图表

## 🛠️ 技术脚手架（Tech Stack）
> 前后端分离架构
- 前端脚手架：**Vue3 + Vite + Element Plus + Pinia**
- 后端脚手架：**SpringBoot 3.x（或FastAPI Python）**
- 数据库：MySQL8.0
- AI模块：支持本地Ollama部署大模型 / 调用云端大模型API
- 文件处理：POI（Word/PDF解析）、图片OCR
- 报表：ECharts可视化、POI导出Excel、iText导出PDF
  
## 📂 项目框架目录结构
```
student-practice-ai-evaluation-system
├── frontend/ # 前端 Vue 脚手架工程
│ ├── src/
│ │ ├── api/ # 接口请求
│ │ ├── components/ # 公共组件（文件上传、评分表单、图表）
│ │ ├── views/ # 页面：成果上传、AI 核查、评价管理、报表页面
│ │ ├── store/ # Pinia 状态管理
│ │ └── router/ # 路由
│ ├── vite.config.js
│ └── package.json
├── backend/ # 后端服务脚手架
│ ├── src/main/java/com/ai/eval
│ │ ├── controller/ # 接口控制器
│ │ ├── service/ # 业务逻辑：文件解析、AI 核查、评价计算
│ │ ├── model/ # 数据库实体、指标模型
│ │ ├── ai/ # 大模型模块：本地模型 / 云端 API 调用
│ │ └── util/ # 文件解析、OCR、报表工具类
│ └── pom.xml
├── docs/ # 项目文档、需求说明书、数据库设计
├── .gitignore
└── README.md
```
