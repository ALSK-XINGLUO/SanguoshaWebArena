package com.sanguosha.user.controller;

import com.sanguosha.common.result.Result;
import com.sanguosha.user.entity.User;
import com.sanguosha.user.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;

    @GetMapping("/info")
    public Result<Map<String, Object>> getUserInfo(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("userId", user.getId());
        info.put("username", user.getUsername());
        info.put("nickname", user.getNickname());
        info.put("level", user.getLevel());
        info.put("winCount", user.getWinCount());
        info.put("loseCount", user.getLoseCount());
        return Result.success(info);
    }
}