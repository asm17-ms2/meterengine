CREATE COLLATION korean (provider = icu, locale = 'ko-KR', deterministic = true);

ALTER TABLE customer        ALTER COLUMN name TYPE VARCHAR COLLATE korean;
ALTER TABLE organization    ALTER COLUMN name TYPE VARCHAR COLLATE korean;
ALTER TABLE billable_metric ALTER COLUMN name TYPE VARCHAR COLLATE korean;
