/**
 * 统一的前端 ID 类型。
 * 后端的 Long 在浏览器里不能安全使用 number 表示，因此前端侧统一按字符串处理。
 */
export type IdLike = string | number | bigint | null | undefined

/**
 * 将各种来源的 ID 归一化为字符串。
 * 这里负责兜住路由参数、接口返回值和历史遗留 number 类型，避免业务代码再次触发精度丢失。
 *
 * @param id 待归一化的 ID 值
 * @returns 去除空白后的字符串 ID；无效值时返回 undefined
 */
export const normalizeId = (id: IdLike): string | undefined => {
  if (id === null || id === undefined) {
    return undefined
  }
  const normalizedValue = String(id).trim()
  return normalizedValue ? normalizedValue : undefined
}

/**
 * 将 Vue Router 的查询参数安全转换为单个字符串 ID。
 * 查询参数可能是字符串、数组或空值，这里统一收敛，避免页面内部分散处理。
 *
 * @param value 路由中的原始查询参数
 * @returns 可直接传给后端的字符串 ID；不存在时返回空字符串
 */
export const normalizeRouteId = (value: unknown): string => {
  if (Array.isArray(value)) {
    return normalizeId(value[0]) ?? ''
  }
  return normalizeId(value as IdLike) ?? ''
}

/**
 * 比较两个业务 ID 是否相同。
 * 通过归一化后按字符串比较，避免一边是 number、一边是 string 时出现误判。
 *
 * @param left 左侧 ID
 * @param right 右侧 ID
 * @returns 两个 ID 归一化后是否一致
 */
export const isSameId = (left: IdLike, right: IdLike): boolean => {
  const normalizedLeft = normalizeId(left)
  const normalizedRight = normalizeId(right)
  return Boolean(normalizedLeft && normalizedRight && normalizedLeft === normalizedRight)
}
