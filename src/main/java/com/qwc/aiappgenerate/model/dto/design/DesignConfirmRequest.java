package com.qwc.aiappgenerate.model.dto.design;

import lombok.Data;

import java.io.Serializable;

/**
 * 确认当前页面样式版本请求。
 */
@Data
public class DesignConfirmRequest implements Serializable {

    /** 当前应用 ID。 */
    private Long appId;

    /** 用户在界面上看到并确认的版本号，用于阻止误确认过期版本。 */
    private Integer revisionNumber;
}
