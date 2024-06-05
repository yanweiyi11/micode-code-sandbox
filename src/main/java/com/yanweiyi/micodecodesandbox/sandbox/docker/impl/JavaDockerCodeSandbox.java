package com.yanweiyi.micodecodesandbox.sandbox.docker.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.yanweiyi.micodecodesandbox.model.ExecuteCodeRequest;
import com.yanweiyi.micodecodesandbox.model.ExecuteCodeResponse;
import com.yanweiyi.micodecodesandbox.model.ExecuteMessage;
import com.yanweiyi.micodecodesandbox.model.enums.ExecuteInfoEnum;
import com.yanweiyi.micodecodesandbox.sandbox.docker.DockerCodeSandbox;
import com.yanweiyi.micodecodesandbox.service.DockerService;
import com.yanweiyi.micodecodesandbox.utils.DockerClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author yanweiyi
 */
@Slf4j
@Component
public class JavaDockerCodeSandbox implements DockerCodeSandbox {

    // 代码临时存储目录
    private static final String TEMP_CODE_DIRECTORY = "tempCode";

    // 用户代码文件名
    private static final String USER_JAVA_CLASS_NAME = "Main";

    // 超时控制时长（ms）
    private static final long TIMEOUT_MILLISECONDS = 5000L;

    // 最大内存限制（byte）
    private static final long MAX_MEMORY_BYTE = 1073741824L; // 10 G

    // 当前项目目录
    private static final String PROJECT_DIRECTORY;

    // 临时代码存储目录完整路径
    private static final String TEMP_CODE_FULL_PATH;

    private final DockerClient dockerClient = DockerClientUtil.getDockerClient();

    // 初始化项目路径和临时代码文件夹路径，并创建临时代码目录
    static {
        PROJECT_DIRECTORY = System.getProperty("user.dir");
        TEMP_CODE_FULL_PATH = PROJECT_DIRECTORY + File.separator + TEMP_CODE_DIRECTORY;
        Path tempCodePath = Paths.get(TEMP_CODE_FULL_PATH);
        if (Files.notExists(tempCodePath)) {
            try {
                Files.createDirectories(tempCodePath); // 创建代码目录
            } catch (IOException e) {
                Thread.currentThread().interrupt(); // 重新设置中断状态
                throw new RuntimeException("Failed to create temporary code folder", e);
            }
        }
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        // 获取请求中的输入列表、用户代码
        List<String> inputList = executeCodeRequest.getInputList();
        String userCode = executeCodeRequest.getCode();

        // 准备执行结果响应对象
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();

        // 将用户代码保存到文件中
        String userCodeDirectory = TEMP_CODE_FULL_PATH + File.separator + UUID.randomUUID();
        String userJavaFilePath = userCodeDirectory + File.separator + USER_JAVA_CLASS_NAME + ".java";
        Path userJavaPath = Paths.get(userJavaFilePath);
        Path directoryPath = Paths.get(userCodeDirectory);

        // 保存用户代码到文件系统并编译
        try {
            // 确保代码存放目录存在
            Files.createDirectories(directoryPath);
            // 保存用户代码到文件
            Files.write(userJavaPath, userCode.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.error("error saving user code: {}", e.getMessage());
            executeCodeResponse.setStatus(ExecuteInfoEnum.SYSTEM_ERROR.getValue());
            return executeCodeResponse;
        }

        String containerId = null;
        try {
            // 编译用户提交的 Java 代码
            try {
                ProcessBuilder processBuilder = new ProcessBuilder("javac", "-encoding", "utf-8", userJavaFilePath);
                Process compileProcess = processBuilder.start();
                int exitCode = compileProcess.waitFor();
                if (exitCode != 0) {
                    // 编译失败，处理编译输出的错误信息
                    String compileErrorOutput = getOutputString(compileProcess.getErrorStream());
                    log.error("compilation error, error output: {}", compileErrorOutput);
                    executeCodeResponse.setStatus(ExecuteInfoEnum.COMPILE_ERROR.getValue()); // TODO 编译失败
                    executeCodeResponse.setErrorMessage(getSanitizeErrorMessage(compileErrorOutput));
                    return executeCodeResponse;
                }
                compileProcess.destroy();
            } catch (InterruptedException | IOException e) {
                log.error("error compiling code, .java file location: {}, error: {}", userJavaFilePath, e.getMessage());
                executeCodeResponse.setStatus(ExecuteInfoEnum.COMPILE_ERROR.getValue()); // TODO 编译失败
                executeCodeResponse.setErrorMessage(getSanitizeErrorMessage(e.getMessage()));
                return executeCodeResponse;
            }
            // 创建 Docker 容器配置
            log.info("docker container create");
            CreateContainerCmd containerCommand = dockerClient.createContainerCmd(DockerService.JAVA_IMAGE_NAME);
            // 开启获取 Docker 执行的输入和输出
            containerCommand.withAttachStdin(true);
            containerCommand.withAttachStdout(true);
            containerCommand.withAttachStderr(true);
            // 禁用网络，防止被刷带宽等
            containerCommand.withNetworkDisabled(true);
            // 关闭交互终端
            containerCommand.withTty(true);

            // 容器配置对象（目录映射、容器内存限制、只读根目录）
            HostConfig hostConfig = containerCommand.getHostConfig();
            if (hostConfig == null) {
                log.error("hostConfig creation failed");
                executeCodeResponse.setStatus(ExecuteInfoEnum.SYSTEM_ERROR.getValue()); // TODO 系统错误
                return executeCodeResponse;
            }
            // 将编译好的文件上传到容器环境（通过将本地文件夹映射到docker的工作目录）
            hostConfig.setBinds(new Bind(userCodeDirectory, new Volume("/app")));
            // 设置内存限制
            hostConfig.withMemory(MAX_MEMORY_BYTE);
            // 限制用户不能往根目录写入
            hostConfig.withReadonlyRootfs(true);
            containerCommand.withHostConfig(hostConfig);

            // 启动容器执行用户代码
            CreateContainerResponse createContainerResponse = containerCommand.exec();
            log.info("docker container start");
            containerId = createContainerResponse.getId();
            dockerClient.startContainerCmd(containerId).exec();

            // 执行用户的代码并收集执行信息(包括用例输入、执行时间、内存使用等)
            log.info("start input to the container");
            // 存储每个用例执行的详细信息
            List<ExecuteMessage> executeMessageList = new ArrayList<>();
            // 开启定时器
            StopWatch stopWatch = new StopWatch();
            for (int caseCount = 1; caseCount <= inputList.size(); caseCount++) {
                // 初始化执行信息收集对象
                ExecuteMessage executeMessage = new ExecuteMessage();

                // 默认内存溢出为 false
                executeMessage.setIsMemoryOverflow(false);
                // 开启内存监控，获取程序运行期间的最大内存占用量
                executeMessage.setMemoryUsed(Long.MIN_VALUE);
                StatsCmd statsCmd = dockerClient.statsCmd(containerId);
                ResultCallback.Adapter<Statistics> statisticsResultCallback = new ResultCallback.Adapter<Statistics>() {
                    @Override
                    public void onNext(Statistics statistics) {
                        Long currentMemoryUsage = statistics.getMemoryStats().getUsage();
                        if (currentMemoryUsage != null) {
                            currentMemoryUsage = currentMemoryUsage / 1024; // byte 转换为 kb
                            Long maxMemoryUsage = executeMessage.getMemoryUsed();
                            executeMessage.setMemoryUsed(Math.max(currentMemoryUsage, maxMemoryUsage));
                            // 检查是否超过内存限制
                            if (maxMemoryUsage > MAX_MEMORY_BYTE) {
                                log.error("out of memory");
                                executeMessage.setIsMemoryOverflow(true);
                            }
                        }
                        super.onNext(statistics);
                    }
                };
                statsCmd.exec(statisticsResultCallback);

                // 创建命令对象
                ExecCreateCmd execCreateCmd = dockerClient.execCreateCmd(containerId);
                // 获取输入用例，假设它已经是适当的多行格式
                String inputCases = inputList.get(caseCount - 1);
                // 使用Base64编码，安全传输特殊字符，包括换行符
                String base64EncodedInputCases = Base64.getEncoder().encodeToString(inputCases.getBytes(StandardCharsets.UTF_8));
                // 拼接成完整的命令行字符串，调整 base64 的使用以适配 BusyBox，BusyBox 是一个集合了许多常用 UNIX 工具的轻量级软件
                String command = String.format("echo \"%s\" | base64 -d | java -cp /app %s", base64EncodedInputCases, USER_JAVA_CLASS_NAME);
                // 使用这个变量作为参数来执行命令
                execCreateCmd.withCmd("sh", "-c", command);

                // 开启获取 Docker 执行的输入和输出
                execCreateCmd.withAttachStdin(true);
                execCreateCmd.withAttachStdout(true);
                execCreateCmd.withAttachStderr(true);

                ExecCreateCmdResponse createCmdResponse = execCreateCmd.exec();
                String createCmdResponseId = createCmdResponse.getId();

                // 开始计时
                stopWatch.start();
                // 执行命令对象
                ExecStartCmd execStartCmd = dockerClient.execStartCmd(createCmdResponseId);
                // 默认超时为 true，等待执行到 onComplete 时，再设置为 false
                executeMessage.setIsTimeout(true);
                // 记录执行到第几个用例
                int finalCaseCount = caseCount;
                StringBuilder outputBuilder = new StringBuilder();
                StringBuilder errorBuilder = new StringBuilder();
                ResultCallback.Adapter<Frame> execStartResultCallback = new ResultCallback.Adapter<Frame>() {
                    @Override
                    public void onNext(Frame frame) {
                        StreamType streamType = frame.getStreamType();
                        String payload = new String(frame.getPayload());

                        // 根据流类型记录日志，避免重复记录相同的信息
                        if (StreamType.STDERR.equals(streamType)) {
                            errorBuilder.append(payload);
                            log.error("case {}: execution error, output results: {}", finalCaseCount, payload);
                            return;
                        } else {
                            outputBuilder.append(payload);
                            log.info("case {}: execution passed, output results: {}", finalCaseCount, payload);
                        }
                        super.onNext(frame);
                    }

                    @Override
                    public void onComplete() {
                        // 当程序正常执行完成时，会调用此方法，通过此方法来判断程序执行是否超时
                        log.info("case {}: code not timed out", finalCaseCount);
                        executeMessage.setIsTimeout(false);
                        super.onComplete();
                    }
                };

                // 阻塞等待执行完成, 并且设置超时时间
                try {
                    execStartCmd.exec(execStartResultCallback).awaitCompletion(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    log.error("error occurred during blocking wait: {}", e.getMessage());
                    executeCodeResponse.setStatus(ExecuteInfoEnum.SYSTEM_ERROR.getValue()); // TODO 系统错误
                    return executeCodeResponse;
                }

                // 结束计时
                stopWatch.stop();
                try {
                    statisticsResultCallback.close(); // 关闭内存监控，不使用 try-catch 会报错
                    statsCmd.close();
                } catch (IOException e) {
                    log.error("error closing memory monitoring: {}", e.getMessage());
                    executeCodeResponse.setStatus(ExecuteInfoEnum.SYSTEM_ERROR.getValue()); // TODO 系统错误
                    return executeCodeResponse;
                }
                // 从 outputBuilder 中获取结果并设置
                String output = outputBuilder.toString();
                if (output.endsWith("\n")) {
                    output = output.substring(0, output.length() - 1); // 去重最后一个\n
                }
                executeMessage.setOutput(output);

                // 从 errorBuilder 中获取结果并设置
                String errorOutput = errorBuilder.toString();
                executeMessage.setErrorOutput(errorOutput);

                // 设置代码执行时间
                executeMessage.setTimeUsed(stopWatch.getLastTaskTimeMillis());
                // 收集执行信息添加到列表中
                executeMessageList.add(executeMessage);
            }

            // 整理执行结果并返回
            log.info("organizing execution results...");
            List<Long> excuteTimeList = new ArrayList<>();
            List<Long> memoryUsedList = new ArrayList<>();
            List<String> outputList = new ArrayList<>();

            // 设置默认状态值
            executeCodeResponse.setStatus(ExecuteInfoEnum.SUCCESS.getValue()); // TODO 执行成功

            for (ExecuteMessage executeMessage : executeMessageList) {
                excuteTimeList.add(executeMessage.getTimeUsed());
                memoryUsedList.add(executeMessage.getMemoryUsed());

                if (executeMessage.getIsMemoryOverflow()) { // 判断是否内存溢出
                    executeCodeResponse.setStatus(ExecuteInfoEnum.MEMORY_OVERFLOW.getValue()); // TODO 内存溢出
                    break;
                }

                if (executeMessage.getIsTimeout()) { // 判断是否超时
                    executeCodeResponse.setStatus(ExecuteInfoEnum.EXECUTION_TIMEOUT.getValue()); // TODO 执行超时
                    break;
                }
                String errorOutput = executeMessage.getErrorOutput();
                if (StrUtil.isNotBlank(errorOutput)) { // 判断是否有异常输出
                    executeCodeResponse.setErrorMessage(getSanitizeErrorMessage(errorOutput));
                    executeCodeResponse.setStatus(ExecuteInfoEnum.EXECUTION_ERROR.getValue()); // TODO 执行错误
                    break;
                }
                outputList.add(executeMessage.getOutput());
            }

            executeCodeResponse.setTimeUsedList(excuteTimeList);
            executeCodeResponse.setMemoryUsedList(memoryUsedList);
            executeCodeResponse.setOutputList(outputList);
        } finally {
            // 清理执行环境，移除容器和删除临时文件
            if (StrUtil.isNotBlank(containerId)) {
                log.info("docker container clear");
                dockerClient.stopContainerCmd(containerId).exec();
                dockerClient.removeContainerCmd(containerId).withForce(true).exec();
                try {
                    deleteDirectoryRecursively(directoryPath);
                } catch (IOException e) {
                    log.error("error cleaning files: {}", e.getMessage());
                    executeCodeResponse.setStatus(ExecuteInfoEnum.SYSTEM_ERROR.getValue()); // TODO 系统错误
                }
            }
        }
        return executeCodeResponse;
    }

    /**
     * 把输出流转换为字符串
     */
    private static String getOutputString(InputStream inputStream) throws IOException {
        StringBuilder outputBuilder = new StringBuilder();
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            outputBuilder.append(line);
        }
        inputStream.close();
        return outputBuilder.toString();
    }

    /**
     * 递归清空目录并删除目录
     */
    private static void deleteDirectoryRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            // 使用 DirectoryStream 读取目录内容
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path entry : stream) {
                    // 递归删除
                    deleteDirectoryRecursively(entry);
                }
            }
        }
        // 删除文件或空目录
        Files.deleteIfExists(path);
    }

    /**
     * 错误消息脱敏，防止返回值中带有本机的信息
     */
    private static String getSanitizeErrorMessage(String errorMessage) {
        String regex = "/root/micode-code-sandbox/tempCode/[0-9a-fA-F\\-]+/";
        return ReUtil.replaceAll(errorMessage, regex, "");
    }
}