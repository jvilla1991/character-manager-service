-- Variant-rule opt-ins chosen at campaign creation (immutable afterward).
-- JSON-as-TEXT (house style, like campaign.threads / pc.inventory) so future
-- variants (Wear & Tear, ...) add keys, not columns. NULL = no variants.
-- Shape: {"slotInventory": true}
ALTER TABLE campaign ADD COLUMN IF NOT EXISTS variant_rules TEXT;
