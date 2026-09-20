package com.freeledger.app;

import android.app.Notification;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PaymentParser {
    private PaymentParser() {}

    private static final String NUMBER =
            "(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)";

    private static final Pattern STRONG_PREFIX = Pattern.compile(
            "(?:支付|付款|消费|扣款|支出|实付|交易金额|交易金额为|金额|付款金额)[^\\d¥￥]{0,14}(?:¥|￥|RMB|CNY)?\\s*" + NUMBER,
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern STRONG_SUFFIX = Pattern.compile(
            "(?:¥|￥|RMB|CNY)?\\s*" + NUMBER +
                    "\\s*(?:元)?[^\\n]{0,12}(?:支付|付款|消费|扣款|支出|实付)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MONEY_SYMBOL = Pattern.compile(
            "(?:¥|￥|RMB|CNY)\\s*" + NUMBER,
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MONEY_YUAN = Pattern.compile(
            NUMBER + "\\s*元"
    );

    private static final Pattern ANY_NUMBER = Pattern.compile("(?<![\\d-])\\d{1,}(?:,\\d{3})*(?:\\.\\d{1,2})?(?![\\d-])");

    private static final Pattern LABELED_MONEY = Pattern.compile(
            "(?:实付|实际支付|支付金额|付款金额|消费金额|扣款金额|交易金额|金额)[^\\d]{0,10}(?:¥|￥|RMB|CNY)?\\s*" + NUMBER + "\\s*(?:元)?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MERCHANT = Pattern.compile(
            "(?:给|向|商户|收款方|交易对象|付款给)[:：\\s]*([^，。,.；;\\n]{2,30})"
    );
    private static final Pattern PAREN_MERCHANT = Pattern.compile("[（(]([^（）()]{2,60})[）)]");

    public static Detection parse(Context context, StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return null;
        if (context.getPackageName().equals(sbn.getPackageName())) return null;

        Notification n = sbn.getNotification();
        Bundle e = n.extras;
        if (e == null) return null;

        String title = cs(e.getCharSequence(Notification.EXTRA_TITLE));
        String text = cs(e.getCharSequence(Notification.EXTRA_TEXT));
        String big = cs(e.getCharSequence(Notification.EXTRA_BIG_TEXT));
        String sub = cs(e.getCharSequence(Notification.EXTRA_SUB_TEXT));
        String info = cs(e.getCharSequence(Notification.EXTRA_INFO_TEXT));

        // Banking apps commonly use EXTRA_TEXT_LINES for an InboxStyle
        // notification; EXTRA_TEXT alone may only contain a short summary.
        List<String> values = nonEmpty(title, text, big, sub, info);
        CharSequence[] lines = e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) {
            for (CharSequence line : lines) {
                String value = cs(line);
                if (!value.isEmpty() && !values.contains(value)) values.add(value);
            }
        }
        String merged = String.join("\n", values);
        if (merged.trim().isEmpty()) return null;

        LedgerStore store = new LedgerStore(context);
        if (!containsKeyword(merged, store.getDetectionKeywords())
                && !containsAny(merged, "动账", "收入", "退款", "消费", "扣款", "支出")) return null;

        Double amount = findAmount(merged);
        if (amount == null || amount <= 0 || amount > 100_000_000) return null;

        String sourceApp = appName(context, sbn.getPackageName());
        String merchant = findMerchant(merged);
        if (merchant.isEmpty()) {
            Matcher pm = PAREN_MERCHANT.matcher(merged);
            if (pm.find()) merchant = pm.group(1);
        }
        if (merchant.isEmpty()) {
            if (!title.isEmpty() && !looksLikeAmount(title) && !title.equals(sourceApp)) merchant = title;
            else merchant = sourceApp;
        }

        Detection d = new Detection();
        d.amount = amount;
        d.merchant = cleanupMerchant(merchant);
        d.rawText = merged.length() > 800 ? merged.substring(0, 800) : merged;
        d.sourcePackage = sbn.getPackageName();
        d.sourceApp = sourceApp;
        d.type = containsAny(merged, "收入", "退款", "入账", "到账") ? "income" : "expense";
        d.signature = sbn.getPackageName() + "|" + Math.round(amount * 100) + "|" +
                d.merchant.toLowerCase(Locale.ROOT) + "|" + merged.hashCode();
        return d;
    }

    private static Double findAmount(String text) {
        Matcher labeled = LABELED_MONEY.matcher(text);
        if (labeled.find()) return parseNumber(labeled.group(1));

        // Card/bank messages usually place the actual amount immediately
        // before “元”; prefer the last such value so card tails and dates are
        // never selected as the amount.
        Matcher yuan = MONEY_YUAN.matcher(text);
        Double lastYuan = null;
        while (yuan.find()) lastYuan = parseNumber(yuan.group(1));
        if (lastYuan != null && lastYuan > 0) return lastYuan;

        Matcher m = STRONG_PREFIX.matcher(text);
        if (m.find()) return parseNumber(m.group(1));

        m = STRONG_SUFFIX.matcher(text);
        if (m.find()) return parseNumber(m.group(1));

        m = MONEY_SYMBOL.matcher(text);
        if (m.find()) return parseNumber(m.group(1));

        // Fallback: score numeric candidates instead of taking the first number.
        double best = -1; int bestScore = Integer.MIN_VALUE;
        Matcher candidate = ANY_NUMBER.matcher(text);
        while (candidate.find()) {
            String raw = candidate.group();
            double value = parseNumber(raw);
            if (value <= 0 || value > 100_000_000) continue;
            int start = candidate.start(), end = candidate.end();
            String around = text.substring(Math.max(0, start - 18), Math.min(text.length(), end + 18));
            int score = 0;
            if (raw.contains(".")) score += 4;
            if (around.matches("(?s).*([¥￥元]|RMB|CNY).*")) score += 8;
            if (around.matches("(?s).*(实付|支付|付款|消费|扣款|支出|交易|金额).*")) score += 6;
            if (around.matches("(?s).*(余额|余额为|卡号|尾号|订单|流水号|验证码|时间|日期).*")) score -= 12;
            if (raw.replace(",", "").length() >= 6) score -= 15;
            if (raw.contains(",")) score += 2;
            if (value >= 1900 && value <= 2100) score -= 15;
            if (score > bestScore) { bestScore = score; best = value; }
        }
        return bestScore >= 4 ? best : null;
    }

    private static String findMerchant(String text) {
        Matcher m = MERCHANT.matcher(text);
        if (m.find()) return m.group(1);
        return "";
    }

    private static boolean containsKeyword(String text, String keywordCsv) {
        String[] keywords = keywordCsv.split("[,，\\n]");
        String haystack = text.toLowerCase(Locale.ROOT);
        for (String k : keywords) {
            String keyword = k.trim();
            if (!keyword.isEmpty() && haystack.contains(keyword.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static boolean containsAny(String text, String... words) {
        String value = text.toLowerCase(Locale.ROOT);
        for (String word : words) if (value.contains(word.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private static double parseNumber(String s) {
        try { return Double.parseDouble(s.replace(",", "")); }
        catch (Exception e) { return -1; }
    }

    private static String cleanupMerchant(String s) {
        String out = s == null ? "" : s.trim();
        out = out.replaceAll("(?:支付|付款|消费|扣款|成功|人民币|CNY|RMB).*", "").trim();
        if (out.length() > 40) out = out.substring(0, 40);
        return out;
    }

    private static boolean looksLikeAmount(String s) {
        return MONEY_SYMBOL.matcher(s).find() || MONEY_YUAN.matcher(s).find();
    }

    private static String appName(Context c, String packageName) {
        try {
            PackageManager pm = c.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    private static String cs(CharSequence c) {
        return c == null ? "" : c.toString().trim();
    }

    private static List<String> nonEmpty(String... values) {
        List<String> out = new ArrayList<>();
        for (String v : values) if (v != null && !v.trim().isEmpty()) out.add(v.trim());
        return out;
    }

    public static class Detection {
        public double amount;
        public String merchant = "";
        public String rawText = "";
        public String sourcePackage = "";
        public String sourceApp = "";
        public String type = "expense";
        public String signature = "";
    }
}
