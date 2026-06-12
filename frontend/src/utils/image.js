/**
 * 将对象存储外链转为同源代理地址。
 * blob: /avatars/ 本地静态资源原样返回。
 */
export function resolveImageSrc(url) {
  if (!url) return ''
  if (url.startsWith('blob:') || url.startsWith('/api/conversation/image') || url.startsWith('/avatars/')) {
    return url
  }
  return `/api/conversation/image?url=${encodeURIComponent(url)}`
}
