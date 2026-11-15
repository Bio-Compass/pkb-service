-- Payload domain meaning belongs to the producing service. Existing JSON objects are unchanged.
ALTER TABLE pkb_item DROP CONSTRAINT ck_pkb_item_source_quantity;
