WITH namespaces AS (
  SELECT oid,nspname FROM pg_namespace WHERE nspname IN ('public','shared','test','live')
), relations AS (
  SELECT c.*,n.nspname FROM pg_class c JOIN namespaces n ON n.oid=c.relnamespace
  WHERE NOT (n.nspname=? AND c.relname='flyway_schema_history' AND c.relkind='r')
    AND c.relkind IN ('r','p','v','m','f','c')
), objects AS (
  SELECT 'relations' AS kind,coalesce(jsonb_agg(jsonb_build_object(
    'name',r.nspname||'.'||r.relname,'kind',r.relkind::text,'persistence',r.relpersistence::text,'row_security',r.relrowsecurity,
    'view',CASE WHEN r.relkind IN ('v','m') THEN pg_get_viewdef(r.oid,false) END,
    'options',r.reloptions,
    'columns',coalesce((SELECT jsonb_agg(jsonb_build_object(
      'name',a.attname,'type',format_type(a.atttypid,a.atttypmod),'not_null',a.attnotnull,
      'default',pg_get_expr(d.adbin,d.adrelid,false),'generated',a.attgenerated::text,'identity',a.attidentity::text,
      'collation',CASE WHEN a.attcollation<>t.typcollation THEN jsonb_build_object(
        'name',cn.nspname||'.'||co.collname,'locale',co.colllocale,'deterministic',co.collisdeterministic) END
    ) ORDER BY a.attnum) FROM pg_attribute a JOIN pg_type t ON t.oid=a.atttypid
      LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
      LEFT JOIN pg_collation co ON co.oid=a.attcollation LEFT JOIN pg_namespace cn ON cn.oid=co.collnamespace
      WHERE a.attrelid=r.oid AND a.attnum>0 AND NOT a.attisdropped),'[]'::jsonb)
  ) ORDER BY r.nspname COLLATE "C",r.relname COLLATE "C"),'[]'::jsonb) AS value FROM relations r
  UNION ALL
  SELECT 'constraints',coalesce(jsonb_agg(jsonb_build_object('table',r.nspname||'.'||r.relname,
    'name',k.conname,'type',k.contype::text,'validated',k.convalidated,'enforced',k.conenforced,'definition',pg_get_constraintdef(k.oid,false))
    ORDER BY r.nspname COLLATE "C",r.relname COLLATE "C",k.conname COLLATE "C"),'[]'::jsonb)
    FROM pg_constraint k JOIN relations r ON r.oid=k.conrelid
  UNION ALL
  SELECT 'indexes',coalesce(jsonb_agg(jsonb_build_object('table',r.nspname||'.'||r.relname,
    'name',c.relname,'valid',i.indisvalid,'ready',i.indisready,'definition',pg_get_indexdef(i.indexrelid,0,false))
    ORDER BY r.nspname COLLATE "C",r.relname COLLATE "C",c.relname COLLATE "C"),'[]'::jsonb)
    FROM pg_index i JOIN relations r ON r.oid=i.indrelid JOIN pg_class c ON c.oid=i.indexrelid
  UNION ALL
  SELECT 'functions',coalesce(jsonb_agg(jsonb_build_object('schema',n.nspname,'name',p.proname,
    'arguments',pg_get_function_identity_arguments(p.oid),'definition',pg_get_functiondef(p.oid))
    ORDER BY n.nspname COLLATE "C",p.proname COLLATE "C",pg_get_function_identity_arguments(p.oid) COLLATE "C"),'[]'::jsonb)
    FROM pg_proc p JOIN namespaces n ON n.oid=p.pronamespace WHERE p.prokind IN ('f','p')
  UNION ALL
  SELECT 'triggers',coalesce(jsonb_agg(jsonb_build_object('table',r.nspname||'.'||r.relname,
    'name',t.tgname,'enabled',t.tgenabled::text,'definition',pg_get_triggerdef(t.oid,false))
    ORDER BY r.nspname COLLATE "C",r.relname COLLATE "C",t.tgname COLLATE "C"),'[]'::jsonb)
    FROM pg_trigger t JOIN relations r ON r.oid=t.tgrelid WHERE NOT t.tgisinternal
  UNION ALL
  SELECT 'sequences',coalesce(jsonb_agg(jsonb_build_object('name',n.nspname||'.'||c.relname,
    'type',format_type(s.seqtypid,NULL),'start',s.seqstart,'increment',s.seqincrement,
    'min',s.seqmin,'max',s.seqmax,'cache',s.seqcache,'cycle',s.seqcycle)
    ORDER BY n.nspname COLLATE "C",c.relname COLLATE "C"),'[]'::jsonb)
    FROM pg_sequence s JOIN pg_class c ON c.oid=s.seqrelid JOIN namespaces n ON n.oid=c.relnamespace
  UNION ALL
  SELECT 'collations',coalesce(jsonb_agg(jsonb_build_object('name',n.nspname||'.'||c.collname,
    'provider',c.collprovider::text,'locale',c.colllocale,'deterministic',c.collisdeterministic)
    ORDER BY n.nspname COLLATE "C",c.collname COLLATE "C"),'[]'::jsonb)
    FROM pg_collation c JOIN namespaces n ON n.oid=c.collnamespace
  UNION ALL
  SELECT 'types',coalesce(jsonb_agg(jsonb_build_object('name',n.nspname||'.'||t.typname,'kind',t.typtype::text,
    'base',format_type(t.typbasetype,t.typtypmod),'not_null',t.typnotnull,'default',t.typdefault,
    'enum',coalesce((SELECT jsonb_agg(e.enumlabel ORDER BY e.enumsortorder) FROM pg_enum e WHERE e.enumtypid=t.oid),'[]'::jsonb))
    ORDER BY n.nspname COLLATE "C",t.typname COLLATE "C"),'[]'::jsonb)
    FROM pg_type t JOIN namespaces n ON n.oid=t.typnamespace WHERE t.typtype IN ('e','d','r','m')
)
SELECT jsonb_object_agg(kind,value) FROM objects;
