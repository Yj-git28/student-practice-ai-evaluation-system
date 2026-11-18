package org.example.backend.system.service;

import org.example.backend.system.mapper.SysConfigMapper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Optional;

/**
 * 管理员注册邀请码：存放在 sys_config，由系统生成，管理员可在设置里查看与重置。
 * 规则：一次性使用（注册成功即换新）+ 2 小时有效期（过期自动换新）。
 */
@Service
public class InviteCodeService {

    private static final String KEY = "admin_invite_code";
    private static final String EXPIRE_KEY = "admin_invite_code_expire_at";
    private static final long TTL_MILLIS = 2 * 60 * 60 * 1000L; // 2 小时
    private static final char[] ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray(); // 去掉易混淆的 I/O/0/1

    private final SysConfigMapper configMapper;
    private final SecureRandom random = new SecureRandom();

    public InviteCodeService(SysConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    /** 邀请码 + 过期时间戳（毫秒） */
    public record InviteCode(String code, long expireAt) {
    }

    /** 当前邀请码：不存在或已过期时自动生成新码 */
    public InviteCode current() {
        Optional<String> code = configMapper.select(KEY);
        long expireAt = configMapper.select(EXPIRE_KEY).map(this::parseLong).orElse(0L);
        if (code.isEmpty() || expireAt <= System.currentTimeMillis()) {
            return reset();
        }
        return new InviteCode(code.get(), expireAt);
    }

    /** 重置邀请码：新码重新计时 2 小时，旧码立即失效 */
    public InviteCode reset() {
        String code = generate();
        long expireAt = System.currentTimeMillis() + TTL_MILLIS;
        configMapper.upsert(KEY, code);
        configMapper.upsert(EXPIRE_KEY, String.valueOf(expireAt));
        return new InviteCode(code, expireAt);
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L; // 脏数据按已过期处理
        }
    }

    private String generate() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
