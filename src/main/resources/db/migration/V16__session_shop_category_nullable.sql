-- A curated shop (session_shop.shop_id set) can span multiple catalog
-- categories, so it has no single category. Make category nullable; standard
-- shops still set it, curated shops leave it NULL and resolve items from shop_item.
ALTER TABLE session_shop ALTER COLUMN category DROP NOT NULL;
