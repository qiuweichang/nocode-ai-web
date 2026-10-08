package com.qwc.aiappgenerate.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 项目文件树节点。
 * 同时描述文件和目录，供前端递归渲染完整的生成项目结构。
 */
@Data
public class ProjectFileVO implements Serializable {

    /** 节点名称，不包含父目录。 */
    private String name;

    /** 节点相对于生成项目根目录的统一斜杠路径。 */
    private String path;

    /** 是否为目录；目录节点通过 children 承载后代节点。 */
    private Boolean directory;

    /** 文件字节数，目录固定为 0。 */
    private Long size;

    /** 文件是否允许在浏览器中按 UTF-8 文本编辑。 */
    private Boolean editable;

    /** 最后修改时间戳，用于前端判断生成过程中文件是否发生变化。 */
    private Long modifiedTime;

    /** 子节点列表；文件节点保持空列表以简化前端递归处理。 */
    private List<ProjectFileVO> children = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}
