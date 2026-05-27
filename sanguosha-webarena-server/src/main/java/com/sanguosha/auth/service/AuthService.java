package com.sanguosha.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sanguosha.auth.dto.LoginRequest;
import com.sanguosha.auth.dto.RegisterRequest;
import com.sanguosha.common.exception.BusinessException;
import com.sanguosha.config.JwtConfig;
import com.sanguosha.user.entity.User;
import com.sanguosha.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final JwtConfig jwtConfig;

    public Map<String, Object> register(RegisterRequest request) {
        // check username exists
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, request.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(400, "用户名已存在");
        }

        // create user
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(encryptPassword(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setLevel(1);
        user.setWinCount(0);
        user.setLoseCount(0);
        userMapper.insert(user);

        // generate token
        String token = jwtConfig.generateToken(user.getId(), user.getUsername());

        Map<String, Object> userData = new HashMap<>();
        userData.put("userId", user.getId());
        userData.put("username", user.getUsername());
        userData.put("nickname", user.getNickname());
        userData.put("level", user.getLevel());
        userData.put("winCount", user.getWinCount());
        userData.put("loseCount", user.getLoseCount());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", userData);
        return result;
    }

    public Map<String, Object> login(LoginRequest request) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, request.getUsername());
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new BusinessException(400, "用户名或密码错误");
        }

        if (!encryptPassword(request.getPassword()).equals(user.getPassword())) {
            throw new BusinessException(400, "用户名或密码错误");
        }

        String token = jwtConfig.generateToken(user.getId(), user.getUsername());

        Map<String, Object> userData = new HashMap<>();
        userData.put("userId", user.getId());
        userData.put("username", user.getUsername());
        userData.put("nickname", user.getNickname());
        userData.put("level", user.getLevel());
        userData.put("winCount", user.getWinCount());
        userData.put("loseCount", user.getLoseCount());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", userData);
        return result;
    }

    private String encryptPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("加密失败", e);
        }
    }
}