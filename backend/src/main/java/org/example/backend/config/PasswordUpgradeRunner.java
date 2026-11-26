package org.example.backend.config;
import org.example.backend.system.entity.SysUser;
import org.example.backend.system.mapper.SysUserMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordUpgradeRunner implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final PasswordEncoder encoder;

    public PasswordUpgradeRunner(SysUserMapper userMapper, PasswordEncoder encoder) {
        this.userMapper = userMapper;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (SysUser u : userMapper.selectAllForPasswordUpgrade()) {
            String pwd = u.getPassword();
            // BCrypt 密文固定以 $2a$ / $2b$ / $2y$ 开头，已经是密文就跳过
            if (pwd == null || pwd.isBlank()) continue;
            if (pwd != null && (pwd.startsWith("$2a$") || pwd.startsWith("$2b$") || pwd.startsWith("$2y$"))) {
                continue;
            }
            userMapper.updatePassword(u.getUserId(), encoder.encode(pwd));
        }
    }
}

//跑完确认：
//
//        ```sql
//        SELECT user_id, username, LEFT(password, 7) AS prefix FROM sys_user;
//-- 期望全部是 $2a$10$