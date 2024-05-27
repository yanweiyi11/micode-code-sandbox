package com.yanweiyi.micodecodesandbox.model;

import lombok.Data;

import java.util.List;

/**
 * @author yanweiyi
 */
@Data
public class ExecuteCodeRequest {
    /**
     * 输入用例
     */
    List<String> inputList;

    /**
     * 待执行代码
     */
    String code;

    /**
     * 代码语言
     */
    String language;
}
