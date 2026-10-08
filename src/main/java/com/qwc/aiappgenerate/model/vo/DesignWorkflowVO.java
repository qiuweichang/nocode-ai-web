package com.qwc.aiappgenerate.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 页面设计工作流视图。
 * 前端根据 stage 锁定设计预览、切换提示语，并在 CONFIRMED 后开放代码生成。
 */
@Data
@Builder
public class DesignWorkflowVO {

    /** 应用 ID。 */
    private Long appId;

    /** 当前阶段值。 */
    private String stage;

    /** 当前阶段中文说明。 */
    private String stageText;

    /** 当前展示版本号。 */
    private Integer currentRevision;

    /** 当前已确认版本号。 */
    private Integer confirmedRevision;

    /** 当前版本的同源 HTML 预览路径。 */
    private String previewPath;

    /** 当前版本截图路径。 */
    private String screenshotPath;

    /** 最近一次失败原因。 */
    private String failureMessage;

    /** 是否已经允许进入代码生成阶段。 */
    private Boolean confirmed;

    /** 历史设计版本。 */
    private List<DesignRevisionVO> revisions;
}
