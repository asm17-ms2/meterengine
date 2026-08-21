"use server";

import { revalidatePath } from "next/cache";

import type {
  DeleteState,
  FormState,
} from "@/app/(console)/customers/state";
import type { ApiError } from "@/lib/api/client";
import {
  createCustomer,
  deleteCustomer,
  renameCustomer,
} from "@/lib/api/customers";

/**
 * 고객 쓰기 경로 (MS2-154).
 *
 * 이 화면이 이 레포의 첫 쓰기 화면이다. 조회 세 화면은 Server Component가 직접
 * 백엔드를 읽으면 끝이지만, 저장 버튼은 브라우저에서 시작하므로 서버로 돌아올
 * 길이 필요하다. 그 길이 Server Action이다.
 *
 *   브라우저 [저장] --> Next 서버 (여기서 X-Organization-Id 주입) --> Spring
 *
 * Route Handler로 자체 API를 만들지 않은 이유: 프록시 코드를 한 겹 더 쓰는 것
 * 말고 얻는 것이 없다. 여기서는 lib/api/customers.ts를 그대로 부르면 되고,
 * 조직 식별자는 서버에만 남는다 (frontend/README.md "백엔드 연동").
 *
 * <p>이 파일은 async 함수 말고 아무것도 export하지 못한다. "use server" 파일에서
 * 객체를 하나라도 내보내면 이 파일의 액션 전체가 런타임에 거부된다. 빌드는
 * 통과하고 액션을 실제로 부를 때 터지므로, 상태 타입과 초기값은 state.ts에 있다.
 */

/**
 * 이름 길이 상한. 백엔드 SaveCustomerRequest의 maxLength와 같은 값이다.
 *
 * 이 파일이 상수를 export하지 못해서(위 주석 참조) 바깥으로 내보내지 않는다.
 * 입력칸의 maxLength는 CustomerFormDialog가 따로 들고 있다.
 */
const NAME_MAX = 255;

/**
 * 이름을 읽고 다듬는다. 서버 액션은 브라우저를 거치지 않고도 불릴 수 있으므로
 * 화면 쪽 검증을 믿지 않고 여기서 다시 본다.
 */
function readName(formData: FormData): string {
  const raw = formData.get("name");
  return typeof raw === "string" ? raw.trim() : "";
}

function validateName(name: string): string | null {
  if (name === "") return "고객명을 입력하세요";
  if (name.length > NAME_MAX) return `고객명은 ${NAME_MAX}자를 넘을 수 없습니다`;
  return null;
}

/**
 * 저장 실패를 화면 문구로 옮긴다.
 *
 * code로 고르고 detail은 쓰지 않는다. detail은 영어이고 개발자용이라 그대로
 * 띄우지 말라고 백엔드 계약이 명시한다 (openapi.yaml ProblemResponse).
 * 모르는 code는 기본 문구로 떨어진다 - code 집합은 닫혀 있지 않다.
 */
function saveFailureMessage(error: ApiError): string {
  switch (error.code) {
    case "validation_error":
      return `고객명을 확인해주세요. ${NAME_MAX}자 이내여야 합니다.`;
    case "customer_not_found":
      return "이미 삭제된 고객입니다. 목록을 새로 고쳐주세요.";
    case "unknown_organization":
      return "도입사를 찾을 수 없습니다. 설정을 확인해주세요.";
    case "network_error":
      return error.title;
    default:
      return "저장하지 못했습니다. 잠시 후 다시 시도해주세요.";
  }
}

/** 고객 등록. 서버가 customer_id와 등록 시각을 발급한다. */
export async function createCustomerAction(
  _prev: FormState,
  formData: FormData,
): Promise<FormState> {
  const name = readName(formData);
  const invalid = validateName(name);
  if (invalid) return { status: "invalid", message: invalid };

  const result = await createCustomer(name);
  if (!result.ok) {
    return { status: "failed", message: saveFailureMessage(result.error) };
  }

  revalidatePath("/customers");
  return { status: "done" };
}

/** 고객 이름 수정. 고칠 수 있는 것은 이름 하나다. */
export async function renameCustomerAction(
  _prev: FormState,
  formData: FormData,
): Promise<FormState> {
  const id = formData.get("id");
  if (typeof id !== "string" || id === "") {
    return { status: "failed", message: "고객을 특정하지 못했습니다." };
  }

  const name = readName(formData);
  const invalid = validateName(name);
  if (invalid) return { status: "invalid", message: invalid };

  const result = await renameCustomer(id, name);
  if (!result.ok) {
    return { status: "failed", message: saveFailureMessage(result.error) };
  }

  revalidatePath("/customers");
  return { status: "done" };
}

/**
 * 고객 삭제.
 *
 * 409와 404를 실패로 뭉뚱그리지 않는다. 둘 다 서버가 정확히 판정해 준 결과이고
 * 운영자가 할 일이 다르다. 409는 "이 고객은 지울 수 없다"이고, 404는 "이미
 * 없어졌으니 목록을 새로 보면 된다"이다.
 */
export async function deleteCustomerAction(
  _prev: DeleteState,
  formData: FormData,
): Promise<DeleteState> {
  const id = formData.get("id");
  const name = formData.get("name");
  const label = typeof name === "string" ? name : "";
  if (typeof id !== "string" || id === "") {
    return { status: "failed", message: "고객을 특정하지 못했습니다." };
  }

  const result = await deleteCustomer(id);
  if (!result.ok) {
    if (result.error.code === "customer_has_events") {
      return { status: "rejected", name: label };
    }
    if (result.error.code === "customer_not_found") {
      // 지우려던 것이 이미 없다. 목록에서 사라지는 것이 사용자가 원한 결과와
      // 같으므로 목록을 새로 읽어 둔다.
      revalidatePath("/customers");
      return { status: "gone", name: label };
    }
    return {
      status: "failed",
      message:
        result.error.code === "network_error"
          ? result.error.title
          : "삭제하지 못했습니다. 잠시 후 다시 시도해주세요.",
    };
  }

  revalidatePath("/customers");
  return { status: "done" };
}
