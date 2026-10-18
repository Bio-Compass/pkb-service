ALTER TABLE pkb_item
    ADD COLUMN observed_timezone TEXT,
    ADD CONSTRAINT ck_pkb_item_observed_timezone_not_blank
        CHECK (observed_timezone IS NULL OR length(btrim(observed_timezone)) > 0);
