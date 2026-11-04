# syntax=docker/dockerfile:1

# ---------- 阶段 1：Vue 前端构建 ----------
# 产物输出路径由 frontend/vite.config.js 决定：../src/main/resources/static
FROM node:20-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# ---------- 阶段 2：Maven 打包可执行 jar ----------
FROM maven:3.9-eclipse-temurin-11 AS build
WORKDIR /app
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline || echo "依赖预下载失败，忽略并继续"
COPY src/main ./src/main
# 清掉仓库里提交的旧前端产物，替换为前端阶段新构建的结果
RUN rm -rf src/main/resources/static
COPY --from=frontend /app/src/main/resources/static ./src/main/resources/static
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -Dmaven.test.skip=true package

# ---------- 阶段 3：运行镜像 ----------
FROM eclipse-temurin:11-jre
WORKDIR /app
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_TOOL_OPTIONS="-Xms256m -Xmx512m -Duser.timezone=Asia/Shanghai"
COPY --from=build /app/target/*.jar /app/app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
