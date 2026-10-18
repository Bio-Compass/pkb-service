-- Preserve historical records: an operator must resolve conflicting replacements.
DO $$
BEGIN
    IF EXISTS (
        SELECT supersedes FROM pkb_item
        WHERE supersedes IS NOT NULL
        GROUP BY supersedes HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot enforce single PKB replacement: duplicate supersedes values exist'
            USING HINT = 'Find conflicts with SELECT supersedes, array_agg(pkb_item_id) FROM pkb_item WHERE supersedes IS NOT NULL GROUP BY supersedes HAVING count(*) > 1; resolve them manually, then rerun Flyway.';
    END IF;
END $$;

CREATE UNIQUE INDEX uq_pkb_item_supersedes ON pkb_item (supersedes) WHERE supersedes IS NOT NULL;
DROP INDEX idx_pkb_item_supersedes;
