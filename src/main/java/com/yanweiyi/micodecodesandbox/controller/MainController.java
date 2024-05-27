package com.yanweiyi.micodecodesandbox.controller;


import com.yanweiyi.micodecodesandbox.model.ExecuteCodeRequest;
import com.yanweiyi.micodecodesandbox.model.ExecuteCodeResponse;
import com.yanweiyi.micodecodesandbox.sandbox.docker.JavaDockerCodeSandbox;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author yanweiyi
 */
@RestController("/")
public class MainController {

    /**
     * 鉴权请求头和密钥
     */
    private static final String AUTH_REQUEST_HEADER = "nnnu";

    private static final String AUTH_REQUEST_SECRET = "231510029";

    @Resource
    private JavaDockerCodeSandbox javaDockerCodeSandbox;

    @GetMapping("/health")
    public String healthCheck() {
        return "ok";
    }

    @PostMapping("/executeCode")
    public ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest,
                                           HttpServletRequest request,
                                           HttpServletResponse response) {
        // 基本的认证
        String authHeader = request.getHeader(AUTH_REQUEST_HEADER);
        if (!AUTH_REQUEST_SECRET.equals(authHeader)) {
            response.setStatus(403);
            return null;
        }
        if (executeCodeRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
        return javaDockerCodeSandbox.executeCode(executeCodeRequest);
    }

}
