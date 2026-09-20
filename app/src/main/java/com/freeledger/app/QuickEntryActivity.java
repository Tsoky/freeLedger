package com.freeledger.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class QuickEntryActivity extends Activity {
    private LedgerStore store;
    private LinearLayout content;

    private Spinner typeSpinner;
    private Spinner categorySpinner;
    private Spinner accountSpinner;
    private Spinner planSpinner;

    private EditText amountInput;
    private EditText merchantInput;
    private EditText noteInput;
    private EditText dateInput;

    private final List<JSONObject> categoryObjects = new ArrayList<>();
    private final List<JSONObject> accountObjects = new ArrayList<>();
    private final List<JSONObject> planObjects = new ArrayList<>();

    private boolean detected;
    private String sourcePackage = "";
    private String sourceApp = "";
    private int sourceNotificationId = -1;
    private String preferredCategoryId = "";
    private String preferredAccountId = "";
    private boolean initialSuggestionsApplied = false;
    private String editId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new LedgerStore(this);

        detected = getIntent().getBooleanExtra("detected", false);
        sourcePackage = getIntent().getStringExtra("sourcePackage");
        sourceApp = getIntent().getStringExtra("sourceApp");
        sourceNotificationId = getIntent().getIntExtra(
                DetectionNotifier.EXTRA_NOTIFICATION_ID, -1);
        editId = safe(getIntent().getStringExtra("edit_id"));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        content = Ui.vertical(this);
        Ui.applySystemInsets(this, content, 18, 18, 26);
        scroll.addView(content);
        setContentView(scroll);

        renderForm();

        // When entering from a selected month, default the transaction date to
        // that month so the newly saved transaction appears immediately in the
        // month currently shown by MainActivity. Previously this was always
        // today's date, which made entries seem to disappear when viewing a
        // different month.
        String presetDate = getIntent().getStringExtra("date");
        String presetMonth = getIntent().getStringExtra("month");
        if (presetDate != null && presetDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            try {
                dateInput.setText(LocalDate.parse(presetDate).toString());
            } catch (Exception ignored) { }
        } else if (presetMonth != null && presetMonth.matches("\\d{4}-\\d{2}")) {
            try {
                dateInput.setText(java.time.YearMonth.parse(presetMonth).atDay(1).toString());
            } catch (Exception ignored) { }
        }

        String presetType = getIntent().getStringExtra("type");
        if ("income".equals(presetType) && typeSpinner != null) typeSpinner.setSelection(1);

        double amount = getIntent().getDoubleExtra("amount", 0);
        String merchant = safe(getIntent().getStringExtra("merchant"));
        if (amount > 0) amountInput.setText(String.format(Locale.US, "%.2f", amount));
        if (!merchant.isEmpty()) merchantInput.setText(merchant);
        if (!editId.isEmpty()) noteInput.setText(safe(getIntent().getStringExtra("note")));
        String editCategory = safe(getIntent().getStringExtra("category_id"));
        String editAccount = safe(getIntent().getStringExtra("account_id"));
        if (!editCategory.isEmpty()) preferredCategoryId = editCategory;
        if (!editAccount.isEmpty()) preferredAccountId = editAccount;

        if (detected && !merchant.isEmpty()) {
            JSONObject rule = store.getMerchantRule(merchant);
            if (rule != null) {
                preferredCategoryId = rule.optString("categoryId");
                preferredAccountId = rule.optString("accountId");
            }
        }

        reloadChoices();
        if (!editId.isEmpty()) {
            String editPlan = safe(getIntent().getStringExtra("plan_id"));
            if (!editPlan.isEmpty()) selectObjectById(planSpinner, planObjects, editPlan);
        }
        amountInput.requestFocus();
    }

    private void renderForm() {
        TextView title = Ui.title(this, !editId.isEmpty() ? "编辑流水" : (detected ? "确认这笔消费" : "记一笔"), 27);
        content.addView(title);
        content.addView(Ui.text(this,
                detected
                        ? "金额已经从支付通知中预填；确认分类 / 计划后保存即可。"
                        : "分类、账户和计划都由你自己定义。",
                12, Ui.MUTED));

        if (detected && !sourceApp.isEmpty()) {
            TextView source = Ui.text(this, "检测来源：" + sourceApp, 10, Ui.ACCENT);
            source.setPadding(0, Ui.dp(this, 5), 0, 0);
            content.addView(source);
        }

        content.addView(Ui.spacer(this, 16));

        LinearLayout card = Ui.card(this);
        content.addView(card);

        card.addView(label("类型"));
        typeSpinner = spinner();
        typeSpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"支出", "收入"}
        ));
        card.addView(typeSpinner, Ui.match());

        card.addView(Ui.spacer(this, 10));
        card.addView(label("金额"));
        amountInput = Ui.input(this, "0.00");
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER |
                InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amountInput.setTextSize(25);
        amountInput.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(amountInput, Ui.match());

        merchantInput = Ui.input(this, "例如：咖啡店、房东、某个平台");
        merchantInput.setVisibility(View.GONE);

        card.addView(Ui.spacer(this, 10));
        card.addView(label("分类"));
        LinearLayout catRow = Ui.horizontal(this);
        categorySpinner = spinner();
        Button addCat = Ui.button(this, "＋ 新建");
        catRow.addView(categorySpinner, Ui.weight(1));
        catRow.addView(space8(), new LinearLayout.LayoutParams(Ui.dp(this, 8), 1));
        catRow.addView(addCat);
        card.addView(catRow);

        LinearLayout accountRow = Ui.horizontal(this);
        accountSpinner = spinner();
        Button addAccount = Ui.button(this, "＋ 新建");
        accountRow.addView(accountSpinner, Ui.weight(1));
        accountRow.addView(space8(), new LinearLayout.LayoutParams(Ui.dp(this, 8), 1));
        accountRow.addView(addAccount);
        accountRow.setVisibility(View.GONE);

        card.addView(Ui.spacer(this, 10));
        card.addView(label("关联月计划（可选）"));
        planSpinner = spinner();
        card.addView(planSpinner, Ui.match());
        TextView planHint = Ui.text(this,
                "关联后，这笔支出会自动计入该计划的“已花”金额。",
                10, Ui.ACCENT);
        planHint.setPadding(0, Ui.dp(this, 4), 0, 0);
        card.addView(planHint);

        card.addView(Ui.spacer(this, 10));
        card.addView(label("日期"));
        LinearLayout dateRow = Ui.horizontal(this);
        dateInput = Ui.input(this, "YYYY-MM-DD");
        dateInput.setText(LocalDate.now().toString());
        dateInput.setFocusable(false);
        dateInput.setClickable(true);
        Button datePicker = Ui.textButton(this, "▣", 22, Ui.ACCENT);
        datePicker.setContentDescription("选择日期");
        dateRow.addView(dateInput, Ui.weight(1));
        dateRow.addView(datePicker, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 48)));
        card.addView(dateRow);
        View.OnClickListener pickDate = v -> {
            LocalDate current;
            try { current = LocalDate.parse(dateInput.getText().toString()); } catch (Exception e) { current = LocalDate.now(); }
            new DatePickerDialog(this, (view, year, month, day) -> dateInput.setText(LocalDate.of(year, month + 1, day).toString()), current.getYear(), current.getMonthValue() - 1, current.getDayOfMonth()).show();
        };
        dateInput.setOnClickListener(pickDate);
        datePicker.setOnClickListener(pickDate);

        card.addView(Ui.spacer(this, 10));
        card.addView(label("备注（可选）"));
        noteInput = Ui.input(this, "补充说明");
        card.addView(noteInput, Ui.match());

        card.addView(Ui.spacer(this, 14));
        Button save = Ui.darkButton(this, "保存这笔账");
        card.addView(save, Ui.match());

        addCat.setOnClickListener(v -> createCategoryDialog());
        addAccount.setOnClickListener(v -> createAccountDialog());
        save.setOnClickListener(v -> saveEntry());

        typeSpinner.setOnItemSelectedListener(new SimpleItemListener(position -> {
            initialSuggestionsApplied = false;
            reloadChoices();
        }));

        categorySpinner.setOnItemSelectedListener(new SimpleItemListener(position -> {
            if (typeSpinner.getSelectedItemPosition() == 0) suggestPlanForSelectedCategory();
        }));
    }

    private void reloadChoices() {
        String type = currentType();
        categoryObjects.clear();

        List<String> categoryNames = new ArrayList<>();
        categoryNames.add("未分类");
        JSONArray cats = store.getCategories(type);
        for (int i = 0; i < cats.length(); i++) {
            JSONObject c = cats.optJSONObject(i);
            if (c != null) {
                categoryObjects.add(c);
                categoryNames.add(c.optString("name"));
            }
        }
        categorySpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, categoryNames));

        accountObjects.clear();
        List<String> accountNames = new ArrayList<>();
        accountNames.add("未指定账户");
        JSONArray accounts = store.getAccounts();
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject a = accounts.optJSONObject(i);
            if (a != null) {
                accountObjects.add(a);
                accountNames.add(a.optString("name"));
            }
        }
        accountSpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, accountNames));

        reloadPlans();

        if (!initialSuggestionsApplied) {
            selectObjectById(categorySpinner, categoryObjects, preferredCategoryId);
            selectObjectById(accountSpinner, accountObjects, preferredAccountId);
            initialSuggestionsApplied = true;
            suggestPlanForSelectedCategory();
        }
    }

    private void reloadPlans() {
        planObjects.clear();
        List<String> names = new ArrayList<>();
        names.add("不关联计划");

        if ("expense".equals(currentType())) {
            String month = currentMonth();
            JSONArray plans = store.getPlans(month);
            for (int i = 0; i < plans.length(); i++) {
                JSONObject p = plans.optJSONObject(i);
                if (p == null) continue;
                planObjects.add(p);
                double budget = p.optDouble("amount", 0);
                double spent = store.spentForPlan(month, p.optString("id"));
                double left = budget - spent;
                names.add(p.optString("name") + " · " +
                        (left >= 0 ? "剩余 " : "超支 ") + money(Math.abs(left)));
            }
        }
        planSpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, names));
        planSpinner.setEnabled("expense".equals(currentType()));
    }

    private void suggestPlanForSelectedCategory() {
        if (!"expense".equals(currentType())) return;
        String categoryId = selectedId(categorySpinner, categoryObjects);
        String suggested = store.suggestPlan(currentMonth(), categoryId);
        if (!suggested.isEmpty()) selectObjectById(planSpinner, planObjects, suggested);
    }

    private void createCategoryDialog() {
        EditText input = Ui.input(this, "分类名称");
        new AlertDialog.Builder(this)
                .setTitle("新建" + ("expense".equals(currentType()) ? "支出" : "收入") + "分类")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    String id = store.addCategory(currentType(), input.getText().toString());
                    preferredCategoryId = id;
                    initialSuggestionsApplied = false;
                    reloadChoices();
                })
                .show();
    }

    private void createAccountDialog() {
        EditText input = Ui.input(this, "账户名称");
        new AlertDialog.Builder(this)
                .setTitle("新建账户")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    String id = store.addAccount(input.getText().toString());
                    preferredAccountId = id;
                    initialSuggestionsApplied = false;
                    reloadChoices();
                })
                .show();
    }

    private void saveEntry() {
        double amount;
        try {
            amount = Double.parseDouble(amountInput.getText().toString().trim());
        } catch (Exception e) {
            Toast.makeText(this, "请输入正确金额", Toast.LENGTH_SHORT).show();
            return;
        }
        if (amount <= 0) {
            Toast.makeText(this, "金额必须大于 0", Toast.LENGTH_SHORT).show();
            return;
        }

        String date = dateInput.getText().toString().trim();
        try { LocalDate.parse(date); }
        catch (Exception e) {
            Toast.makeText(this, "日期格式应为 YYYY-MM-DD", Toast.LENGTH_SHORT).show();
            return;
        }

        String type = currentType();
        String categoryId = selectedId(categorySpinner, categoryObjects);
        String accountId = selectedId(accountSpinner, accountObjects);
        String planId = "expense".equals(type)
                ? selectedId(planSpinner, planObjects)
                : "";
        if (!planId.isEmpty()) {
            for (JSONObject plan : planObjects) {
                if (planId.equals(plan.optString("id"))) {
                    String boundCategory = plan.optString("categoryId");
                    if (!boundCategory.isEmpty() && !boundCategory.equals(categoryId)) {
                        Toast.makeText(this, "所选计划与支出分类不一致", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    break;
                }
            }
        }

        String categoryName = store.getCategoryName(type, categoryId);
        String accountName = store.getAccountName(accountId);
        String merchant = merchantInput.getText().toString().trim();
        String note = noteInput.getText().toString().trim();

        if (!editId.isEmpty()) {
            store.updateTransactionDetails(editId, amount, categoryId, categoryName, planId, note, date);
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        store.addTransaction(LedgerStore.makeTransaction(
                type,
                amount,
                categoryId,
                categoryName,
                accountId,
                accountName,
                planId,
                merchant,
                note,
                date,
                sourcePackage
        ));

        if ("expense".equals(type) && !merchant.isEmpty()
                && (!categoryId.isEmpty() || !accountId.isEmpty())) {
            store.saveMerchantRule(merchant, categoryId, accountId);
        }

        if (sourceNotificationId >= 0) {
            DetectionNotifier.cancel(this, sourceNotificationId);
        }

        Toast.makeText(this, "已记账", Toast.LENGTH_SHORT).show();
        finish();
    }

    private String currentType() {
        return typeSpinner != null && typeSpinner.getSelectedItemPosition() == 1
                ? "income" : "expense";
    }

    private String currentMonth() {
        String date = dateInput == null ? LocalDate.now().toString()
                : dateInput.getText().toString().trim();
        if (date.matches("\\d{4}-\\d{2}-\\d{2}")) return date.substring(0, 7);
        return LocalDate.now().toString().substring(0, 7);
    }

    private TextView label(String text) {
        TextView t = Ui.text(this, text, 11, Ui.MUTED);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Spinner spinner() {
        Spinner s = new Spinner(this);
        s.setBackground(Ui.roundStroke(Ui.CARD, Ui.LINE, 1, 10, this));
        s.setPadding(Ui.dp(this, 8), Ui.dp(this, 5), Ui.dp(this, 8), Ui.dp(this, 5));
        return s;
    }

    private View space8() { return new View(this); }

    private static String selectedId(Spinner spinner, List<JSONObject> objects) {
        int p = spinner.getSelectedItemPosition();
        if (p <= 0 || p - 1 >= objects.size()) return "";
        return objects.get(p - 1).optString("id");
    }

    private static void selectObjectById(Spinner spinner, List<JSONObject> objects, String id) {
        if (id == null || id.isEmpty()) return;
        for (int i = 0; i < objects.size(); i++) {
            if (id.equals(objects.get(i).optString("id"))) {
                spinner.setSelection(i + 1);
                return;
            }
        }
    }

    private static String money(double v) {
        return String.format(Locale.CHINA, "¥%.2f", v);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
