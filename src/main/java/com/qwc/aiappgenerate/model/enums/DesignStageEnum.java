package com.qwc.aiappgenerate.model.enums;

import lombok.Getter;

/**
 * 页面设计工作流阶段。
 * 阶段被持久化到设计目录的 metadata.json，前后端据此决定当前允许生成设计、修改设计还是生成代码。
 */
@Getter
public enum DesignStageEnum {

    EMPTY("EMPTY", "等待生成样式"),
    GENERATING("GENERATING", "正在生成样式"),
    REVIEW("REVIEW", "等待确认样式"),
    REVISING("REVISING", "正在修改样式"),
    CONFIRMED("CONFIRMED", "样式已确认"),
    FAILED("FAILED", "样式设计失败");

    private final String value;

    private final String text;

    DesignStageEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }

    /**
     * 按持久化值解析阶段，未知值安全回退到未开始状态。
     *
     * @param value metadata.json 中保存的阶段值
     * @return 对应阶段枚举
     */
    public static DesignStageEnum fromValue(String value) {
        for (DesignStageEnum stage : values()) {
            if (stage.value.equals(value)) {
                return stage;
            }
        }
        return EMPTY;
    }
}
