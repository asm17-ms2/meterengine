"use client";

import { Alert, Button, Flex } from "antd";
import { useState } from "react";

import { alertIcon } from "@/components/screen/alert-icon";
import { LinkButton } from "@/components/screen/LinkButton";

export function BillingFinalizedNotice({
  title,
  invoicesHref,
  zeroAmountCustomerCount,
  children,
}: {
  title: string;
  invoicesHref: string;
  zeroAmountCustomerCount: number;
  children: React.ReactNode;
}) {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <>
      <Alert
        type="success"
        showIcon
        icon={alertIcon("success")}
        title={title}
        description={`청구 예정액이 0원인 고객 ${zeroAmountCustomerCount}곳은 인보이스가 만들어지지 않았습니다.`}
        action={
          <Flex vertical gap="small">
            <LinkButton href={invoicesHref}>인보이스 보기</LinkButton>
            <Button aria-expanded={isOpen} onClick={() => setIsOpen(!isOpen)}>
              0원 고객 {zeroAmountCustomerCount}곳 {isOpen ? "접기" : "보기"}
            </Button>
          </Flex>
        }
      />
      {isOpen ? children : null}
    </>
  );
}
