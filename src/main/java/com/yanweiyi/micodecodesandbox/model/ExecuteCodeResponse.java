package com.yanweiyi.micodecodesandbox.model;

import lombok.Data;

import java.util.List;

/**
 * @author yanweiyi
 */
@Data
public class ExecuteCodeResponse {

     /**
     * 程序输出结果
     */
    private List<String> outputList;

    /**
     * 程序执行消耗的内存量，单位为字节
     */
    private List<Long> memoryUsedList;

    /**
     * 程序执行消耗的时间，单位为毫秒
     */
    private List<Long> executeTimeList;

    /**
     * 执行状态
     */
    private Integer status;
}
