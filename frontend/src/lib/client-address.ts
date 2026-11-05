import { isIP } from "node:net";

const CLIENT_ADDRESS_HEADER = "x-meterengine-client-ip";

// 앞단 부하 분산기는 받은 값 뒤에 접속 주소를 붙이므로 사용자가 꾸밀 수 없는 값은 맨 오른쪽 하나뿐이다.
export function clientAddressFromForwardedFor(forwardedFor: string | null): string | undefined {
  const rightmost = forwardedFor?.split(",").at(-1)?.trim();
  return rightmost && isIP(rightmost) ? rightmost : undefined;
}

export function withForwardedClientAddress(headers: Headers): Headers {
  const forwarded = new Headers(headers);
  forwarded.delete(CLIENT_ADDRESS_HEADER);
  const clientAddress = clientAddressFromForwardedFor(headers.get("x-forwarded-for"));
  if (clientAddress) forwarded.set(CLIENT_ADDRESS_HEADER, clientAddress);
  return forwarded;
}
