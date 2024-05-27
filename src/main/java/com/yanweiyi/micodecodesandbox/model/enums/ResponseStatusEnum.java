package com.yanweiyi.micodecodesandbox.model.enums;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 执行结果状态枚举
 */
public enum ResponseStatusEnum {

    SUCCESS("执行成功", 1),
    SERVER_ERROR("服务器错误", 2),
    USER_CODE_ERROR("用户代码错误", 3);

    private final String message;

    private final Integer value;

    ResponseStatusEnum(String message, Integer value) {
        this.message = message;
        this.value = value;
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
    public static ResponseStatusEnum getEnumByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (ResponseStatusEnum statusEnum : ResponseStatusEnum.values()) {
            if (statusEnum.value.equals(value)) {
                return statusEnum;
            }
        }
        return null;
    }

    public Integer getValue() {
        return value;
    }

    public String getMessage() {
        return message;
    }
}