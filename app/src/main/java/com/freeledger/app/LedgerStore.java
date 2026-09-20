package com.freeledger.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class LedgerStore {
    private static final String PREF = "free_ledger_store";
    private static final String KEY_STATE = "state_json";
    private static final String DEFAULT_KEYWORDS =
            "支付,付款,消费,扣款,交易,支出,实付,成功支付,支付成功,消费成功";

    private final SharedPreferences prefs;

    public LedgerStore(Context context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private synchronized JSONObject load() {
        String raw = prefs.getString(KEY_STATE, null);
        if (raw != null) {
            try {
                return ensure(new JSONObject(raw));
            } catch (Exception ignored) {}
        }
        JSONObject fresh = blank();
        save(fresh);
        return fresh;
    }

    private JSONObject blank() {
        JSONObject s = new JSONObject();
        try {
            JSONObject categories = new JSONObject();
            categories.put("expense", new JSONArray());
            categories.put("income", new JSONArray());
            s.put("categories", categories);
            s.put("accounts", new JSONArray());
            s.put("transactions", new JSONArray());
            s.put("plans", new JSONObject());
            s.put("merchantRules", new JSONObject());
            JSONObject settings = new JSONObject();
            settings.put("keywords", DEFAULT_KEYWORDS);
            settings.put("recentDetections", new JSONObject());
            s.put("settings", settings);
        } catch (JSONException ignored) {}
        return s;
    }

    private JSONObject ensure(JSONObject s) {
        try {
            if (!s.has("categories") || !(s.opt("categories") instanceof JSONObject)) {
                s.put("categories", new JSONObject());
            }
            JSONObject categories = s.getJSONObject("categories");
            if (!categories.has("expense")) categories.put("expense", new JSONArray());
            if (!categories.has("income")) categories.put("income", new JSONArray());
            if (!s.has("accounts")) s.put("accounts", new JSONArray());
            if (!s.has("transactions")) s.put("transactions", new JSONArray());
            if (!s.has("plans")) s.put("plans", new JSONObject());
            if (!s.has("merchantRules")) s.put("merchantRules", new JSONObject());
            if (!s.has("settings")) s.put("settings", new JSONObject());
            JSONObject settings = s.getJSONObject("settings");
            if (!settings.has("keywords")) settings.put("keywords", DEFAULT_KEYWORDS);
            if (!settings.has("recentDetections")) settings.put("recentDetections", new JSONObject());
        } catch (JSONException ignored) {}
        return s;
    }

    private synchronized void save(JSONObject s) {
        prefs.edit().putString(KEY_STATE, s.toString()).apply();
    }

    public synchronized JSONArray getCategories(String type) {
        JSONObject s = load();
        return cloneArray(s.optJSONObject("categories").optJSONArray(type));
    }

    public synchronized String addCategory(String type, String name) {
        name = safe(name);
        if (name.isEmpty()) return "";
        JSONObject s = load();
        JSONArray arr = s.optJSONObject("categories").optJSONArray(type);
        for (int i = 0; i < arr.length(); i++) {
            if (name.equalsIgnoreCase(arr.optJSONObject(i).optString("name"))) {
                return arr.optJSONObject(i).optString("id");
            }
        }
        String id = UUID.randomUUID().toString();
        JSONObject c = new JSONObject();
        try {
            c.put("id", id);
            c.put("name", name);
            arr.put(c);
        } catch (JSONException ignored) {}
        save(s);
        return id;
    }

    public synchronized void deleteCategory(String type, String id) {
        JSONObject s = load();
        JSONArray arr = s.optJSONObject("categories").optJSONArray(type);
        String oldName = findName(arr, id);
        JSONArray next = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.optJSONObject(i);
            if (c != null && !id.equals(c.optString("id"))) next.put(c);
        }
        try {
            s.optJSONObject("categories").put(type, next);

            JSONArray txs = s.optJSONArray("transactions");
            for (int i = 0; i < txs.length(); i++) {
                JSONObject tx = txs.optJSONObject(i);
                if (tx != null && type.equals(tx.optString("type"))
                        && id.equals(tx.optString("categoryId"))) {
                    tx.put("categoryName", oldName);
                    tx.put("categoryId", "");
                }
            }

            JSONObject months = s.optJSONObject("plans");
            JSONArray names = months.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    JSONArray plans = months.optJSONArray(names.optString(i));
                    if (plans == null) continue;
                    for (int j = 0; j < plans.length(); j++) {
                        JSONObject p = plans.optJSONObject(j);
                        if (p != null && id.equals(p.optString("categoryId"))) {
                            p.put("categoryId", "");
                        }
                    }
                }
            }
        } catch (JSONException ignored) {}
        save(s);
    }

    public synchronized JSONArray getAccounts() {
        return cloneArray(load().optJSONArray("accounts"));
    }

    public synchronized String addAccount(String name) {
        name = safe(name);
        if (name.isEmpty()) return "";
        JSONObject s = load();
        JSONArray arr = s.optJSONArray("accounts");
        for (int i = 0; i < arr.length(); i++) {
            if (name.equalsIgnoreCase(arr.optJSONObject(i).optString("name"))) {
                return arr.optJSONObject(i).optString("id");
            }
        }
        String id = UUID.randomUUID().toString();
        JSONObject a = new JSONObject();
        try {
            a.put("id", id);
            a.put("name", name);
            arr.put(a);
        } catch (JSONException ignored) {}
        save(s);
        return id;
    }

    public synchronized void deleteAccount(String id) {
        JSONObject s = load();
        JSONArray arr = s.optJSONArray("accounts");
        String oldName = findName(arr, id);
        JSONArray next = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject a = arr.optJSONObject(i);
            if (a != null && !id.equals(a.optString("id"))) next.put(a);
        }
        try {
            s.put("accounts", next);
            JSONArray txs = s.optJSONArray("transactions");
            for (int i = 0; i < txs.length(); i++) {
                JSONObject tx = txs.optJSONObject(i);
                if (tx != null && id.equals(tx.optString("accountId"))) {
                    tx.put("accountName", oldName);
                    tx.put("accountId", "");
                }
            }
        } catch (JSONException ignored) {}
        save(s);
    }

    public synchronized JSONArray getPlans(String month) {
        JSONObject s = load();
        JSONObject months = s.optJSONObject("plans");
        JSONArray arr = months.optJSONArray(month);
        return cloneArray(arr == null ? new JSONArray() : arr);
    }

    public synchronized String addPlan(String month, String name, double amount, String categoryId) {
        JSONObject s = load();
        JSONObject months = s.optJSONObject("plans");
        JSONArray arr = months.optJSONArray(month);
        if (arr == null) {
            arr = new JSONArray();
            try { months.put(month, arr); } catch (JSONException ignored) {}
        }
        String id = UUID.randomUUID().toString();
        JSONObject p = new JSONObject();
        try {
            p.put("id", id);
            p.put("name", safe(name));
            p.put("amount", Math.max(0, amount));
            p.put("categoryId", safe(categoryId));
            arr.put(p);
        } catch (JSONException ignored) {}
        save(s);
        return id;
    }

    public synchronized void deletePlan(String month, String planId) {
        JSONObject s = load();
        JSONObject months = s.optJSONObject("plans");
        JSONArray arr = months.optJSONArray(month);
        if (arr == null) return;
        JSONArray next = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject p = arr.optJSONObject(i);
            if (p != null && !planId.equals(p.optString("id"))) next.put(p);
        }
        try {
            months.put(month, next);
            JSONArray txs = s.optJSONArray("transactions");
            for (int i = 0; i < txs.length(); i++) {
                JSONObject tx = txs.optJSONObject(i);
                if (tx != null && planId.equals(tx.optString("planId"))) {
                    tx.put("planId", "");
                }
            }
        } catch (JSONException ignored) {}
        save(s);
    }

    public synchronized void updatePlan(String month, String planId, String name, double amount, String categoryId) {
        JSONObject s = load(); JSONArray arr = s.optJSONObject("plans").optJSONArray(month);
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject p = arr.optJSONObject(i);
            if (p != null && planId.equals(p.optString("id"))) {
                try { p.put("name", safe(name)); p.put("amount", Math.max(0, amount)); p.put("categoryId", safe(categoryId)); } catch (JSONException ignored) {}
                break;
            }
        }
        save(s);
    }

    public synchronized double spentForPlan(String month, String planId) {
        JSONArray txs = getMonthTransactions(month);
        JSONArray plans = getPlans(month);
        double total = 0;
        for (int i = 0; i < txs.length(); i++) {
            JSONObject tx = txs.optJSONObject(i);
            if (tx != null && "expense".equals(tx.optString("type"))
                    && planId.equals(matchingPlanId(tx, plans))) {
                total += tx.optDouble("amount", 0);
            }
        }
        return total;
    }

    public synchronized double getMonthBudgetExpense(String month) {
        JSONArray txs = getMonthTransactions(month);
        JSONArray plans = getPlans(month);
        double total = 0;
        for (int i = 0; i < txs.length(); i++) {
            JSONObject tx = txs.optJSONObject(i);
            if (tx != null && "expense".equals(tx.optString("type"))
                    && !matchingPlanId(tx, plans).isEmpty()) total += tx.optDouble("amount", 0);
        }
        return total;
    }

    // Explicit associations take precedence; a category can automatically match
    // only when exactly one plan uses it, so a transaction is never counted twice.
    private static String matchingPlanId(JSONObject tx, JSONArray plans) {
        String assigned = tx.optString("planId");
        String category = tx.optString("categoryId");
        if (!assigned.isEmpty()) {
            for (int i = 0; i < plans.length(); i++) {
                JSONObject plan = plans.optJSONObject(i);
                if (plan != null && assigned.equals(plan.optString("id"))) {
                    String bound = plan.optString("categoryId");
                    return bound.isEmpty() || bound.equals(category) ? assigned : "";
                }
            }
            return "";
        }
        if (category.isEmpty()) return "";
        String match = "";
        for (int i = 0; i < plans.length(); i++) {
            JSONObject plan = plans.optJSONObject(i);
            if (plan != null && category.equals(plan.optString("categoryId"))) {
                if (!match.isEmpty()) return "";
                match = plan.optString("id");
            }
        }
        return match;
    }

    public synchronized double getMonthPlannedAmount(String month) {
        double total = 0; JSONArray plans = getPlans(month);
        for (int i=0;i<plans.length();i++) total += plans.optJSONObject(i).optDouble("amount", 0);
        return total;
    }

    public synchronized double getMonthActualExpense(String month) {
        double total = 0; JSONArray txs = getMonthTransactions(month);
        for (int i=0;i<txs.length();i++) if ("expense".equals(txs.optJSONObject(i).optString("type"))) total += txs.optJSONObject(i).optDouble("amount", 0);
        return total;
    }

    public synchronized double getPlanUsageRate(String month) {
        double planned = getMonthPlannedAmount(month); return planned <= 0 ? 0 : getMonthBudgetExpense(month) * 100.0 / planned;
    }

    public synchronized double getRemainingBudget(String month) { return getMonthPlannedAmount(month) - getMonthBudgetExpense(month); }

    public synchronized String suggestPlan(String month, String categoryId) {
        if (safe(categoryId).isEmpty()) return "";
        JSONArray plans = getPlans(month);
        String candidate = "";
        int count = 0;
        for (int i = 0; i < plans.length(); i++) {
            JSONObject p = plans.optJSONObject(i);
            if (p != null && categoryId.equals(p.optString("categoryId"))) {
                double remaining = p.optDouble("amount", 0) - spentForPlan(month, p.optString("id"));
                if (remaining > 0) {
                    candidate = p.optString("id");
                    count++;
                }
            }
        }
        return count == 1 ? candidate : "";
    }

    public synchronized void addTransaction(JSONObject tx) {
        JSONObject s = load();
        s.optJSONArray("transactions").put(tx);
        save(s);
    }

    public synchronized void updateTransaction(String id, double amount, String merchant, String note, String date) {
        JSONObject s = load(); JSONArray arr = s.optJSONArray("transactions");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject tx = arr.optJSONObject(i);
            if (tx != null && id.equals(tx.optString("id"))) {
                try { tx.put("amount", Math.max(0, amount)); tx.put("merchant", safe(merchant)); tx.put("note", safe(note)); tx.put("date", safe(date)); } catch (JSONException ignored) {}
                break;
            }
        }
        save(s);
    }

    public synchronized void updateTransactionDetails(String id, double amount, String categoryId,
                                                       String categoryName, String planId,
                                                       String note, String date) {
        JSONObject s = load(); JSONArray arr = s.optJSONArray("transactions");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject tx = arr.optJSONObject(i);
            if (tx != null && id.equals(tx.optString("id"))) {
                try { tx.put("amount", Math.max(0, amount)); tx.put("categoryId", safe(categoryId)); tx.put("categoryName", safe(categoryName)); tx.put("planId", safe(planId)); tx.put("note", safe(note)); tx.put("date", safe(date)); tx.put("merchant", ""); tx.put("accountId", ""); tx.put("accountName", ""); } catch (JSONException ignored) {}
                break;
            }
        }
        save(s);
    }

    public synchronized void deleteTransaction(String id) {
        JSONObject s = load(); JSONArray arr = s.optJSONArray("transactions"); JSONArray next = new JSONArray();
        for (int i = 0; i < arr.length(); i++) { JSONObject tx = arr.optJSONObject(i); if (tx != null && !id.equals(tx.optString("id"))) next.put(tx); }
        try { s.put("transactions", next); } catch (JSONException ignored) {}
        save(s);
    }

    public synchronized JSONArray getMonthTransactions(String month) {
        JSONArray all = load().optJSONArray("transactions");
        JSONArray out = new JSONArray();
        for (int i = 0; i < all.length(); i++) {
            JSONObject tx = all.optJSONObject(i);
            if (tx != null && tx.optString("date").startsWith(month)) out.put(tx);
        }
        return out;
    }

    public synchronized JSONArray getDayTransactions(String date) {
        JSONArray all = load().optJSONArray("transactions");
        JSONArray out = new JSONArray();
        for (int i = 0; i < all.length(); i++) {
            JSONObject tx = all.optJSONObject(i);
            if (tx != null && date.equals(tx.optString("date"))) out.put(tx);
        }
        return out;
    }

    public synchronized JSONArray getAllTransactions() {
        return cloneArray(load().optJSONArray("transactions"));
    }

    public synchronized String getCategoryName(String type, String id) {
        if (safe(id).isEmpty()) return "";
        return findName(getCategories(type), id);
    }

    public synchronized String getAccountName(String id) {
        if (safe(id).isEmpty()) return "";
        return findName(getAccounts(), id);
    }

    public synchronized String getPlanName(String month, String id) {
        if (safe(id).isEmpty()) return "";
        return findName(getPlans(month), id);
    }

    public synchronized String getDetectionKeywords() {
        return load().optJSONObject("settings").optString("keywords", DEFAULT_KEYWORDS);
    }

    public synchronized void setDetectionKeywords(String keywords) {
        JSONObject s = load();
        try {
            s.optJSONObject("settings").put("keywords",
                    safe(keywords).isEmpty() ? DEFAULT_KEYWORDS : keywords.trim());
        } catch (JSONException ignored) {}
        save(s);
    }

    public synchronized boolean markDetectionIfNew(String signature, long windowMs) {
        JSONObject s = load();
        JSONObject recent = s.optJSONObject("settings").optJSONObject("recentDetections");
        long now = System.currentTimeMillis();
        long previous = recent.optLong(signature, 0);
        JSONArray keys = recent.names();
        if (keys != null) {
            for (int i = 0; i < keys.length(); i++) {
                String k = keys.optString(i);
                if (now - recent.optLong(k, 0) > 10 * 60_000L) recent.remove(k);
            }
        }
        if (previous > 0 && now - previous < windowMs) {
            save(s);
            return false;
        }
        try { recent.put(signature, now); } catch (JSONException ignored) {}
        save(s);
        return true;
    }

    public synchronized JSONObject getMerchantRule(String merchant) {
        String key = merchantKey(merchant);
        if (key.isEmpty()) return null;
        JSONObject r = load().optJSONObject("merchantRules").optJSONObject(key);
        return r == null ? null : cloneObject(r);
    }

    public synchronized void saveMerchantRule(String merchant, String categoryId, String accountId) {
        String key = merchantKey(merchant);
        if (key.isEmpty()) return;
        JSONObject s = load();
        JSONObject rule = new JSONObject();
        try {
            rule.put("categoryId", safe(categoryId));
            rule.put("accountId", safe(accountId));
            s.optJSONObject("merchantRules").put(key, rule);
        } catch (JSONException ignored) {}
        save(s);
    }

    public synchronized String exportJson() {
        JSONObject out = new JSONObject();
        try {
            out.put("format", "free-ledger-android");
            out.put("version", 1);
            out.put("exportedAt", OffsetDateTime.now().toString());
            out.put("data", load());
        } catch (JSONException ignored) {}
        try {
            return out.toString(2);
        } catch (Exception e) {
            return out.toString();
        }
    }

    public synchronized boolean importJson(String raw) {
        try {
            JSONObject root = new JSONObject(raw);
            JSONObject data = root.optJSONObject("data");
            if (data == null) data = root;

            JSONObject clean = blank();

            JSONObject inCategories = data.optJSONObject("categories");
            if (inCategories != null) {
                JSONObject outCategories = clean.optJSONObject("categories");
                outCategories.put("expense", cloneArray(inCategories.optJSONArray("expense")));
                outCategories.put("income", cloneArray(inCategories.optJSONArray("income")));
            }

            clean.put("accounts", cloneArray(data.optJSONArray("accounts")));
            clean.put("transactions", cloneArray(data.optJSONArray("transactions")));

            JSONObject plans = data.optJSONObject("plans");
            if (plans != null) clean.put("plans", new JSONObject(plans.toString()));

            JSONObject merchantRules = data.optJSONObject("merchantRules");
            if (merchantRules != null) clean.put("merchantRules", new JSONObject(merchantRules.toString()));

            JSONObject settings = data.optJSONObject("settings");
            if (settings != null) {
                JSONObject outSettings = clean.optJSONObject("settings");
                if (settings.has("keywords")) {
                    outSettings.put("keywords", settings.optString("keywords", DEFAULT_KEYWORDS));
                }
            }

            save(ensure(clean));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static JSONObject makeTransaction(
            String type,
            double amount,
            String categoryId,
            String categoryName,
            String accountId,
            String accountName,
            String planId,
            String merchant,
            String note,
            String date,
            String sourcePackage
    ) {
        JSONObject tx = new JSONObject();
        try {
            tx.put("id", UUID.randomUUID().toString());
            tx.put("type", safe(type));
            tx.put("amount", amount);
            tx.put("categoryId", safe(categoryId));
            tx.put("categoryName", safe(categoryName));
            tx.put("accountId", safe(accountId));
            tx.put("accountName", safe(accountName));
            tx.put("planId", safe(planId));
            tx.put("merchant", safe(merchant));
            tx.put("note", safe(note));
            tx.put("date", safe(date).isEmpty() ? LocalDate.now().toString() : date);
            tx.put("sourcePackage", safe(sourcePackage));
            tx.put("createdAt", System.currentTimeMillis());
        } catch (JSONException ignored) {}
        return tx;
    }

    private static String merchantKey(String merchant) {
        return safe(merchant)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。！？、：；“”‘’（）【】]+", "");
    }

    private static String findName(JSONArray arr, String id) {
        if (arr == null) return "";
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return o.optString("name");
        }
        return "";
    }

    private static JSONArray cloneArray(JSONArray arr) {
        if (arr == null) return new JSONArray();
        try { return new JSONArray(arr.toString()); }
        catch (Exception e) { return new JSONArray(); }
    }

    private static JSONObject cloneObject(JSONObject o) {
        if (o == null) return null;
        try { return new JSONObject(o.toString()); }
        catch (Exception e) { return null; }
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
