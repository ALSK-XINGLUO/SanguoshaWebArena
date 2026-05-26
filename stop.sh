#!/bin/bash

# ============================================
# Sanguosha-WebArena 一键关闭脚本
# 停止后端 Spring Boot 服务器和前端 Vite 开发服务器
# ============================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
echo "========================================"
echo "  Sanguosha-WebArena 关闭中..."
echo "========================================"

# 1. 停止前端服务
echo ""
if [ -f /tmp/sanguosha-client.pid ]; then
  CLIENT_PID=$(cat /tmp/sanguosha-client.pid)
  if kill -0 $CLIENT_PID 2>/dev/null; then
    echo "[1/2] 停止前端开发服务器 (PID: $CLIENT_PID)..."
    kill $CLIENT_PID 2>/dev/null && echo "  前端已停止" || echo "  前端停止失败"
  else
    echo "[1/2] 前端未在运行"
  fi
  rm -f /tmp/sanguosha-client.pid
else
  echo "[1/2] 未找到前端 PID 文件，尝试查找 vite 进程..."
  pkill -f "vite" 2>/dev/null && echo "  已停止 vite 进程" || echo "  未发现 vite 进程"
fi

# 2. 停止后端服务
echo ""
if [ -f /tmp/sanguosha-server.pid ]; then
  SERVER_PID=$(cat /tmp/sanguosha-server.pid)
  if kill -0 $SERVER_PID 2>/dev/null; then
    echo "[2/2] 停止后端服务器 (PID: $SERVER_PID)..."
    kill $SERVER_PID 2>/dev/null && echo "  后端已停止" || echo "  后端停止失败"
  else
    echo "[2/2] 后端未在运行"
  fi
  rm -f /tmp/sanguosha-server.pid
else
  echo "[2/2] 未找到后端 PID 文件，尝试查找 maven/spring 进程..."
  pkill -f "spring-boot" 2>/dev/null && echo "  已停止 Spring Boot 进程" || echo "  未发现 Spring Boot 进程"
fi

echo ""
echo "========================================"
echo "  Sanguosha-WebArena 已关闭"
echo "========================================"