package org.example.backend.system.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** B9：@SysLog 切面能真正织入方法并落库（同时验证 AspectJ 与 Spring AOP 版本兼容） */
class SysLogAspectTest {

    static class Target {
        @SysLog(type = "TEST", content = "测试操作")
        public String work(String arg) {
            return "ok:" + arg;
        }
    }

    @Test
    @DisplayName("标注 @SysLog 的方法成功执行后写入 sys_log")
    void writesLogAfterAnnotatedMethod() {
        SysLogService service = mock(SysLogService.class);
        AspectJProxyFactory factory = new AspectJProxyFactory(new Target());
        factory.addAspect(new SysLogAspect(service));
        Target proxy = factory.getProxy();

        assertThat(proxy.work("abc")).isEqualTo("ok:abc");

        verify(service).record(isNull(), eq("TEST"), contains("成功"));
    }
}
