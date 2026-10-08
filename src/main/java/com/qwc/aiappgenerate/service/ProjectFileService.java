package com.qwc.aiappgenerate.service;

import com.qwc.aiappgenerate.model.entity.User;
import com.qwc.aiappgenerate.model.vo.ProjectFileContentVO;
import com.qwc.aiappgenerate.model.vo.ProjectFileVO;

import java.util.List;

/**
 * 生成项目文件服务。
 * 负责在应用所有权校验后列出、读取和保存生成目录中的项目文件。
 */
public interface ProjectFileService {

    /**
     * 读取生成项目的完整可展示目录树。
     *
     * @param appId 应用 ID
     * @param loginUser 当前登录用户
     * @return 已过滤依赖目录、构建产物和敏感文件后的目录树
     */
    List<ProjectFileVO> listProjectFiles(Long appId, User loginUser);

    /**
     * 读取指定文本文件。
     *
     * @param appId 应用 ID
     * @param relativePath 相对于生成项目根目录的文件路径
     * @param loginUser 当前登录用户
     * @return 文件内容和版本信息
     */
    ProjectFileContentVO readProjectFile(Long appId, String relativePath, User loginUser);

    /**
     * 覆盖保存指定文本文件。
     *
     * @param appId 应用 ID
     * @param relativePath 相对于生成项目根目录的文件路径
     * @param content 待保存的完整 UTF-8 文本
     * @param loginUser 当前登录用户
     * @return 保存后的文件内容版本信息
     */
    ProjectFileContentVO saveProjectFile(Long appId, String relativePath, String content, User loginUser);
}
