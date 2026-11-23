package vn.xin.clockwidget

/** Danh sách font Google (OFL) đóng gói sẵn. Tệp này được sinh tự động. */
class FontEntry(
    val label: String,
    val regular: Int,
    val bold: Int,
    val widgetLayout: Int,
    val notifLayout: Int
)

object FontCatalog {
    val all: List<FontEntry> = listOf(
        FontEntry("Be Vietnam Pro (hiện đại, thiết kế cho tiếng Việt)", R.font.be_vietnam_pro_regular, R.font.be_vietnam_pro_bold, R.layout.w_be_vietnam_pro, R.layout.n_be_vietnam_pro),
        FontEntry("Montserrat (hình học)", R.font.montserrat_regular, R.font.montserrat_bold, R.layout.w_montserrat, R.layout.n_montserrat),
        FontEntry("Nunito (bo tròn)", R.font.nunito_regular, R.font.nunito_bold, R.layout.w_nunito, R.layout.n_nunito),
        FontEntry("Quicksand (bo tròn, mảnh)", R.font.quicksand_regular, R.font.quicksand_bold, R.layout.w_quicksand, R.layout.n_quicksand),
        FontEntry("Josefin Sans (thanh lịch)", R.font.josefin_sans_regular, R.font.josefin_sans_bold, R.layout.w_josefin_sans, R.layout.n_josefin_sans),
        FontEntry("Oswald (hẹp, đậm)", R.font.oswald_regular, R.font.oswald_bold, R.layout.w_oswald, R.layout.n_oswald),
        FontEntry("Playfair Display (serif sang trọng)", R.font.playfair_display_regular, R.font.playfair_display_bold, R.layout.w_playfair_display, R.layout.n_playfair_display),
        FontEntry("Lora (serif dễ đọc)", R.font.lora_regular, R.font.lora_bold, R.layout.w_lora, R.layout.n_lora),
        FontEntry("Dancing Script (viết tay)", R.font.dancing_script_regular, R.font.dancing_script_bold, R.layout.w_dancing_script, R.layout.n_dancing_script),
        FontEntry("Pacifico (viết tay đậm)", R.font.pacifico_regular, R.font.pacifico_regular, R.layout.w_pacifico, R.layout.n_pacifico),
        FontEntry("Roboto Mono (kiểu đồng hồ số)", R.font.roboto_mono_regular, R.font.roboto_mono_bold, R.layout.w_roboto_mono, R.layout.n_roboto_mono)
    )

    fun get(i: Int): FontEntry = all[i.coerceIn(0, all.size - 1)]
}
