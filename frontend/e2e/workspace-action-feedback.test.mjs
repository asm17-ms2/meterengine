import assert from "node:assert/strict";
import test from "node:test";
import { successMessage } from "../src/app/(console)/workspace-action-feedback.ts";

test("each workspace action that shows a toast says what it did", () => {
  const messages = ["createMember", "updateMemberRole", "updateMemberScopes", "revokeOrganizationInvitation", "deleteApiKey", "updateApiKeyScopes"].map(successMessage);
  for (const message of messages) assert.notEqual(message, "처리했습니다.");
  assert.equal(new Set(messages).size, messages.length);
  assert.equal(successMessage("unknownOperation"), "처리했습니다.");
});
