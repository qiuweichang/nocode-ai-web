declare namespace API {
  type AppAddRequest = {
    initPrompt?: string
  }

  type AppAdminUpdateRequest = {
    id?: string
    appName?: string
    cover?: string
    priority?: number
  }

  type AppDeployRequest = {
    appId?: string
  }

  type AppFileSaveRequest = {
    appId?: string
    path?: string
    content?: string
  }

  type DesignGenerateRequest = {
    appId?: string
    prompt?: string
  }

  type DesignReviseRequest = {
    appId?: string
    prompt?: string
    baseRevisionNumber?: number
    pagePath?: string
    tagName?: string
    selector?: string
    elementId?: string
    className?: string
    textContent?: string
  }

  type DesignConfirmRequest = {
    appId?: string
    revisionNumber?: number
  }

  type DesignRevisionVO = {
    number?: number
    previewPath?: string
    screenshotPath?: string
    prompt?: string
    confirmed?: boolean
    createdAt?: string
  }

  type DesignWorkflowVO = {
    appId?: string
    stage?: 'EMPTY' | 'GENERATING' | 'REVIEW' | 'REVISING' | 'CONFIRMED' | 'FAILED'
    stageText?: string
    currentRevision?: number
    confirmedRevision?: number
    previewPath?: string
    screenshotPath?: string
    failureMessage?: string
    confirmed?: boolean
    revisions?: DesignRevisionVO[]
  }

  type AppQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: string
    appName?: string
    cover?: string
    initPrompt?: string
    codeGenType?: string
    deployKey?: string
    priority?: number
    userId?: string
  }

  type AppUpdateRequest = {
    id?: string
    appName?: string
  }

  type AppVO = {
    id?: string
    appName?: string
    cover?: string
    initPrompt?: string
    codeGenType?: string
    deployKey?: string
    deployedTime?: string
    priority?: number
    userId?: string
    createTime?: string
    updateTime?: string
    hasGeneratedCode?: boolean
    user?: UserVO
  }

  type BaseResponseAppVO = {
    code?: number
    data?: AppVO
    message?: string
  }

  type BaseResponseBoolean = {
    code?: number
    data?: boolean
    message?: string
  }

  type BaseResponseLoginUserVO = {
    code?: number
    data?: LoginUserVO
    message?: string
  }

  type BaseResponseLong = {
    code?: number
    data?: string
    message?: string
  }

  type ProjectFileVO = {
    name?: string
    path?: string
    directory?: boolean
    size?: number
    editable?: boolean
    modifiedTime?: number
    children?: ProjectFileVO[]
  }

  type ProjectFileContentVO = {
    path?: string
    content?: string
    size?: number
    modifiedTime?: number
  }

  type BaseResponseProjectFileVOList = {
    code?: number
    data?: ProjectFileVO[]
    message?: string
  }

  type BaseResponseProjectFileContentVO = {
    code?: number
    data?: ProjectFileContentVO
    message?: string
  }

  type BaseResponseDesignWorkflowVO = {
    code?: number
    data?: DesignWorkflowVO
    message?: string
  }

  type BaseResponsePageAppVO = {
    code?: number
    data?: PageAppVO
    message?: string
  }

  type BaseResponsePageChatHistory = {
    code?: number
    data?: PageChatHistory
    message?: string
  }

  type BaseResponsePageUserVO = {
    code?: number
    data?: PageUserVO
    message?: string
  }

  type BaseResponseString = {
    code?: number
    data?: string
    message?: string
  }

  type BaseResponseUser = {
    code?: number
    data?: User
    message?: string
  }

  type BaseResponseUserVO = {
    code?: number
    data?: UserVO
    message?: string
  }

  type ChatHistory = {
    id?: string
    message?: string
    messageType?: string
    appId?: string
    userId?: string
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type ChatHistoryQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: string
    message?: string
    messageType?: string
    appId?: string
    userId?: string
    lastCreateTime?: string
  }

  type chatToGenCodeParams = {
    appId: string
    message: string
  }

  type DeleteRequest = {
    id?: string
  }

  type getAppVOByIdByAdminParams = {
    id: string
  }

  type getAppVOByIdParams = {
    id: string
  }

  type getUserByIdParams = {
    id: string
  }

  type getUserVOByIdParams = {
    id: string
  }

  type listAppChatHistoryParams = {
    appId: string
    pageSize?: number
    lastCreateTime?: string
  }

  type LoginUserVO = {
    id?: string
    userAccount?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    createTime?: string
    updateTime?: string
  }

  type PageAppVO = {
    records?: AppVO[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type PageChatHistory = {
    records?: ChatHistory[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type PageUserVO = {
    records?: UserVO[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type ServerSentEventString = true

  type User = {
    id?: string
    userAccount?: string
    userPassword?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    editTime?: string
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type UserAddRequest = {
    userName?: string
    userAccount?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
  }

  type UserLoginRequest = {
    userAccount?: string
    userPassword?: string
  }

  type UserQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: string
    userName?: string
    userAccount?: string
    userProfile?: string
    userRole?: string
  }

  type UserRegisterRequest = {
    userAccount?: string
    userPassword?: string
    checkPassword?: string
  }

  type UserUpdateRequest = {
    id?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
  }

  type UserVO = {
    id?: string
    userAccount?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    createTime?: string
  }
}
