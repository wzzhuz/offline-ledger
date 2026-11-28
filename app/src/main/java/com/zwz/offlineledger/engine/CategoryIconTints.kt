package com.zwz.offlineledger.engine

/**
 * 第 55 批：分类管理三页彩色图标的**领域配色表**（纯 JVM，零 Android 依赖）。
 *
 * 设计取舍：Tabler 是单色线性图标（stroke=currentColor），上色由本表控制。
 * 按一级领域分组、**同组同色**，共 11 种低饱和色；MUST NOT 逐图标随机高饱和
 * ——那正是活文档 §2.6 记一笔页「6~7 种颜色一眼不清晰」的翻车原因。
 *
 * 与统计口径无关：本表返回 ARGB 位模式 Int，MUST NOT 改写参与环形图扇区
 * 取色的 `colorHex`。排序页灰阶用 [GRAYSCALE]，浏览页用本表色。
 */
object CategoryIconTints {
    // 支出领域
    val FoodOrange: Int = 0xFFE07B4A.toInt()
    val TransportBlue: Int = 0xFF4A86C8.toInt()
    val CarSlate: Int = 0xFF6E88A8.toInt()
    val HomeTeal: Int = 0xFF2F9C8C.toInt()
    val ShoppingPurple: Int = 0xFF8B6BC4.toInt()
    val MedicalRed: Int = 0xFFD15C55.toInt()
    val LeisureGreen: Int = 0xFF4C9A63.toInt()
    val PetBrown: Int = 0xFFB9824B.toInt()
    val SocialPink: Int = 0xFFC2628C.toInt()
    val OtherGray: Int = 0xFF77808C.toInt()
    // 收入统一绿（与 LedgerColors.IncomeGreen 同值，中文语境）
    val IncomeGreen: Int = 0xFF2E7D32.toInt()
    /** 排序页降饱和：一羽图 3 自己就是灰阶。 */
    val GRAYSCALE: Int = 0xFF616161.toInt()
    /** 目录缺项时的兜底色（实际走 CategoryIcon 中性圆，这里只防 null）。 */
    val FALLBACK: Int = 0xFF616161.toInt()

    /** 编译期显式常量表；未知 key MUST 返回 null，MUST NOT 运行时反射。 */
    fun tintOf(iconKey: String): Int? = when (iconKey) {
        "alert-triangle" -> CarSlate
        "app-window" -> LeisureGreen
        "apple" -> FoodOrange
        "award" -> IncomeGreen
        "baby-carriage" -> ShoppingPurple
        "backpack" -> ShoppingPurple
        "ball-basketball" -> ShoppingPurple
        "bath" -> PetBrown
        "bed" -> MedicalRed
        "bike" -> TransportBlue
        "bolt" -> HomeTeal
        "bone" -> PetBrown
        "book-2" -> LeisureGreen
        "books" -> LeisureGreen
        "bowl-chopsticks" -> FoodOrange
        "box" -> CarSlate
        "briefcase" -> IncomeGreen
        "bubble-tea" -> FoodOrange
        "building" -> HomeTeal
        "bus" -> TransportBlue
        "calendar" -> IncomeGreen
        "candy" -> FoodOrange
        "capsule" -> MedicalRed
        "car" -> TransportBlue
        "cash" -> SocialPink
        "certificate" -> LeisureGreen
        "certificate-2" -> IncomeGreen
        "chart-line" -> IncomeGreen
        "chef-hat" -> FoodOrange
        "clock" -> IncomeGreen
        "coffee" -> FoodOrange
        "coin" -> IncomeGreen
        "currency-yuan" -> HomeTeal
        "dental" -> MedicalRed
        "device-gamepad" -> LeisureGreen
        "device-mobile" -> ShoppingPurple
        "dog-bowl" -> PetBrown
        "dots" -> OtherGray
        "droplet" -> HomeTeal
        "eyeglass" -> MedicalRed
        "ferry" -> TransportBlue
        "file-certificate" -> CarSlate
        "file-text" -> IncomeGreen
        "flame" -> HomeTeal
        "flower" -> HomeTeal
        "gas-station" -> CarSlate
        "gift" -> SocialPink
        "gift-card" -> SocialPink
        "hammer" -> HomeTeal
        "heart-handshake" -> SocialPink
        "heartbeat" -> MedicalRed
        "help-circle" -> OtherGray
        "home" -> HomeTeal
        "home-move" -> HomeTeal
        "key" -> TransportBlue
        "masks-theater" -> LeisureGreen
        "medical-cross" -> PetBrown
        "moon-stars" -> FoodOrange
        "motorbike" -> FoodOrange
        "movie" -> LeisureGreen
        "package" -> OtherGray
        "parking" -> CarSlate
        "paw" -> PetBrown
        "percentage" -> IncomeGreen
        "phone" -> HomeTeal
        "pill" -> MedicalRed
        "plane" -> TransportBlue
        "receipt" -> OtherGray
        "receipt-refund" -> IncomeGreen
        "road" -> CarSlate
        "rotate" -> IncomeGreen
        "run" -> LeisureGreen
        "scissors" -> ShoppingPurple
        "shield" -> CarSlate
        "shirt" -> ShoppingPurple
        "shopping-bag" -> ShoppingPurple
        "shopping-cart" -> ShoppingPurple
        "smoking" -> FoodOrange
        "sofa" -> HomeTeal
        "sparkles" -> ShoppingPurple
        "spray" -> CarSlate
        "steering-wheel" -> TransportBlue
        "stethoscope" -> MedicalRed
        "temperature" -> HomeTeal
        "tool" -> CarSlate
        "tools-kitchen-2" -> FoodOrange
        "trending-up" -> IncomeGreen
        "trophy" -> IncomeGreen
        "users" -> FoodOrange
        "vaccine" -> MedicalRed
        "wallet" -> IncomeGreen
        "wifi" -> HomeTeal
        else -> null
    }
}
