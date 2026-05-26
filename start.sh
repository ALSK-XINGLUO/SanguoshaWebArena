#!/bin/bash

# ============================================
# Sanguosha-WebArena 一键启动脚本
# 同时启动后端 Spring Boot 服务器和前端 Vite 开发服务器
# ============================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
echo "========================================"
echo "  Sanguosha-WebArena 启动中..."
echo "========================================"

# 1. 启动后端服务 (Spring Boot via Maven)
echo ""
echo "[1/2] 启动后端服务器 (Spring Boot)..."
cd "$SCRIPT_DIR/sanguosha-webarena-server"

# 在后台运行 Maven
mvn spring-boot:run -q > /tmp/sanguosha-server.log 2>&1 &
SERVER_PID=$!
echo "  后端 PID: $SERVER_PID"
echo "  日志: tail -f /tmp/sanguosha-server.log"

# 等待后端启动完成 (最长等待 60 秒)
echo "  等待后端启动..."
for i in $(seq 1 60); do
  if curl -s http://localhost:8080/api/auth/login > /dev/null 2>&1; then
    echo "  后端启动成功! (http://localhost:8080)"
    break
  fi
  if ! kill -0 $SERVER_PID 2>/dev/null; then
    echo "  [错误] 后端进程异常退出，查看日志:"
    tail -20 /tmp/sanguosha-server.log
    exit 1
  fi
  sleep 1
done

# 2. 启动前端服务 (Vite Dev Server)
echo ""
echo "[2/2] 启动前端开发服务器 (Vite)..."
cd "$SCRIPT_DIR/sanguosha-webarena-client"

# 先确保依赖已安装
if [ ! -d "node_modules" ]; then
  echo "  安装前端依赖..."
  npm install
fi

npm run dev > /tmp/sanguosha-client.log 2>&1 &
CLIENT_PID=$!
echo "  前端 PID: $CLIENT_PID"
echo "  日志: tail -f /tmp/sanguosha-client.log"

# 短暂等待确保前端启动
sleep 2

# 保存 PID 以供关闭脚本使用
echo "$SERVER_PID" > /tmp/sanguosha-server.pid
echo "$CLIENT_PID" > /tmp/sanguosha-client.pid

echo ""
echo "========================================"
echo "  Sanguosha-WebArena 启动完成!"
echo "========================================"
echo ""
echo "  前端地址: http://localhost:5173"
echo "  后端地址: http://localhost:8080"
echo ""
echo "  停止服务: ./stop.sh"
echo "========================================"