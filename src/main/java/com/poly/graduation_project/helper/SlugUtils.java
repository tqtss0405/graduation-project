package com.poly.graduation_project.helper;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public class SlugUtils {
    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    public static String makeSlug(String input) {
        if (input == null) return "";
        // Chuyển sang chữ thường
        String nowhitespace = WHITESPACE.matcher(input).replaceAll("-");
        // Loại bỏ dấu tiếng Việt
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        String slug = pattern.matcher(normalized).replaceAll("");
        // Loại bỏ ký tự đặc biệt và dăm ba cái gạch ngang thừa
        slug = NONLATIN.matcher(slug).replaceAll("");
        return slug.toLowerCase(Locale.ENGLISH).replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
    }
}