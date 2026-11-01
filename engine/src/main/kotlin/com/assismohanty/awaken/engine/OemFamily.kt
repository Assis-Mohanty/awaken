package com.assismohanty.awaken.engine

/** Phone makers that stop background apps aggressively, so the battery guide can show the right steps (GD 8.2). */
enum class OemFamily {
    XIAOMI, SAMSUNG, OPPO, ONEPLUS, VIVO, HUAWEI, OTHER;

    companion object {
        fun of(manufacturer: String?): OemFamily {
            val name = manufacturer.orEmpty().trim().lowercase()
            return when {
                name.contains("xiaomi") || name.contains("redmi") || name.contains("poco") -> XIAOMI
                name.contains("samsung") -> SAMSUNG
                name.contains("oneplus") -> ONEPLUS
                name.contains("oppo") || name.contains("realme") -> OPPO
                name.contains("vivo") || name.contains("iqoo") -> VIVO
                name.contains("huawei") || name.contains("honor") -> HUAWEI
                else -> OTHER
            }
        }
    }
}
