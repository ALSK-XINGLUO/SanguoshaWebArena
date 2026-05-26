-- 创建数据库
CREATE DATABASE IF NOT EXISTS sanguosha DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE sanguosha;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码(SHA-256)',
    `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
    `level` INT DEFAULT 1 COMMENT '等级',
    `win_count` INT DEFAULT 0 COMMENT '胜利场次',
    `lose_count` INT DEFAULT 0 COMMENT '失败场次',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';