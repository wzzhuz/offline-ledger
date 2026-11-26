package com.zwz.offlineledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.zwz.offlineledger.R
import com.zwz.offlineledger.engine.CategoryIconKeys
import com.zwz.offlineledger.engine.CategoryIconTints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.engine.CategoryManagementRules
import com.zwz.offlineledger.ui.theme.LedgerColors

/** 分类管理三页的蓝底顶栏 / 主按钮共用色。
 * 第 78 批：改为读取可观察主色，动态色 / 深浅切换下不再与 colorScheme.primary 两套蓝。
 */
val CategoryHeaderBlue: Color get() = LedgerColors.Primary

/**
 * 第 55 批：分类管理三页图标的**唯一出口**（Tabler Icons outline / MIT）。
 *
 * `when (iconKey)` 是编译期显式常量表，每个分支引用随包本地 VectorDrawable；
 * MUST NOT 用字符串在运行时反射 / 查表映射成 ImageVector（活文档 T14）。
 * 目录缺项 MUST 回退 `CategoryIcon` 中性圆 + 中文首字。
 *
 * 图标资源来自 Tabler Icons v3.34.0（pin 版本，非 @latest），
 * 6,257 个 outline SVG 中按需取用 90 个，转成 res/drawable 本地资源，
 * **零新增第三方依赖、零运行时网络**。来源、许可证与版权声明见
 * `assets/NOTICE` 与 change 的 `design.md`「依赖与许可」。
 */
object CategoryIconCatalog {
    /** 返回 @DrawableRes；编译期常量表，未知 key 返回 null。 */
    fun resIdOf(iconKey: String): Int? = when (iconKey) {
        "alert-triangle" -> R.drawable.ic_cat_alert_triangle
        "app-window" -> R.drawable.ic_cat_app_window
        "apple" -> R.drawable.ic_cat_apple
        "award" -> R.drawable.ic_cat_award
        "baby-carriage" -> R.drawable.ic_cat_baby_carriage
        "backpack" -> R.drawable.ic_cat_backpack
        "ball-basketball" -> R.drawable.ic_cat_ball_basketball
        "bath" -> R.drawable.ic_cat_bath
        "bed" -> R.drawable.ic_cat_bed
        "bike" -> R.drawable.ic_cat_bike
        "bolt" -> R.drawable.ic_cat_bolt
        "bone" -> R.drawable.ic_cat_bone
        "book-2" -> R.drawable.ic_cat_book_2
        "books" -> R.drawable.ic_cat_books
        "bowl-chopsticks" -> R.drawable.ic_cat_bowl_chopsticks
        "box" -> R.drawable.ic_cat_box
        "briefcase" -> R.drawable.ic_cat_briefcase
        "bubble-tea" -> R.drawable.ic_cat_bubble_tea
        "building" -> R.drawable.ic_cat_building
        "bus" -> R.drawable.ic_cat_bus
        "calendar" -> R.drawable.ic_cat_calendar
        "candy" -> R.drawable.ic_cat_candy
        "capsule" -> R.drawable.ic_cat_capsule
        "car" -> R.drawable.ic_cat_car
        "cash" -> R.drawable.ic_cat_cash
        "certificate" -> R.drawable.ic_cat_certificate
        "certificate-2" -> R.drawable.ic_cat_certificate_2
        "chart-line" -> R.drawable.ic_cat_chart_line
        "chef-hat" -> R.drawable.ic_cat_chef_hat
        "clock" -> R.drawable.ic_cat_clock
        "coffee" -> R.drawable.ic_cat_coffee
        "coin" -> R.drawable.ic_cat_coin
        "currency-yuan" -> R.drawable.ic_cat_currency_yuan
        "dental" -> R.drawable.ic_cat_dental
        "device-gamepad" -> R.drawable.ic_cat_device_gamepad
        "device-mobile" -> R.drawable.ic_cat_device_mobile
        "dog-bowl" -> R.drawable.ic_cat_dog_bowl
        "dots" -> R.drawable.ic_cat_dots
        "droplet" -> R.drawable.ic_cat_droplet
        "eyeglass" -> R.drawable.ic_cat_eyeglass
        "ferry" -> R.drawable.ic_cat_ferry
        "file-certificate" -> R.drawable.ic_cat_file_certificate
        "file-text" -> R.drawable.ic_cat_file_text
        "flame" -> R.drawable.ic_cat_flame
        "gas-station" -> R.drawable.ic_cat_gas_station
        "gift" -> R.drawable.ic_cat_gift
        "gift-card" -> R.drawable.ic_cat_gift_card
        "hammer" -> R.drawable.ic_cat_hammer
        "heart-handshake" -> R.drawable.ic_cat_heart_handshake
        "heartbeat" -> R.drawable.ic_cat_heartbeat
        "help-circle" -> R.drawable.ic_cat_help_circle
        "home" -> R.drawable.ic_cat_home
        "home-move" -> R.drawable.ic_cat_home_move
        "key" -> R.drawable.ic_cat_key
        "masks-theater" -> R.drawable.ic_cat_masks_theater
        "medical-cross" -> R.drawable.ic_cat_medical_cross
        "moon-stars" -> R.drawable.ic_cat_moon_stars
        "motorbike" -> R.drawable.ic_cat_motorbike
        "movie" -> R.drawable.ic_cat_movie
        "package" -> R.drawable.ic_cat_package
        "parking" -> R.drawable.ic_cat_parking
        "paw" -> R.drawable.ic_cat_paw
        "percentage" -> R.drawable.ic_cat_percentage
        "phone" -> R.drawable.ic_cat_phone
        "pill" -> R.drawable.ic_cat_pill
        "plane" -> R.drawable.ic_cat_plane
        "receipt" -> R.drawable.ic_cat_receipt
        "receipt-refund" -> R.drawable.ic_cat_receipt_refund
        "road" -> R.drawable.ic_cat_road
        "rotate" -> R.drawable.ic_cat_rotate
        "run" -> R.drawable.ic_cat_run
        "shield" -> R.drawable.ic_cat_shield
        "shirt" -> R.drawable.ic_cat_shirt
        "shopping-bag" -> R.drawable.ic_cat_shopping_bag
        "shopping-cart" -> R.drawable.ic_cat_shopping_cart
        "smoking" -> R.drawable.ic_cat_smoking
        "sofa" -> R.drawable.ic_cat_sofa
        "sparkles" -> R.drawable.ic_cat_sparkles
        "spray" -> R.drawable.ic_cat_spray
        "steering-wheel" -> R.drawable.ic_cat_steering_wheel
        "stethoscope" -> R.drawable.ic_cat_stethoscope
        "temperature" -> R.drawable.ic_cat_temperature
        "tool" -> R.drawable.ic_cat_tool
        "tools-kitchen-2" -> R.drawable.ic_cat_tools_kitchen_2
        "trending-up" -> R.drawable.ic_cat_trending_up
        "trophy" -> R.drawable.ic_cat_trophy
        "users" -> R.drawable.ic_cat_users
        "vaccine" -> R.drawable.ic_cat_vaccine
        "wallet" -> R.drawable.ic_cat_wallet
        "wifi" -> R.drawable.ic_cat_wifi
        else -> null
    }
}


data class CategoryTile(
    val id: Long,
    val name: String,
    val iconName: String = "",
    val colorHex: String = "#888888",
    val enabled: Boolean = true,
    val isAdd: Boolean = false
)

/** 分类管理三页共用的图标格。 */
/**
 * 一羽式「浅色圆底 + 单色线性图标」。
 * 方案 A 的兜底路径原样保留：目录缺项 → 中性圆 + 中文首字。
 * grayscale=true 时按一羽图 3 降饱和为中性灰（排序页专用）。
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun CategoryGlyph(
    name: String,
    iconName: String,
    colorHex: String,
    grayscale: Boolean,
    size: Int = 40,
    categoryId: Long = 0L
) {
    // 兼容将来 iconName 里带 "|" 的扩展；当前清单只写纯 iconKey。
    // iconName 为空时按 id 回查预置表 —— 覆盖安装（库已存在）也能立刻显示图标，
    // 零改表、零写库、零 Entity 变更。
    val key = iconName.substringBefore('|').trim()
        .ifEmpty { CategoryIconKeys.keyOfPreset(categoryId).orEmpty() }
    val resId = if (key.isEmpty()) null else CategoryIconCatalog.resIdOf(key)
    if (resId == null) {
        CategoryIcon(name = name, colorHex = colorHex, size = size)
        return
    }
    val argb = CategoryIconTints.tintOf(key) ?: CategoryIconTints.FALLBACK
    val base = if (grayscale) CategoryIconTints.GRAYSCALE else argb
    val tint = Color(base)
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(tint.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(resId),
            contentDescription = name,
            tint = tint,
            modifier = Modifier.size((size * 0.56f).dp)
        )
    }
}

/**
 * 固定列数等宽图标网格：每格 weight(1f)、固定格高、gutter 均匀、文字单行省略。
 * MUST NOT 用 LazyVerticalGrid 嵌进 LazyColumn.item {}（同方向嵌套滚动 + 高度不确定），
 * MUST NOT 用 FlowRow + ExperimentalLayoutApi（活文档 T12）。
 */
@Composable
fun CategoryIconGrid(
    tiles: List<CategoryTile>,
    onTile: (CategoryTile) -> Unit,
    columns: Int = 5,
    grayscale: Boolean = false,
    modifier: Modifier = Modifier
) {
    val rows = CategoryManagementRules.gridCells(tiles.size, columns).chunked(columns)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val tile = index?.let { tiles[it] }
                        if (tile != null) {
                            CategoryTileCell(tile, grayscale) { onTile(tile) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryTileCell(
    tile: CategoryTile,
    grayscale: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            if (tile.isAdd) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(LedgerColors.Neutral.copy(alpha = 0.10f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "＋",
                        style = MaterialTheme.typography.titleMedium,
                        color = LedgerColors.Neutral
                    )
                }
            } else {
                CategoryGlyph(
                    name = tile.name,
                    iconName = tile.iconName,
                    colorHex = tile.colorHex,
                    grayscale = grayscale,
                    size = 40,
                    categoryId = tile.id
                )
            }
            // 停用改小角标，MUST NOT 再拼进名称把格子撑破。
            if (!tile.enabled && !tile.isAdd) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(LedgerColors.ExpenseRed, CircleShape)
                )
            }
        }
        Text(
            text = tile.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
        )
    }
}
