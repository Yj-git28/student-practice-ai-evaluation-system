package org.example.backend;

import org.example.backend.security.PublicApi;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PublicApi
@RestController
@RequestMapping("/test")
public class TestController {

    @GetMapping
    public String helloTest() {
        return "✅ 前后端联调成功！MySQL版本后端";
    }
}
