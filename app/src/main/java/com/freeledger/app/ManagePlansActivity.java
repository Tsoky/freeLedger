package com.freeledger.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.view.ViewGroup;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ManagePlansActivity extends Activity {
    private LedgerStore store;
    private LinearLayout content;
    private String month;
    private final List<JSONObject> expenseCategories = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new LedgerStore(this);
        month = getIntent().getStringExtra("month");
        if (month == null || month.isEmpty()) month = YearMonth.now().toString();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        content = Ui.vertical(this);
        Ui.applySystemInsets(this, content, 18, 18, 26);
        scroll.addView(content);
        setContentView(scroll);
        render();
    }

    private void render() {
        content.removeAllViews();
        content.addView(Ui.title(this, month + " 月计划", 27));
        content.addView(Ui.text(this,
                "计划可以绑定你创建的分类，也可以完全不绑定。",
                12, Ui.MUTED));
        content.addView(Ui.spacer(this, 14));

        LinearLayout addCard = Ui.card(this);
        EditText name = Ui.input(this, "计划名称，例如：旅行 / 房租 / 订阅");
        EditText amount = Ui.input(this, "计划金额");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        Spinner category = new Spinner(this);
        category.setBackground(Ui.roundStroke(Ui.CARD, Ui.LINE, 1, 10, this));
        category.setPadding(Ui.dp(this, 8), Ui.dp(this, 5), Ui.dp(this, 8), Ui.dp(this, 5));

        expenseCategories.clear();
        List<String> catNames = new ArrayList<>();
        catNames.add("不限定分类");
        JSONArray cats = store.getCategories("expense");
        for (int i = 0; i < cats.length(); i++) {
            JSONObject c = cats.optJSONObject(i);
            if (c != null) {
                expenseCategories.add(c);
                catNames.add(c.optString("name"));
            }
        }
        category.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, catNames) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextSize(12);
                view.setTextColor(Ui.TEXT);
                view.setPadding(Ui.dp(ManagePlansActivity.this, 8), 0, Ui.dp(ManagePlansActivity.this, 8), 0);
                return view;
            }
        });

        Button add = Ui.darkButton(this, "添加计划");
        addCard.addView(name, Ui.match());
        addCard.addView(Ui.spacer(this, 8));
        addCard.addView(amount, Ui.match());
        addCard.addView(Ui.spacer(this, 8));
        LinearLayout.LayoutParams categoryParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 38));
        addCard.addView(category, categoryParams);
        addCard.addView(Ui.spacer(this, 10));
        addCard.addView(add, Ui.match());
        content.addView(addCard);

        add.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) {
                Toast.makeText(this, "请输入计划名称", Toast.LENGTH_SHORT).show();
                return;
            }
            double value;
            try { value = Double.parseDouble(amount.getText().toString().trim()); }
            catch (Exception e) { value = 0; }

            String categoryId = "";
            int p = category.getSelectedItemPosition();
            if (p > 0 && p - 1 < expenseCategories.size()) {
                categoryId = expenseCategories.get(p - 1).optString("id");
            }
            store.addPlan(month, n, value, categoryId);
            render();
        });

        content.addView(Ui.spacer(this, 12));
        LinearLayout plansCard = Ui.card(this);
        plansCard.addView(Ui.title(this, "计划与实际", 16));

        JSONArray plans = store.getPlans(month);
        if (plans.length() == 0) {
            TextView e = Ui.text(this, "这个月还没有计划。", 12, Ui.MUTED);
            e.setPadding(0, Ui.dp(this, 10), 0, 0);
            plansCard.addView(e);
        }

        for (int i = 0; i < plans.length(); i++) {
            JSONObject p = plans.optJSONObject(i);
            if (p == null) continue;

            double budget = p.optDouble("amount", 0);
            double spent = store.spentForPlan(month, p.optString("id"));
            double left = budget - spent;

            LinearLayout row = Ui.vertical(this);
            row.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 7));

            LinearLayout top = Ui.horizontal(this);
            top.addView(Ui.title(this, p.optString("name"), 14), Ui.weight(1));
            Button edit = compactAction("编辑");
            Button del = compactAction("删除");
            top.addView(edit);
            top.addView(new View(this), new LinearLayout.LayoutParams(Ui.dp(this, 6), 1));
            top.addView(del);
            row.addView(top);

            String categoryName = store.getCategoryName("expense", p.optString("categoryId"));
            String detail =
                    (!categoryName.isEmpty() ? "分类：" + categoryName + " · " : "") +
                    "计划 " + money(budget) + " · 已花 " + money(spent) +
                    " · " + (left >= 0 ? "剩余 " : "超支 ") + money(Math.abs(left));
            row.addView(Ui.text(this, detail, 11, left >= 0 ? Ui.MUTED : Ui.DANGER));

            plansCard.addView(row);

            String id = p.optString("id");
            edit.setOnClickListener(v -> showEditDialog(p));
            del.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("删除计划？")
                    .setMessage("已记流水会保留，只会取消与这个计划的关联。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除", (d, w) -> {
                        store.deletePlan(month, id);
                        render();
                    })
                    .show());
        }
        content.addView(plansCard);
    }

    private void showEditDialog(JSONObject plan) {
        LinearLayout form = Ui.vertical(this);
        form.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), 0);
        EditText name = Ui.input(this, "计划名称"); name.setText(plan.optString("name"));
        EditText amount = Ui.input(this, "预算金额"); amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL); amount.setText(String.valueOf(plan.optDouble("amount")));
        form.addView(name, Ui.match()); form.addView(Ui.spacer(this, 8)); form.addView(amount, Ui.match());
        new AlertDialog.Builder(this).setTitle("编辑计划").setView(form).setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> { try { store.updatePlan(month, plan.optString("id"), name.getText().toString().trim(), Double.parseDouble(amount.getText().toString().trim()), plan.optString("categoryId")); render(); } catch (Exception e) { Toast.makeText(this, "请输入有效金额", Toast.LENGTH_SHORT).show(); } }).show();
    }

    private Button compactAction(String label) {
        Button b = Ui.button(this, label);
        b.setTextSize(11);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 8), Ui.dp(this, 2));
        return b;
    }

    private static String money(double v) {
        return String.format(Locale.CHINA, "¥%.2f", v);
    }
}
