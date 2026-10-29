ALTER TABLE organization_member ADD COLUMN scopes VARCHAR[] NOT NULL DEFAULT ARRAY['admin']::VARCHAR[]
  CONSTRAINT organization_member_scopes_check CHECK (
    cardinality(scopes) > 0 AND array_position(scopes, NULL) IS NULL
    AND scopes <@ ARRAY['ingest', 'read', 'admin']::VARCHAR[]);

ALTER TABLE organization_member DROP CONSTRAINT organization_member_role_check;
ALTER TABLE organization_member ADD CONSTRAINT organization_member_role_check
  CHECK (role IN ('owner', 'admin', 'member'));
ALTER TABLE organization_member ADD CONSTRAINT organization_member_role_scopes_check CHECK (
  (role IN ('owner', 'admin') AND 'admin' = ANY(scopes))
  OR (role = 'member' AND NOT ('admin' = ANY(scopes))));

ALTER TABLE organization_invitation ADD COLUMN scopes VARCHAR[] NOT NULL DEFAULT ARRAY['admin']::VARCHAR[]
  CONSTRAINT organization_invitation_scopes_check CHECK (
    cardinality(scopes) > 0 AND array_position(scopes, NULL) IS NULL
    AND scopes <@ ARRAY['ingest', 'read', 'admin']::VARCHAR[]);
