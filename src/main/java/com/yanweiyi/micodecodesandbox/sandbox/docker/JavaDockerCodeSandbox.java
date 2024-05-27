package com.yanweiyi.micodecodesandbox.sandbox.docker;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.yanweiyi.micodecodesandbox.model.ExecuteCodeRequest;
import com.yanweiyi.micodecodesandbox.model.ExecuteCodeResponse;
import com.yanweiyi.micodecodesandbox.model.ExecuteMessage;
import com.yanweiyi.micodecodesandbox.model.enums.ResponseStatusEnum;
import com.yanweiyi.micodecodesandbox.service.DockerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;

import javax.annotation.Resource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author yanweiyi
 */
@Slf4j
public class JavaDockerCodeSandbox {

    // 代码临时存储目录
    private static final String TEMP_CODE_DIRECTORY = "tempCode";

    // 用户代码文件名
    private static final String USER_JAVA_CLASS_NAME = "Main";

    // 超时控制时长（毫秒）
    private static final long TIMEOUT_MILLISECONDS = 5000L;

    // 最大内存限制（字节）
    private static final long MAX_MEMORY_BYTES = 100 * 1024 * 1024;

    // 当前项目目录
    private static final String PROJECT_DIRECTORY;

    // 临时代码存储目录完整路径
    private static final String TEMP_CODE_FULL_PATH;

    @Resource
    private DockerClient dockerClient;

    static {
        PROJECT_DIRECTORY = System.getProperty("user.dir");
        TEMP_CODE_FULL_PATH = PROJECT_DIRECTORY + File.separator + TEMP_CODE_DIRECTORY;
        if (!FileUtil.exist(TEMP_CODE_FULL_PATH)) {
            FileUtil.mkdir(TEMP_CODE_FULL_PATH); // 创建代码目录
        }
    }

    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        // 获取输入用例、用户代码和编程语言
        List<String> inputList = executeCodeRequest.getInputList();
        String userCode = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();

        // 将用户代码保存到文件中
        String userCodeDirectory = TEMP_CODE_FULL_PATH + File.separator + UUID.randomUUID();
        String userJavaFilePath = userCodeDirectory + File.separator + USER_JAVA_CLASS_NAME + ".java";
        Path userJavaPath = Paths.get(userJavaFilePath);

        // 编译用户代码，生成 class 文件
        Process compileProcess = null;
        try {
            Files.write(userJavaPath, userCode.getBytes(StandardCharsets.UTF_8));
            ProcessBuilder processBuilder = new ProcessBuilder("javac", "-encoding", "UTF-8", userJavaFilePath);
            compileProcess = processBuilder.start();
            int exitCode = compileProcess.waitFor();
            if (exitCode != 0) {
                String compileErrorOutput = getOutputString(compileProcess.getErrorStream());
                throw new RuntimeException("Compilation failed. Error output: " + compileErrorOutput);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error during code execution: " + e.getMessage(), e);
        } finally {
            if (compileProcess != null) {
                compileProcess.destroy();
            }
        }

        // 创建容器
        CreateContainerCmd containerCommand = dockerClient.createContainerCmd(DockerService.IMAGE_NAME);
        // 开启获取 Docker 执行的输入和输出
        containerCommand.withAttachStdin(true);
        containerCommand.withAttachStdout(true);
        containerCommand.withAttachStderr(true);
        // 禁用网络，防止被刷带宽等
        containerCommand.withNetworkDisabled(true);
        // 开启一个交互终端
        containerCommand.withTty(true);
        // 容器配置对象（目录映射、容器内存限制、只读根目录）
        HostConfig hostConfig = containerCommand.getHostConfig();
        if (hostConfig == null) {
            throw new RuntimeException("HostConfig creation failed");
        }
        // 将编译好的文件上传到容器环境（通过将本地文件夹映射到docker的工作目录）
        hostConfig.setBinds(new Bind(userCodeDirectory, new Volume("/app")));
        hostConfig.withMemory(MAX_MEMORY_BYTES);
        // 限制用户不能往根目录写入
        hostConfig.withReadonlyRootfs(true);
        containerCommand.withHostConfig(hostConfig);

        // 启动容器
        CreateContainerResponse createContainerResponse = containerCommand.exec();
        String containerId = createContainerResponse.getId();
        dockerClient.createContainerCmd(containerId).exec();

        // 运行命令，向控制台输入预设的'输入用例'
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        // 开启定时器
        StopWatch stopWatch = new StopWatch();
        for (String inputArg : inputList) {
            // 封装执行消息对象
            ExecuteMessage executeMessage = new ExecuteMessage();

            // 开启内存监控，获取程序运行期间的最大内存占用量
            executeMessage.setMemoryUsed(Long.MIN_VALUE);
            ResultCallback.Adapter<Statistics> statisticsResultCallback = new ResultCallback.Adapter<Statistics>() {
                @Override
                public void onNext(Statistics statistics) {
                    Long currentMemoryUsage = statistics.getMemoryStats().getUsage();
                    if (currentMemoryUsage != null) {
                        Long maxMemoryUsage = executeMessage.getMemoryUsed();
                        executeMessage.setMemoryUsed(Math.max(currentMemoryUsage, maxMemoryUsage));
                    }
                    super.onNext(statistics);
                }
            };

            // 创建命令对象
            ExecCreateCmd execCreateCmd = dockerClient.execCreateCmd(containerId);
            execCreateCmd.withCmd("java", "-cp", "/app", USER_JAVA_CLASS_NAME);
            // 开启获取 Docker 执行的输入和输出
            execCreateCmd.withAttachStdin(true);
            execCreateCmd.withAttachStdout(true);
            execCreateCmd.withAttachStderr(true);
            ExecCreateCmdResponse createCmdResponse = execCreateCmd.exec();
            String createCmdResponseId = createCmdResponse.getId();

            // 将预设的输入用例转换为输入流
            InputStream stdin = new ByteArrayInputStream(inputArg.getBytes());

            // 开始计时
            stopWatch.start();

            // 执行命令对象
            ExecStartCmd execStartCmd = dockerClient.execStartCmd(createCmdResponseId);
            execStartCmd.withStdIn(stdin);

            // 默认超时为 true，等待执行到 onComplete 时，再设置为 false
            executeMessage.setTimeout(true);
            ResultCallback.Adapter<Frame> execStartResultCallback = new ResultCallback.Adapter<Frame>() {
                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    String payload = new String(frame.getPayload());
                    if (StreamType.STDERR.equals(streamType)) {
                        executeMessage.setOutput(payload);
                        log.error("execution error, output results: {}", payload);
                    } else {
                        executeMessage.setErrorOutput(payload);
                        log.info("execution passed, output results: {}", payload);
                    }
                    super.onNext(frame);
                }

                @Override
                public void onComplete() {
                    // 当程序正常执行完成时，会调用此方法，通过此方法来判断程序执行是否超时
                    executeMessage.setTimeout(false);
                    super.onComplete();
                }
            };
            try {
                // 阻塞等待执行完成, 并且设置超时时间
                execStartCmd
                        .exec(execStartResultCallback)
                        .awaitCompletion(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException("Program execution exception: " + e.getMessage(), e);
            }

            // 结束计时
            stopWatch.stop();
            try {
                statisticsResultCallback.close(); // 关闭内存监控，不使用 try-catch 会报错
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            executeMessage.setExecuteTime(stopWatch.getLastTaskTimeMillis());
            executeMessageList.add(executeMessage);
        }
        // 整理执行结果
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<Long> excuteTimeList = new ArrayList<>();
        List<Long> memoryUsedList = new ArrayList<>();
        List<String> outputList = new ArrayList<>();
        // 设置默认状态值为'执行成功'
        executeCodeResponse.setStatus(ResponseStatusEnum.SUCCESS.getValue());

        for (ExecuteMessage executeMessage : executeMessageList) {
            excuteTimeList.add(executeMessage.getExecuteTime());
            memoryUsedList.add(executeMessage.getMemoryUsed());

            if (executeMessage.getTimeout()) { // 判断是否超时

            }
            String errorOutput = executeMessage.getErrorOutput();
            if (StrUtil.isNotBlank(errorOutput)) { // 如果有异常输出，设置状态为'用户代码错误'
                outputList.add(errorOutput);
                executeCodeResponse.setStatus(ResponseStatusEnum.USER_CODE_ERROR.getValue());
                break;
            }
            outputList.add(executeMessage.getOutput());
        }
        executeCodeResponse.setExecuteTimeList(excuteTimeList);
        executeCodeResponse.setMemoryUsedList(memoryUsedList);
        executeCodeResponse.setOutputList(outputList);
        // 清理文件，释放空间
        dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        try {
            Files.deleteIfExists(userJavaPath);
            Files.deleteIfExists(Paths.get(userCodeDirectory));
        } catch (IOException e) {
            throw new RuntimeException("Error cleaning file: " + e.getMessage(), e);
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

}