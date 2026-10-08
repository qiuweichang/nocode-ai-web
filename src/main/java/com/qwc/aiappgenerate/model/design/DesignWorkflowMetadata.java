package com.qwc.aiappgenerate.model.design;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 单个应用的设计工作流元数据。
 * 使用文件持久化而不是新增数据库表，使设计产物与生成代码共同迁移、下载或清理，同时保持两个目录相互隔离。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesignWorkflowMetadata {

    /** 所属应用 ID。 */
    private Long appId;

    /** 当前工作流阶段，值来自 DesignStageEnum。 */
    private String stage;

    /** Stitch 项目 ID，首次生成时创建，后续版本复用。 */
    private String stitchProjectId;

    /** 用户创建应用时的原始提示词。 */
    private String originalPrompt;

    /** 当前展示版本号。 */
    private Integer currentRevision;

    /** 已确认版本号；未确认时为空。 */
    private Integer confirmedRevision;

    /** 最近一次失败的可展示错误信息。 */
    private String failureMessage;

    /** 最近状态变化时间。 */
    private LocalDateTime updatedAt;

    /** 按版本号升序保存的全部成功版本。 */
    @Builder.Default
    private List<DesignRevisionMetadata> revisions = new ArrayList<>();
}
