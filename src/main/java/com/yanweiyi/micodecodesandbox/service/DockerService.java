package com.yanweiyi.micodecodesandbox.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.model.PullResponseItem;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;

@Service
public class DockerService {

    public static final String IMAGE_NAME = "openjdk:8-alpine";

    @Resource
    private DockerClient dockerClient;

    /**
     * 使用 PostConstruct 注解确保该方法在 Bean 初始化后被自动调用，用于拉取 Docker 镜像
     */
    @PostConstruct
    public void pullDockerImage() {
        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(IMAGE_NAME);
        try (ResultCallback.Adapter<PullResponseItem> callback = new ResultCallback.Adapter<>()) {
            pullImageCmd.exec(callback).awaitCompletion();
            callback.onComplete();
        } catch (InterruptedException | IOException e) {
            Thread.currentThread().interrupt(); // 重新设置中断状态
            throw new RuntimeException("Failed to pull Docker image: " + IMAGE_NAME, e);
        }
    }
}