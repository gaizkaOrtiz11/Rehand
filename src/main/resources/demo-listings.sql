-- =====================================================================
-- Rehand demo listings (idempotent: INSERT IGNORE with fixed ids)
-- =====================================================================
-- 24 listings for the two demo members (user ids 2 and 3) with photos from static/img/demo
-- (stored as demo/<file>.svg, shown at /img/demo/<file>.svg). Two listings are SOLD; none are RESERVED (a reservation only comes from an accepted offer).
-- Loaded after data.sql only when app.load-demo-data=true (see config/DemoDataLoader.java),
-- or by hand:  mysql -u rehand -p rehand < src/main/resources/demo-listings.sql
-- Do not put a semicolon inside a text value: the script runner splits statements at semicolons.
-- =====================================================================

INSERT IGNORE INTO listings
    (id, seller_id, category_id, title, description, price, item_condition, brand, size, color, city, status, view_count, created_at) VALUES
    (1000, 2, 11, 'Quilted puffer jacket, forest green', 'Warm quilted jacket with a high collar and two zip pockets. Worn for one winter, no stains or tears. Machine washed and ready to wear.', 38.00, 'VERY_GOOD', 'Zara', 'M', 'Green', 'Kaunas', 'ACTIVE', 41, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
    (1001, 3, 12, 'Men''s padded jacket, barely worn', 'Light padded jacket that works from autumn to early spring. Bought in the wrong size and worn twice. Original zip pulls, nothing missing.', 52.00, 'NEW', 'Decathlon', 'L', 'Green', 'Vilnius', 'ACTIVE', 18, DATE_SUB(NOW(), INTERVAL 20 HOUR)),
    (1002, 2, 13, 'White leather sneakers with orange stripe', 'Clean everyday sneakers, leather upper with an orange stripe. Small crease marks near the toe, soles still have plenty of grip.', 34.00, 'VERY_GOOD', 'Adidas', '40', 'White', 'Kaunas', 'ACTIVE', 57, DATE_SUB(NOW(), INTERVAL 31 HOUR)),
    (1003, 3, 13, 'Court sneakers, size 43', 'Comfortable court shoes, used for a season of indoor football. Laces are new. Slight wear on the heel, otherwise in good shape.', 28.00, 'GOOD', 'Nike', '43', 'White', 'Vilnius', 'ACTIVE', 22, DATE_SUB(NOW(), INTERVAL 50 HOUR)),
    (1004, 2, 14, 'Navy daypack with laptop sleeve', 'Sturdy 20 litre daypack with a padded sleeve that fits a 15 inch laptop. Front pocket, side straps and a reinforced base. Used for one semester.', 24.00, 'VERY_GOOD', 'Fjällräven', '20 L', 'Navy', 'Kaunas', 'ACTIVE', 33, DATE_SUB(NOW(), INTERVAL 72 HOUR)),
    (1005, 3, 53, 'Hiking backpack 45 L', 'Comfortable carry system with an adjustable back panel and a rain cover. Took it on two multi-day trips. One strap has a small frayed spot but works perfectly.', 59.00, 'GOOD', 'Osprey', '45 L', 'Blue', 'Vilnius', 'ACTIVE', 64, DATE_SUB(NOW(), INTERVAL 96 HOUR)),
    (1006, 3, 51, 'City bike, 7 speed, green frame', 'Reliable city bike with a step-over frame, mudguards and a rear rack. New brake pads in October. Includes a lock and a front light.', 185.00, 'GOOD', 'Trek', '54 cm', 'Green', 'Vilnius', 'ACTIVE', 96, DATE_SUB(NOW(), INTERVAL 120 HOUR)),
    (1007, 2, 51, 'Hybrid bike, recently serviced', 'Light aluminium hybrid, serviced last month: new chain, cables and tyres. Smooth 21 gears. Ideal for commuting along the river.', 240.00, 'VERY_GOOD', 'Giant', '52 cm', 'Green', 'Klaipėda', 'ACTIVE', 73, DATE_SUB(NOW(), INTERVAL 150 HOUR)),
    (1008, 3, 23, 'Over-ear wireless headphones', 'Noise cancelling headphones with about 30 hours of battery life. Ear pads were replaced in spring. Comes with the case and the charging cable.', 62.00, 'VERY_GOOD', 'Sony', NULL, 'Black', 'Vilnius', 'ACTIVE', 88, DATE_SUB(NOW(), INTERVAL 170 HOUR)),
    (1009, 2, 23, 'Studio headphones with case', 'Wired studio headphones with a detachable cable and a hard case. Clear, neutral sound.', 48.00, 'GOOD', 'Audio-Technica', NULL, 'Black', 'Kaunas', 'ACTIVE', 51, DATE_SUB(NOW(), INTERVAL 200 HOUR)),
    (1010, 2, 22, '13 inch laptop, 16 GB RAM, 512 GB SSD', 'Compact laptop used for coursework. Fresh operating system install, battery holds about six hours. A few light scratches on the lid.', 520.00, 'VERY_GOOD', 'Lenovo', '13 inch', 'Grey', 'Kaunas', 'ACTIVE', 120, DATE_SUB(NOW(), INTERVAL 230 HOUR)),
    (1011, 3, 22, 'Student laptop, good battery', 'Reliable 15 inch laptop for notes, browsing and light programming. Charger included.', 310.00, 'GOOD', 'HP', '15 inch', 'Silver', 'Šiauliai', 'ACTIVE', 67, DATE_SUB(NOW(), INTERVAL 260 HOUR)),
    (1012, 3, 41, 'Stack of 10 novels, Lithuanian and English', 'Ten paperback novels: contemporary fiction, two classics and a few thrillers. Read once, spines intact. Sold together only.', 15.00, 'GOOD', NULL, NULL, NULL, 'Vilnius', 'ACTIVE', 29, DATE_SUB(NOW(), INTERVAL 290 HOUR)),
    (1013, 2, 42, 'Calculus and linear algebra textbooks', 'Two university textbooks in English with a few pencil notes in the margins. Useful for first-year engineering and science courses.', 30.00, 'GOOD', 'Pearson', NULL, NULL, 'Kaunas', 'ACTIVE', 46, DATE_SUB(NOW(), INTERVAL 320 HOUR)),
    (1014, 3, 42, 'Data structures and algorithms textbook', 'Clear, thorough textbook with exercises. No highlighting. Sold to another student.', 22.00, 'VERY_GOOD', NULL, NULL, NULL, 'Vilnius', 'SOLD', 35, DATE_SUB(NOW(), INTERVAL 350 HOUR)),
    (1015, 2, 33, 'Linen shade table lamp', 'Small table lamp with a cream linen shade and a black metal stem. Works with any E14 bulb. Gives a warm, soft light for a desk or bedside.', 18.00, 'VERY_GOOD', 'IKEA', NULL, 'Cream', 'Kaunas', 'ACTIVE', 24, DATE_SUB(NOW(), INTERVAL 380 HOUR)),
    (1016, 3, 33, 'Floor lamp with warm light', 'Slim floor lamp with a fabric shade and a stable round base. Cable is 2 metres long. Moving abroad, so it has to go.', 35.00, 'GOOD', NULL, NULL, 'Cream', 'Bilbao', 'ACTIVE', 19, DATE_SUB(NOW(), INTERVAL 410 HOUR)),
    (1017, 2, 24, 'Mirrorless camera with 18-55 mm lens', 'Compact mirrorless camera with the kit lens, two batteries, charger and a strap. Shutter count is low. Great for travel and portraits.', 410.00, 'VERY_GOOD', 'Canon', NULL, 'Black', 'Kaunas', 'ACTIVE', 134, DATE_SUB(NOW(), INTERVAL 440 HOUR)),
    (1018, 3, 24, 'Compact travel camera', 'Pocket camera with a 10x zoom and a flip screen. Sold with the case and a 32 GB memory card.', 120.00, 'GOOD', 'Panasonic', NULL, 'Black', 'Vilnius', 'SOLD', 58, DATE_SUB(NOW(), INTERVAL 470 HOUR)),
    (1019, 2, 31, 'Solid wood dining chair', 'Sturdy chair with a green cushion that can be removed and washed. A few marks on the legs, nothing structural. Pickup in Kaunas.', 25.00, 'GOOD', NULL, NULL, 'Brown', 'Kaunas', 'ACTIVE', 31, DATE_SUB(NOW(), INTERVAL 500 HOUR)),
    (1020, 3, 31, 'Set of two oak chairs', 'Two matching chairs with slatted backs. Wood is in good shape and the joints are tight. Perfect for a small kitchen table.', 60.00, 'VERY_GOOD', NULL, NULL, 'Brown', 'Vilnius', 'ACTIVE', 27, DATE_SUB(NOW(), INTERVAL 530 HOUR)),
    (1021, 3, 14, 'Everyday backpack, black straps', 'Plenty of room for a day at the library. Zips run smoothly. One inner seam is a bit worn, which is why the price is low.', 16.00, 'FAIR', NULL, NULL, 'Navy', 'Bilbao', 'ACTIVE', 12, DATE_SUB(NOW(), INTERVAL 560 HOUR)),
    (1022, 2, 25, 'Gaming headset with microphone', 'Closed-back headset with a flexible microphone and a long cable. Works on PC and consoles. Cushions are in good shape.', 29.00, 'GOOD', NULL, NULL, 'Black', 'Klaipėda', 'ACTIVE', 21, DATE_SUB(NOW(), INTERVAL 590 HOUR)),
    (1023, 2, 44, 'Comic book bundle, 12 issues', 'Twelve comic issues in plastic sleeves. Mixed series, all in English. A good starter lot for a collection.', 12.00, 'GOOD', NULL, NULL, NULL, 'Klaipėda', 'ACTIVE', 15, DATE_SUB(NOW(), INTERVAL 620 HOUR));

INSERT IGNORE INTO listing_photos (id, listing_id, file_path, position) VALUES
    (2000, 1000, 'demo/jacket.svg', 0),
    (2001, 1000, 'demo/jacket-2.svg', 1),
    (2002, 1000, 'demo/jacket-3.svg', 2),
    (2003, 1001, 'demo/jacket.svg', 0),
    (2004, 1001, 'demo/jacket-2.svg', 1),
    (2005, 1002, 'demo/sneakers.svg', 0),
    (2006, 1002, 'demo/sneakers-2.svg', 1),
    (2007, 1002, 'demo/sneakers-3.svg', 2),
    (2008, 1003, 'demo/sneakers.svg', 0),
    (2009, 1004, 'demo/backpack.svg', 0),
    (2010, 1004, 'demo/backpack-2.svg', 1),
    (2011, 1005, 'demo/backpack.svg', 0),
    (2012, 1006, 'demo/bike.svg', 0),
    (2013, 1006, 'demo/bike-2.svg', 1),
    (2014, 1007, 'demo/bike.svg', 0),
    (2015, 1008, 'demo/headphones.svg', 0),
    (2016, 1008, 'demo/headphones-2.svg', 1),
    (2017, 1009, 'demo/headphones.svg', 0),
    (2018, 1010, 'demo/laptop.svg', 0),
    (2019, 1010, 'demo/laptop-2.svg', 1),
    (2020, 1011, 'demo/laptop.svg', 0),
    (2021, 1012, 'demo/books.svg', 0),
    (2022, 1012, 'demo/books-2.svg', 1),
    (2023, 1013, 'demo/books.svg', 0),
    (2024, 1014, 'demo/books.svg', 0),
    (2025, 1015, 'demo/lamp.svg', 0),
    (2026, 1015, 'demo/lamp-2.svg', 1),
    (2027, 1016, 'demo/lamp.svg', 0),
    (2028, 1017, 'demo/camera.svg', 0),
    (2029, 1017, 'demo/camera-2.svg', 1),
    (2030, 1018, 'demo/camera.svg', 0),
    (2031, 1019, 'demo/chair.svg', 0),
    (2032, 1019, 'demo/chair-2.svg', 1),
    (2033, 1020, 'demo/chair.svg', 0),
    (2034, 1021, 'demo/backpack.svg', 0),
    (2035, 1022, 'demo/headphones.svg', 0),
    (2036, 1023, 'demo/books.svg', 0);
