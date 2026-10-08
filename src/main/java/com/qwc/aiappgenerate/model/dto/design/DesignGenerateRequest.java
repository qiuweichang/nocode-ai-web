package com.qwc.aiappgenerate.model.dto.design;

import lombok.Data;

import java.io.Serializable;

/**
 * 首次生成页面样式请求。
 */
@Data
public class DesignGenerateRequest implements Serializable {

    /** 当前应用 ID，使用 Long 在后端保留雪花 ID 精度。 */
    private Long appId;

    /** 用户对完整桌面网页的原始需求。 */
    private String prompt;
}
