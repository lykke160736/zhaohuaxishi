// 平台统一食物图片资源（AI 生图服务，写实质感美食摄影）
const t2i = (prompt, size = 'landscape_4_3') =>
  `https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=${encodeURIComponent(prompt)}&image_size=${size}`

// 首页主视觉：面包市集俯拍
export const HERO_IMG = t2i(
  'overhead flat lay of fresh baked bread, baguettes, croissants and wheat ears on natural linen cloth, warm cream and beige tones, soft morning light, appetizing realistic food photography',
  'landscape_16_9'
)

// 盲盒意象：牛皮纸袋装着惊喜食物
export const BOX_IMG = t2i(
  'kraft paper takeout bag with fresh artisan bread and golden pastry peeking out, tied with cotton twine, mystery surprise food box concept, warm side light, cream background, realistic food photography',
  'square'
)

// 各门店实拍图
export const STORE_IMGS = {
  S1: t2i(
    'rustic artisan bakery wooden counter with fresh crusty sourdough loaves and golden croissants, flour dust floating in warm morning sunlight, appetizing realistic food photography, shallow depth of field'
  ),
  S2: t2i(
    'crispy golden fried chicken pieces piled in craft paper box, glistening crunchy coating with light steam, appetizing realistic food photography, warm dark moody background'
  ),
  S3: t2i(
    'chinese restaurant buffet line with steaming stainless trays of colorful stir fry dishes, braised meat and rice, appetizing spread, warm restaurant lighting, realistic food photography'
  ),
  S4: t2i(
    'traditional chinese pastries, mung bean cakes and walnut crisp cookies arranged on rustic ceramic plate, cup of tea aside, warm soft window light, appetizing realistic food photography'
  ),
  S5: t2i(
    'home style bento rice box with glossy braised pork belly, green vegetables and marinated egg, wooden table, warm evening light, appetizing realistic food photography'
  ),
  // —— 新增门店各自独立实拍图，避免同品类重复 ——
  S6: t2i(
    'bright morning bakery window display with stacked milk toast loaves and golden butter croissants on wooden trays, sunlight streaming through glass, airy minimal bakery interior, realistic food photography'
  ),
  S7: t2i(
    'taiwanese braised pork rice bowl with shiny dark soy braised pork over white rice, pickled cucumber and braised tofu on the side, street food stall counter, steam rising, appetizing realistic food photography'
  ),
  S8: t2i(
    'handmade mochi rice cakes dusted with peanut powder on bamboo mat, sticky and soft texture pulled apart, small ceramic dish of crushed peanuts, warm homely kitchen light, realistic food photography'
  ),
  S9: t2i(
    'chinese canteen self service counter with divided stainless steel tray of stir fried greens braised fish and rice, sneeze guard glass, bright cafeteria lighting, realistic food photography'
  ),
  S10: t2i(
    'large taiwanese fried chicken cutlet in paper bag sprinkled with pepper salt, crispy golden crust close up, street vendor background, bokeh night lights, appetizing realistic food photography'
  ),
  S11: t2i(
    'artisan sourdough bread loaves in a woven basket on a seaside cafe windowsill, ocean horizon blurred behind glass, morning light, coastal bakery atmosphere, realistic food photography'
  ),
  S12: t2i(
    'fresh healthy salad bowl with avocado quinoa cherry tomatoes and grilled chicken breast, drizzled olive oil, bright white ceramic bowl, clean minimal cafe table, realistic food photography'
  ),
  S13: t2i(
    'freshly cut fruit cups with strawberries kiwi mango and blueberries in clear containers, bright juice bar display, vibrant colors, realistic food photography'
  ),
  S14: t2i(
    'warm milk tea in kraft paper cup with tapioca pearls at bottom, steam rising, cozy tea shop wooden counter, blurred warm lights background, realistic beverage photography'
  ),
  S15: t2i(
    'silky tofu pudding douhua in ceramic bowl topped with brown sugar syrup and peanut crumble, old alley wooden table, soft afternoon light, realistic food photography'
  ),
  S16: t2i(
    'steamed chinese buns and baozi piled in bamboo steamer baskets, white fluffy tops, morning market stall, steam rising, realistic food photography'
  ),
  S17: t2i(
    'charcoal grilled chicken skewers with glossy glaze on a small iron grill, glowing embers, smoke wisps, dark moody street food atmosphere, appetizing realistic food photography'
  ),
  S18: t2i(
    'simple chinese homestyle meal set with braised chicken rice and seasonal greens on a white tray, bright dining hall table, clean minimal presentation, realistic food photography'
  ),
  S19: t2i(
    'colorful fruit dessert bowl filled with mixed fresh fruits yogurt and crushed ice, tall glass cup, bright sunshine cafe counter, realistic dessert photography'
  )
}

// 品类默认图（新入驻门店按品类取用）
export const CATEGORY_IMGS = {
  面包: STORE_IMGS.S1,
  炸鸡: STORE_IMGS.S2,
  自助餐: STORE_IMGS.S3,
  糕点: STORE_IMGS.S4,
  便当: STORE_IMGS.S5,
  轻食: STORE_IMGS.S12,
  水果: STORE_IMGS.S13,
  饮品: STORE_IMGS.S14
}

export const storeImg = (s) => STORE_IMGS[s.id] || CATEGORY_IMGS[s.category] || BOX_IMG
