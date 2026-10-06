ALTER TABLE defects
    ALTER COLUMN created_by SET NOT NULL;

ALTER TABLE defects
    ADD CONSTRAINT ck_defects_created_by_not_blank
        CHECK (length(btrim(created_by)) > 0);

ALTER TABLE defects
    ADD CONSTRAINT ck_defects_resolved_by
        CHECK (
            (
                status IN ('OPEN', 'WAITING_PARTS')
                    AND resolved_by IS NULL
                )
                OR
            (
                status IN ('RESOLVED', 'WRITTEN_OFF')
                    AND resolved_by IS NOT NULL
                    AND length(btrim(resolved_by)) > 0
                )
            );