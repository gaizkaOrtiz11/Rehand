package com.rehand.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the list of page numbers shown in the pagination bar: first, last and a window
 * around the current page. A 0 in the list stands for a gap ("...").
 */
public final class PageNav {

    private PageNav() {
    }

    /**
     * @param current current page, 1-based
     * @param total   number of pages
     */
    public static List<Integer> window(int current, int total) {
        List<Integer> out = new ArrayList<>();
        if (total <= 1) {
            return out;
        }
        int last = 0;
        for (int p = 1; p <= total; p++) {
            boolean show = p == 1 || p == total || Math.abs(p - current) <= 2;
            if (!show) {
                continue;
            }
            if (last != 0 && p - last > 1) {
                out.add(0);
            }
            out.add(p);
            last = p;
        }
        return out;
    }
}
