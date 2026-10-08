// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /app/add */
export async function addApp(body: API.AppAddRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseLong>('/app/add', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/admin/delete */
export async function deleteAppByAdmin(body: API.DeleteRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/app/admin/delete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /app/admin/get/vo */
export async function getAppVoByIdByAdmin(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getAppVOByIdByAdminParams,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseAppVO>('/app/admin/get/vo', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/admin/list/page/vo */
export async function listAppVoByPageByAdmin(
  body: API.AppQueryRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponsePageAppVO>('/app/admin/list/page/vo', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/admin/update */
export async function updateAppByAdmin(
  body: API.AppAdminUpdateRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean>('/app/admin/update', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /app/chat/gen/code */
export async function chatToGenCode(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.chatToGenCodeParams,
  options?: { [key: string]: any },
) {
  return request<API.ServerSentEventString[]>('/app/chat/gen/code', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 获取当前应用的 Stitch 样式设计状态 GET /app/design */
export async function getAppDesign(appId: string, options?: { [key: string]: any }) {
  return request<API.BaseResponseDesignWorkflowVO>('/app/design', {
    method: 'GET',
    params: { appId },
    ...(options || {}),
  })
}

/** 生成首版 Stitch 桌面样式 POST /app/design/generate */
export async function generateAppDesign(
  body: API.DesignGenerateRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseDesignWorkflowVO>('/app/design/generate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    timeout: 360000,
    ...(options || {}),
  })
}

/** 基于当前版本和可选元素上下文修改 Stitch 样式 POST /app/design/revise */
export async function reviseAppDesign(
  body: API.DesignReviseRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseDesignWorkflowVO>('/app/design/revise', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    timeout: 360000,
    ...(options || {}),
  })
}

/** 确认当前 Stitch 样式版本 POST /app/design/confirm */
export async function confirmAppDesign(
  body: API.DesignConfirmRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseDesignWorkflowVO>('/app/design/confirm', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/delete */
export async function deleteApp(body: API.DeleteRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/app/delete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/deploy */
export async function deployApp(body: API.AppDeployRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseString>('/app/deploy', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 下载当前用户拥有的应用源码 ZIP GET /app/download/{appId} */
export async function downloadAppCode(appId: string, options?: { [key: string]: any }) {
  return request<Blob>(`/app/download/${appId}`, {
    method: 'GET',
    responseType: 'blob',
    ...(options || {}),
  })
}

/** 获取当前用户拥有应用的完整生成项目文件树 GET /app/files */
export async function listProjectFiles(appId: string, options?: { [key: string]: any }) {
  return request<API.BaseResponseProjectFileVOList>('/app/files', {
    method: 'GET',
    params: { appId },
    ...(options || {}),
  })
}

/** 读取当前用户拥有应用中的指定文本文件 GET /app/file */
export async function readProjectFile(
  appId: string,
  path: string,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseProjectFileContentVO>('/app/file', {
    method: 'GET',
    params: { appId, path },
    ...(options || {}),
  })
}

/** 保存当前用户拥有应用中的指定文本文件 POST /app/file/save */
export async function saveProjectFile(
  body: API.AppFileSaveRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseProjectFileContentVO>('/app/file/save', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /app/get/vo */
export async function getAppVoById(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getAppVOByIdParams,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseAppVO>('/app/get/vo', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/good/list/page/vo */
export async function listGoodAppVoByPage(
  body: API.AppQueryRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponsePageAppVO>('/app/good/list/page/vo', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/my/list/page/vo */
export async function listMyAppVoByPage(
  body: API.AppQueryRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponsePageAppVO>('/app/my/list/page/vo', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/update */
export async function updateApp(body: API.AppUpdateRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/app/update', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
