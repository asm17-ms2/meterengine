// 조회 조건이 바뀌면 Suspense를 새로 마운트해 이전 결과 대신 로딩을 보이게 하는 key.
export function searchKey(params: Record<string, string | string[] | undefined>): string {
  return Object.entries(params)
    .flatMap(([name, value]) => (value === undefined ? [] : [value].flat().map(item => `${name}=${item}`)))
    .sort()
    .join("&");
}
