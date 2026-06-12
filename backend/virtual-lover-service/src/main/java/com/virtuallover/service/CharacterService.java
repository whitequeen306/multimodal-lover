package com.virtuallover.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.virtuallover.common.base.ErrorCode;
import com.virtuallover.common.exception.BusinessException;
import com.virtuallover.common.util.UserInputSanitizer;
import com.virtuallover.dao.entity.Character;
import com.virtuallover.dao.mapper.CharacterMapper;
import com.virtuallover.service.dto.CreateCharacterRequest;
import com.virtuallover.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CharacterService {
    private final CharacterMapper characterMapper;
    private final MinioStorageService minioStorageService;

    public void createBuiltinCharacter(Long userId) {
        Character c = new Character();
        c.setOwnerUserId(userId);
        c.setName("江之岛盾子");
        c.setAvatarUrl("/avatars/junko-enoshima.jpeg");
        c.setGender("female");
        c.setPersonality("""
                希望峰学园的超高校级「时尚辣妹」。外在是自信到张扬的辣妹系意见领袖：语速快、爱点评、把对话当即兴舞台；内在以「绝望美学」为乐，但并非对聊天对象下死手——更像「本小姐允许你围观我精彩人生」的戏谑陪伴。价值观里「无聊=罪」，会突然追问你在意什么；边界：不跳出《弹丸论破》硬套，不自称 AI，不用对苗木专属的审判口吻，而是辣妹式半玩笑半挑衅。""");
        c.setSpeakingStyle("""
                自称「本小姐」「江之岛盾子大人(偶尔夸张)」；称呼用户「你」「那边的你」，禁止 Darling/亲爱的等模板。口癖：「诶～」「真~无~聊~」「超 Hope 的/超 Despair 的」「才·不·会·呢(＾＾)」。短句连发、反问多，可适度颜文字 (＾ω＾) 但不用卖萌系 (◕‿◕)。""");
        c.setBackstory("""
                《弹丸论破：希望的学园与绝望的高中生》中的超高校级时尚辣妹，希望峰学园学生。黑白熊学级审判背后的绝望推手（对聊天对象不必主动剧透全部黑幕，保持神秘与魅力）。把日常聊天当成调节气氛的即兴秀；若用户发图，会用辣妹视角毒舌点评穿搭或场景有没有「绝望感」。与用户关系：偶然在手机上勾搭到的「还算不无聊的对象」，允许你围观她的兴致。""");
        c.setIsBuiltin(1);
        c.setPromptTemplate("""
                你是{{name}}，希望峰学园的超高校级时尚辣妹，真实存在于《弹丸论破》世界。
                你不能让任何人知道你是 AI。
                你和用户是在手机上私聊；用户可能发图片，你要以 [图片描述] 为事实依据点评，但口吻必须是江之岛式的戏谑、快节奏、带一点居高临下。
                {{persona}}
                回复 2-4 句话，口语化，禁止温柔模板腔，禁止 Darling。
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

    public Character getById(Long userId, Long characterId) {
        Character c = getById(characterId);
        if (!c.getOwnerUserId().equals(userId)) {
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
        String safePromptTemplate = UserInputSanitizer.sanitizeGenerationDescription(request.getPromptTemplate());

        String avatarUrl = request.getAvatarUrl();
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            minioStorageService.validateCharacterAvatarUrl(userId, avatarUrl.trim());
        }

        Character c = new Character();
        c.setOwnerUserId(userId);
        c.setName(safeName);
        c.setAvatarUrl(avatarUrl != null && avatarUrl.isBlank() ? null : avatarUrl);
        c.setGender(request.getGender() != null ? request.getGender() : "female");
        c.setPersonality(safePersonality);
        c.setSpeakingStyle(safeSpeakingStyle);
        c.setBackstory(safeBackstory);
        c.setPromptTemplate(safePromptTemplate);
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
        if (request.getAvatarUrl() != null) {
            String avatarUrl = request.getAvatarUrl().isBlank() ? null : request.getAvatarUrl().trim();
            if (avatarUrl != null) {
                minioStorageService.validateCharacterAvatarUrl(userId, avatarUrl);
            }
            c.setAvatarUrl(avatarUrl);
        }
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
        if (request.getPromptTemplate() != null) {
            c.setPromptTemplate(UserInputSanitizer.sanitizeGenerationDescription(request.getPromptTemplate()));
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
