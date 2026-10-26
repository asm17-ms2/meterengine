"use client";

import { MenuFoldOutlined, MenuUnfoldOutlined } from "@ant-design/icons";
import { Button, Col, Drawer, Flex, Grid, Layout, theme, Typography } from "antd";
import { Suspense, useState } from "react";

import { DevStateSwitch } from "@/components/shell/DevStateSwitch";
import {
  CONTENT_MAX_WIDTH,
  HEADER_HEIGHT,
  NAV_DRAWER_SIZE,
  SIDER_WIDTH,
} from "@/components/shell/layout-sizes";
import { NavMenu } from "@/components/shell/NavMenu";

function NavigationPanel({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <Flex vertical justify="space-between" style={{ height: "100%" }}>
      <NavMenu onNavigate={onNavigate} />
      <Suspense fallback={null}>
        <DevStateSwitch />
      </Suspense>
    </Flex>
  );
}

export function ConsoleShell({
  organizationLabel,
  children,
}: {
  organizationLabel: string;
  children: React.ReactNode;
}) {
  const { token } = theme.useToken();
  const screens = Grid.useBreakpoint();
  const [isNavOpen, setIsNavOpen] = useState(false);
  const isLarge = screens.lg === true;

  if (isLarge && isNavOpen) setIsNavOpen(false);

  const closeNav = () => setIsNavOpen(false);

  return (
    <Layout style={{ minHeight: "100vh" }}>
      <Layout.Header
        style={{
          position: "sticky",
          top: 0,
          zIndex: 10,
          display: "flex",
          alignItems: "center",
          gap: 12,
          borderBottom: `1px solid ${token.colorBorderSecondary}`,
        }}
      >
        <Col lg={0} style={{ flex: "none" }}>
          <Button
            type="text"
            aria-label={isNavOpen ? "메뉴 닫기" : "메뉴 열기"}
            aria-expanded={isNavOpen}
            icon={
              isNavOpen ? (
                <MenuFoldOutlined aria-hidden aria-label={undefined} />
              ) : (
                <MenuUnfoldOutlined aria-hidden aria-label={undefined} />
              )
            }
            onClick={() => setIsNavOpen(!isNavOpen)}
          />
        </Col>
        <Typography.Text strong style={{ fontSize: token.fontSizeLG, whiteSpace: "nowrap" }}>
          MeterEngine
        </Typography.Text>
        <Typography.Text type="secondary" ellipsis style={{ minWidth: 0 }}>
          {organizationLabel}
        </Typography.Text>
      </Layout.Header>
      <Layout hasSider>
        <Col xs={0} lg={{ span: 24, flex: `${SIDER_WIDTH}px` }}>
          <Layout.Sider
            theme="light"
            width={SIDER_WIDTH}
            trigger={null}
            style={{
              position: "sticky",
              top: HEADER_HEIGHT,
              height: `calc(100vh - ${HEADER_HEIGHT}px)`,
              overflow: "auto",
              borderInlineEnd: `1px solid ${token.colorBorderSecondary}`,
            }}
          >
            <NavigationPanel />
          </Layout.Sider>
        </Col>
        <Layout.Content id="console-content" style={{ minWidth: 0 }}>
          <Flex vertical gap="medium" style={{ width: "100%", maxWidth: CONTENT_MAX_WIDTH }}>
            {children}
          </Flex>
        </Layout.Content>
      </Layout>
      <Drawer
        placement="left"
        open={isNavOpen && !isLarge}
        onClose={closeNav}
        closable={false}
        size={NAV_DRAWER_SIZE}
        rootStyle={{ top: HEADER_HEIGHT }}
        styles={{ body: { padding: 0 } }}
      >
        <NavigationPanel onNavigate={closeNav} />
      </Drawer>
    </Layout>
  );
}
