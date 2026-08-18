-- 데모 확장 고객 3곳 (MS2-142 mock 산출물의 축소판).
-- sample-events.csv가 이 고객 id로 이벤트를 보내므로, 전송 전에 이 파일을 DB에 적용해야 한다.
-- 적용 방법은 demo/README.md의 "확장 시드" 절 참조.
--
-- id는 고객 이름에서 uuid5로 만든 결정론적 값이라 MS2-142 원본과 항상 같다.
-- (기본 시드의 아크메 주식회사, 베타 스튜디오는 backend의 R__seed.sql이 항상 만들며,
--  이벤트를 받지 않아 사용량 0, 금액 0 행으로 보인다)
--
-- TODO(MS2-155): 고객 등록 API가 생기면 psql 주입 대신 API 등록으로 전환한다.
--   그때 customer_id 발급 방식(서버 발급 vs 클라이언트 지정)에 따라 이 파일과
--   sample-events.csv의 고정 id 유지 여부를 함께 결정해야 한다.
INSERT INTO customer (id, organization_id, name) VALUES
  ('35bc8d12-9d38-57ab-bc9b-bbd35d779a26', 'd7cee55d-8c82-4afc-b996-6749d8b26a4e', '이슬비랩스'),
  ('008cd6a7-6ff9-505d-9421-747e7d2d62aa', 'd7cee55d-8c82-4afc-b996-6749d8b26a4e', '도담헬스'),
  ('8c525322-2712-5b5f-aa1a-435a7ff9fe97', 'd7cee55d-8c82-4afc-b996-6749d8b26a4e', '한들물류')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;
