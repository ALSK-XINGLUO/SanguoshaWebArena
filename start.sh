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

# 启动后端
echo ""
echo "[1/2] 启动后端服务器 (Spring Boot)..."
"$SCRIPT_DIR/start-server.sh"

# 启动前端
echo ""
echo "[2/2] 启动前端开发服务器 (Vite)..."
"$SCRIPT_DIR/start-client.sh"

echo ""
echo "========================================"
echo "  Sanguosha-WebArena 启动完成!"
echo "========================================"
echo ""
echo "  前端地址: http://localhost:5173"
echo "  后端地址: http://localhost:8080"
echo ""
echo "  单独启动后端: ./start-server.sh"
echo "  单独启动前端: ./start-client.sh"
echo "  停止服务:    ./stop.sh"
echo "========================================"