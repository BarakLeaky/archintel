ALTER TABLE sources
    ALTER COLUMN content_hash TYPE VARCHAR(64)
    USING TRIM(content_hash);

ALTER TABLE opportunities
    ALTER COLUMN content_hash TYPE VARCHAR(64)
    USING TRIM(content_hash);
