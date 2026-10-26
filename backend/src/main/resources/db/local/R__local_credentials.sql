INSERT INTO organization (id, name) VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', '데모 도입사')
ON CONFLICT (id) DO NOTHING;

INSERT INTO organization (id, name, status) VALUES
  ('69cb6c64-14ed-49a0-a397-5f90b468f510', '로컬 두 번째 도입사', 'active')
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_account (id, email, password_hash, name, role, created_at) VALUES
  ('663fce9b-dec7-41b6-9330-e6fe2eeffb7e', 'member@meterengine.local',
   '{bcrypt}$2a$10$Kqg4kLMfEy5ainWZ/SYDPOzmdZZi9Edq3zdE2tYtMeFpeTW1.ODGq',
   '로컬 사용자', 'member', now()),
  ('43b56678-00b0-4ff0-b3c6-e32821328ca3', 'operator@meterengine.local',
   '{bcrypt}$2a$10$zcy3Tgv1/0SOUp7skgmjVuazzqspQoQROkyFUsIRDK7YByQg.zNs6',
   '로컬 운영자', 'operator', now())
ON CONFLICT (id) DO UPDATE SET
  password_hash = EXCLUDED.password_hash,
  name          = EXCLUDED.name,
  role          = EXCLUDED.role;

INSERT INTO organization_member (organization_id, user_account_id, created_at) VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', '663fce9b-dec7-41b6-9330-e6fe2eeffb7e',
   '2026-01-01T00:00:00Z'),
  ('69cb6c64-14ed-49a0-a397-5f90b468f510', '663fce9b-dec7-41b6-9330-e6fe2eeffb7e',
   '2026-01-02T00:00:00Z')
ON CONFLICT DO NOTHING;

INSERT INTO api_key
  (id, organization_id, mode, label, key_hash, last_four, scopes, created_at) VALUES
  ('e0c3a216-82f4-40fe-babe-e35b49ebd24a', 'd7cee55d-8c82-4afc-b996-6749d8b26a4e', 'live',
   '로컬 개발 live',
   decode('53106e7ff3b325d28c49f68e1acfb7ac7e3f00801459dd2eced21a47362d003d', 'hex'),
   '2LtA', '{admin}', now()),
  ('b5e65980-3d64-47fa-9b59-0ba963529bc6', 'd7cee55d-8c82-4afc-b996-6749d8b26a4e', 'test',
   '로컬 개발 test',
   decode('e1212355d0b77a0d18734b983f453fecdeb9983ca340dd79b313b21032bedcaf', 'hex'),
   'CeVC', '{admin}', now())
ON CONFLICT DO NOTHING;
