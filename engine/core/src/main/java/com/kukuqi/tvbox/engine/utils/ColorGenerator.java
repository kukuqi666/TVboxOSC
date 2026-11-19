package com.kukuqi.tvbox.engine.utils;

import java.util.ArrayList;
import java.util.List;

public class ColorGenerator {

    private static final List<Integer> PALETTE_400;
    private static final List<Integer> PALETTE_700;

    // 保持原有头像配色，移除仅提供常量的旧颜色库。
    static {
        PALETTE_400 = new ArrayList<>();
        PALETTE_400.add(0xFFEF5350);
        PALETTE_400.add(0xFFEC407A);
        PALETTE_400.add(0xFFAB47BC);
        PALETTE_400.add(0xFF7E57C2);
        PALETTE_400.add(0xFF5C6BC0);
        PALETTE_400.add(0xFF42A5F5);
        PALETTE_400.add(0xFF29B6F6);
        PALETTE_400.add(0xFF26C6DA);
        PALETTE_400.add(0xFF26A69A);
        PALETTE_400.add(0xFF66BB6A);
        PALETTE_400.add(0xFF9CCC65);
        PALETTE_400.add(0xFFD4E157);
        PALETTE_400.add(0xFFFFEE58);
        PALETTE_400.add(0xFFFFCA28);
        PALETTE_400.add(0xFFFFA726);
        PALETTE_400.add(0xFFFF7043);
        PALETTE_400.add(0xFF8D6E63);
        PALETTE_400.add(0xFFBDBDBD);
        PALETTE_400.add(0xFF78909C);

        PALETTE_700 = new ArrayList<>();
        PALETTE_700.add(0xFFD32F2F);
        PALETTE_700.add(0xFFC2185B);
        PALETTE_700.add(0xFF7B1FA2);
        PALETTE_700.add(0xFF512DA8);
        PALETTE_700.add(0xFF303F9F);
        PALETTE_700.add(0xFF1976D2);
        PALETTE_700.add(0xFF0288D1);
        PALETTE_700.add(0xFF0097A7);
        PALETTE_700.add(0xFF00796B);
        PALETTE_700.add(0xFF388E3C);
        PALETTE_700.add(0xFF689F38);
        PALETTE_700.add(0xFFAFB42B);
        PALETTE_700.add(0xFFFBC02D);
        PALETTE_700.add(0xFFFFA000);
        PALETTE_700.add(0xFFF57C00);
        PALETTE_700.add(0xFFE64A19);
        PALETTE_700.add(0xFF5D4037);
        PALETTE_700.add(0xFF616161);
        PALETTE_700.add(0xFF455A64);
    }

    public static int get400(String key) {
        return PALETTE_400.get((key.hashCode() & Integer.MAX_VALUE) % PALETTE_400.size());
    }

    public static int get700(String key) {
        return PALETTE_700.get((key.hashCode() & Integer.MAX_VALUE) % PALETTE_700.size());
    }
}
