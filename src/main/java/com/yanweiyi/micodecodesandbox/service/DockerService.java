package com.yanweiyi.micodecodesandbox.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DockerClientBuilder;
import com.yanweiyi.micodecodesandbox.utils.DockerClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class DockerService {

    public static final String JAVA_IMAGE_NAME = "openjdk:8-alpine";

    private final DockerClient dockerClient = DockerClientUtil.getDockerClient();

    /**
     * 使用 PostConstruct 注解确保该方法在 Bean 初始化后被自动调用，用于拉取 Docker 镜像
     */
    @PostConstruct
    public void pullDockerImageIfNeeded() {
        List<Image> images = dockerClient.listImagesCmd().exec();
        boolean imageExists = images.stream()
                .anyMatch(image -> Arrays.asList(image.getRepoTags()).contains(JAVA_IMAGE_NAME));

        if (!imageExists) {
            log.info("image {} not found. pulling docker image...", JAVA_IMAGE_NAME);
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(JAVA_IMAGE_NAME);
            try (ResultCallback.Adapter<PullResponseItem> callback = new ResultCallback.Adapter<>()) {
                pullImageCmd.exec(callback).awaitCompletion();
                callback.onComplete();
                log.info("docker image {} pulled successfully.", JAVA_IMAGE_NAME);
            } catch (InterruptedException | IOException e) {
                Thread.currentThread().interrupt(); // 重新设置中断状态
                throw new RuntimeException("failed to pull docker image: " + JAVA_IMAGE_NAME, e);
            }
        } else {
            log.info("docker image {} already exists. skipping pull.", JAVA_IMAGE_NAME);
        }
    }
}