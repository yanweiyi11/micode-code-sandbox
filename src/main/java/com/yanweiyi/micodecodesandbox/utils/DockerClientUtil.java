package com.yanweiyi.micodecodesandbox.utils;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DockerClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class DockerClientUtil {

    private static DockerClient dockerClient;

    // 单例模式获取 DockerClient
    public static DockerClient getDockerClient() {
        if (dockerClient == null) {
            dockerClient = DockerClientBuilder.getInstance().build();;
        }
        return dockerClient;
    }
}