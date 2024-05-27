package com.yanweiyi.micodecodesandbox.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DockerClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DockerClientConfiguration {

    @Bean
    public DockerClient dockerClient() {
        // 配置连接到远程 Docker 服务
        // DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
        //         .withDockerHost("tcp://159.75.93.145:2375")
        //         .build();

        // 创建 DockerHttpClient
        // ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
        //         .dockerHost(config.getDockerHost())
        //         .sslConfig(config.getSSLConfig())
        //         .build();

        // 创建并返回 DockerClient Bean
        return DockerClientBuilder
                .getInstance()
                .build();
    }
}