package com.yanweiyi.micodecodesandbox.sandbox.docker.factory;

import com.yanweiyi.micodecodesandbox.sandbox.docker.DockerCodeSandbox;
import com.yanweiyi.micodecodesandbox.sandbox.docker.impl.JavaDockerCodeSandbox;
import com.yanweiyi.micodecodesandbox.sandbox.docker.impl.PythonDockerCodeSandbox;

public class DockerCodeSandboxFactory {
    public static DockerCodeSandbox getCodeSandbox(String language) {
        switch (language) {
            case "java":
                return new JavaDockerCodeSandbox();
            case "python":
                return new PythonDockerCodeSandbox();
            default:
                return null;
        }
    }
}