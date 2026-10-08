package com.qwc.aiappgenerate.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 在线编辑器文件内容响应。
 * 返回文本及版本信息，帮助前端显示保存状态并避免展示过期内容。
 */
@Data
public class ProjectFileContentVO implements Serializable {

    /** 文件相对于生成项目根目录的路径。 */
    private String path;

    /** UTF-8 文件完整文本。 */
    private String content;

    /** 文件字节数。 */
    private Long size;

    /** 最后修改时间戳。 */
    private Long modifiedTime;

    private static final long serialVersionUID = 1L;
}
