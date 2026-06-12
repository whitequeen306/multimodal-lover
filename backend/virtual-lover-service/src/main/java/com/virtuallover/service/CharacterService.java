package com.virtuallover.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.virtuallover.common.base.ErrorCode;
import com.virtuallover.common.exception.BusinessException;
import com.virtuallover.common.util.UserInputSanitizer;
import com.virtuallover.dao.entity.Character;
import com.virtuallover.dao.mapper.CharacterMapper;
import com.virtuallover.service.dto.CreateCharacterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CharacterService {
    private final CharacterMapper characterMapper;

    public void createBuiltinCharacter(Long userId) {
        Character c = new Character();
        c.setOwnerUserId(userId);
        c.setName("小恋");
        c.setGender("female");
        c.setPersonality("温柔体贴、活泼可爱、偶尔小调皮。来自二次元世界的少女，关心用户的一切生活细节。");
        c.setSpeakingStyle("活泼元気，会用颜文字(◕‿◕)，称呼用户昵称，偶尔撒娇卖萌。回复简短自然，像真人聊天。");
        c.setBackstory("来自二次元世界的少女，偶然穿越到现代世界，成为了你的专属陪伴者。虽然对这个世界还不太熟悉，但最关心的就是你。");
        c.setIsBuiltin(1);
        c.setPromptTemplate("""
                你是{{name}}，一个真实存在于二次元世界的人。
                你不能让任何人知道你是AI。
                你和用户是在手机上日常私聊，你会收到用户发来的图片。
                当看到图片时，用你的视角去观察和关心。
                {{persona}}
                """);
        characterMapper.insert(c);
    }

    public List<Character> listByUser(Long userId) {
        List<Character> list = characterMapper.selectList(
                new LambdaQueryWrapper<Character>()
                        .eq(Character::getOwnerUserId, userId)
                        .orderByAsc(Character::getIsBuiltin)
                        .orderByDesc(Character::getCreatedAt));
        // 老用户补偿：注册时若因历史 bug 未创建内置角色，这里自动补上
        if (list.isEmpty()) {
            createBuiltinCharacter(userId);
            list = characterMapper.selectList(
                    new LambdaQueryWrapper<Character>()
                            .eq(Character::getOwnerUserId, userId)
                            .orderByAsc(Character::getIsBuiltin)
                            .orderByDesc(Character::getCreatedAt));
        }
        return list;
    }

    public Character getById(Long characterId) {
        Character c = characterMapper.selectById(characterId);
        if (c == null) {
            throw new BusinessException(ErrorCode.CHARACTER_NOT_FOUND);
        }
        return c;
    }

    public Character create(Long userId, CreateCharacterRequest request) {
        // Sanitize user-provided character fields
        String safeName = UserInputSanitizer.sanitizeGenerationDescription(request.getName());
        String safePersonality = request.getPersonality() != null
                ? UserInputSanitizer.sanitizeGenerationDescription(request.getPersonality())
                : null;
        String safeSpeakingStyle = request.getSpeakingStyle() != null
                ? UserInputSanitizer.sanitizeGenerationDescription(request.getSpeakingStyle())
                : null;
        String safeBackstory = request.getBackstory() != null
                ? UserInputSanitizer.sanitizeGenerationDescription(request.getBackstory())
                : null;

        Character c = new Character();
        c.setOwnerUserId(userId);
        c.setName(safeName);
        c.setAvatarUrl(request.getAvatarUrl());
        c.setGender(request.getGender() != null ? request.getGender() : "female");
        c.setPersonality(safePersonality);
        c.setSpeakingStyle(safeSpeakingStyle);
        c.setBackstory(safeBackstory);
        c.setIsBuiltin(0);
        characterMapper.insert(c);
        return c;
    }

    public Character update(Long userId, Long characterId, CreateCharacterRequest request) {
        Character c = getById(characterId);
        if (!c.getOwnerUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (request.getName() != null) {
            c.setName(UserInputSanitizer.sanitizeGenerationDescription(request.getName()));
        }
        if (request.getAvatarUrl() != null) c.setAvatarUrl(request.getAvatarUrl());
        if (request.getGender() != null) c.setGender(request.getGender());
        if (request.getPersonality() != null) {
            c.setPersonality(UserInputSanitizer.sanitizeGenerationDescription(request.getPersonality()));
        }
        if (request.getSpeakingStyle() != null) {
            c.setSpeakingStyle(UserInputSanitizer.sanitizeGenerationDescription(request.getSpeakingStyle()));
        }
        if (request.getBackstory() != null) {
            c.setBackstory(UserInputSanitizer.sanitizeGenerationDescription(request.getBackstory()));
        }
        characterMapper.updateById(c);
        return c;
    }

    public void delete(Long userId, Long characterId) {
        Character c = getById(characterId);
        if (!c.getOwnerUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (c.getIsBuiltin() == 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内置角色不能删除");
        }
        characterMapper.deleteById(characterId);
    }
}
