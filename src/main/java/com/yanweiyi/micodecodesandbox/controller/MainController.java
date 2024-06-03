package com.yanweiyi.micodecodesandbox.controller;


import com.yanweiyi.micodecodesandbox.model.ExecuteCodeRequest;
import com.yanweiyi.micodecodesandbox.model.ExecuteCodeResponse;
import com.yanweiyi.micodecodesandbox.sandbox.docker.DockerCodeSandbox;
import com.yanweiyi.micodecodesandbox.sandbox.docker.factory.DockerCodeSandboxFactory;
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

    private DockerCodeSandbox dockerCodeSandbox;

    @GetMapping("/health")
    public String healthCheck() {
        return "ok";
    }

    @PostMapping("/executeCode")
    public ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest,
                                           HttpServletRequest request,
                                           HttpServletResponse response) {
        // 请求参数校验
        if (executeCodeRequest == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        // 权限认证
        String authHeader = request.getHeader(AUTH_REQUEST_HEADER);
        if (!AUTH_REQUEST_SECRET.equals(authHeader)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        // 根据语言选择代码沙箱
        DockerCodeSandbox dockerCodeSandbox = DockerCodeSandboxFactory
                .getCodeSandbox(executeCodeRequest.getLanguage());
        if (dockerCodeSandbox == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        return dockerCodeSandbox.executeCode(executeCodeRequest);
    }

}
