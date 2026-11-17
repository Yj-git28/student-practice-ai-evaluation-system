package org.example.backend;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 需要可用的 MySQL（172.24.161.195:3306/training_eval）才能启动上下文。
 * 本机网络不可达时用 @Disabled 跳过；在能连库的环境执行 `mvn test -Dtest=BackendApplicationTests`。
 * 业务断言见 PermissionServiceTest / DataScopeResolverTest / TrainingPublishMethodSecurityTest。
 */
@Disabled("需要可用的 MySQL：PasswordUpgradeRunner 启动时会查 sys_user")
@SpringBootTest
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
