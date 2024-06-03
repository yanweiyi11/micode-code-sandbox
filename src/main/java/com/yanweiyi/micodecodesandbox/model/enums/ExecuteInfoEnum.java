package com.yanweiyi.micodecodesandbox.model.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 执行结果状态枚举
 */
@Getter
public enum ExecuteInfoEnum {

    SUCCESS("执行成功", 1),
    COMPILE_ERROR("编译失败", 2),
    EXECUTION_TIMEOUT("执行超时", 3),
    EXECUTION_ERROR("执行错误", 4),
    SYSTEM_ERROR("系统错误", 5),
    MEMORY_OVERFLOW("内存溢出", 6);

    private final String message;

    private final Integer value;

    ExecuteInfoEnum(String message, Integer value) {
        this.message = message;
        this.value = value;
    }

    public Boolean equalsValue(Integer eqValue) {
        return value.equals(eqValue);
    }

    /**
     * 获取代码列表
     *
     * @return 代码列表
     */
    public static List<Integer> getValues() {
        return Arrays.stream(values()).map(status -> status.value).collect(Collectors.toList());
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举代码
     * @return 对应的枚举值，如果没有找到返回 null
     */
    public static ExecuteInfoEnum getEnumByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (ExecuteInfoEnum statusEnum : ExecuteInfoEnum.values()) {
            if (statusEnum.value.equals(value)) {
                return statusEnum;
            }
        }
        return null;
    }

}