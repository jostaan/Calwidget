package vn.xin.clockwidget

/** Danh sách font Google (OFL) đóng gói sẵn. Tệp này được sinh tự động. */
class FontEntry(
    val label: String,
    val widgetLayout: Int,
    val notifLayout: Int,
    val notifBigLayout: Int
)

object FontCatalog {
    val all: List<FontEntry> = listOf(
        FontEntry("Be Vietnam Pro (hiện đại, thiết kế cho tiếng Việt)", R.layout.w_be_vietnam_pro, R.layout.n_be_vietnam_pro, R.layout.nb_be_vietnam_pro),
        FontEntry("Montserrat (hình học)", R.layout.w_montserrat, R.layout.n_montserrat, R.layout.nb_montserrat),
        FontEntry("Nunito (bo tròn)", R.layout.w_nunito, R.layout.n_nunito, R.layout.nb_nunito),
        FontEntry("Quicksand (bo tròn, mảnh)", R.layout.w_quicksand, R.layout.n_quicksand, R.layout.nb_quicksand),
        FontEntry("Josefin Sans (thanh lịch)", R.layout.w_josefin_sans, R.layout.n_josefin_sans, R.layout.nb_josefin_sans),
        FontEntry("Oswald (hẹp, đậm)", R.layout.w_oswald, R.layout.n_oswald, R.layout.nb_oswald),
        FontEntry("Playfair Display (serif sang trọng)", R.layout.w_playfair_display, R.layout.n_playfair_display, R.layout.nb_playfair_display),
        FontEntry("Lora (serif dễ đọc)", R.layout.w_lora, R.layout.n_lora, R.layout.nb_lora),
        FontEntry("Dancing Script (viết tay)", R.layout.w_dancing_script, R.layout.n_dancing_script, R.layout.nb_dancing_script),
        FontEntry("Pacifico (viết tay đậm)", R.layout.w_pacifico, R.layout.n_pacifico, R.layout.nb_pacifico),
        FontEntry("Roboto Mono (kiểu đồng hồ số)", R.layout.w_roboto_mono, R.layout.n_roboto_mono, R.layout.nb_roboto_mono)
    )

    fun get(i: Int): FontEntry = all[i.coerceIn(0, all.size - 1)]
}
