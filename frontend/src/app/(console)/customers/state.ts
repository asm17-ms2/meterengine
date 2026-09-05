/**
 * 고객 쓰기 액션의 상태 타입과 초기값.
 *
 * actions.ts에 두지 못한다. "use server" 파일은 async 함수만 export할 수 있어서
 * 상수를 하나라도 내보내면 그 파일의 액션 전체가 런타임에 거부된다
 * ("A 'use server' file can only export async functions, found object").
 * 타입만이면 컴파일에서 지워져 문제가 없지만, 초기값은 실제 객체라 걸린다.
 */

/**
 * 등록/수정 폼의 상태. useActionState가 이 값을 다이얼로그에 돌려준다.
 *
 * 'done'이 필요한 이유: 성공했을 때 다이얼로그를 닫아야 하는데, 서버 액션은
 * 화면을 조작할 수 없다. 상태로 알리고 닫는 것은 클라이언트가 한다.
 */
export type CustomerFormState =
  | { status: "idle" }
  | { status: "invalid"; message: string }
  | { status: "failed"; message: string }
  | { status: "done" };

export const CUSTOMER_FORM_IDLE: CustomerFormState = { status: "idle" };

/**
 * 삭제의 상태.
 *
 * 'rejected'(409)는 오류가 아니라 규칙이다. 이벤트가 있는 고객은 지울 수 없다는
 * 것을 서버가 확인하고 거절한 것이라, 에러 블록이 아니라 안내 다이얼로그로 간다.
 * 'gone'(404)은 목록을 열어 둔 사이에 다른 곳에서 이미 지워진 경우다.
 */
export type CustomerDeleteState =
  | { status: "idle" }
  | { status: "rejected"; name: string }
  | { status: "gone"; name: string }
  | { status: "failed"; message: string }
  | { status: "done" };

export const CUSTOMER_DELETE_IDLE: CustomerDeleteState = { status: "idle" };
