package com.virtuallover.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.virtuallover.common.base.ErrorCode;
import com.virtuallover.common.exception.BusinessException;
import com.virtuallover.common.util.UserInputSanitizer;
import com.virtuallover.dao.entity.User;
import com.virtuallover.dao.mapper.UserMapper;
import com.virtuallover.security.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final CharacterService characterService;

    public String register(String username, String password, String nickname) {
        // Sanitize user input
        String safeUsername = UserInputSanitizer.sanitizeGenerationDescription(username);
        String safeNickname = nickname != null
                ? UserInputSanitizer.sanitizeGenerationDescription(nickname)
                : safeUsername;

        User existing = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, safeUsername));
        if (existing != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名已存在");
        }
        User user = new User();
        user.setUsername(safeUsername);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(safeNickname);
        userMapper.insert(user);

        // 自动创建内置角色
        characterService.createBuiltinCharacter(user.getId());

        StpUtil.login(user.getId());
        return StpUtil.getTokenValue();
    }

    public String login(String username, String password) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误，请检查后重试");
        }
        StpUtil.login(user.getId());
        return StpUtil.getTokenValue();
    }

    public User getCurrentUser() {
        long userId = StpUtil.getLoginIdAsLong();
        return userMapper.selectById(userId);
    }
}
