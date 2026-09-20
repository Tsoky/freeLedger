package com.freeledger.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

public class ManageCategoriesActivity extends Activity {
    private LedgerStore store;
    private LinearLayout content;
    private String type = "expense";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new LedgerStore(this);

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
        content.addView(Ui.title(this, "自定义分类", 27));
        content.addView(Ui.text(this,
                "这里没有系统预设。你想怎么分，就怎么分。",
                12, Ui.MUTED));
        content.addView(Ui.spacer(this, 14));

        LinearLayout tabs = Ui.horizontal(this);
        Button expense = Ui.button(this, "支出分类");
        Button income = Ui.button(this, "收入分类");
        if ("expense".equals(type)) expense.setBackground(Ui.roundRect(Ui.ACCENT, 11, this));
        else income.setBackground(Ui.roundRect(Ui.ACCENT, 11, this));
        if ("expense".equals(type)) expense.setTextColor(android.graphics.Color.WHITE);
        else income.setTextColor(android.graphics.Color.WHITE);
        tabs.addView(expense, Ui.weight(1));
        tabs.addView(new View(this),
                new LinearLayout.LayoutParams(Ui.dp(this, 8), 1));
        tabs.addView(income, Ui.weight(1));
        content.addView(tabs);

        expense.setOnClickListener(v -> { type = "expense"; render(); });
        income.setOnClickListener(v -> { type = "income"; render(); });

        content.addView(Ui.spacer(this, 12));
        LinearLayout addCard = Ui.card(this);
        EditText input = Ui.input(this, "输入你自己的分类名称");
        Button add = Ui.darkButton(this, "添加");
        addCard.addView(input, Ui.match());
        addCard.addView(Ui.spacer(this, 8));
        addCard.addView(add, Ui.match());
        content.addView(addCard);

        add.setOnClickListener(v -> {
            String id = store.addCategory(type, input.getText().toString());
            if (id.isEmpty()) {
                Toast.makeText(this, "请输入分类名称", Toast.LENGTH_SHORT).show();
            } else {
                render();
            }
        });

        content.addView(Ui.spacer(this, 12));
        LinearLayout listCard = Ui.card(this);
        listCard.addView(Ui.title(this,
                "expense".equals(type) ? "支出分类" : "收入分类", 16));

        JSONArray arr = store.getCategories(type);
        if (arr.length() == 0) {
            TextView empty = Ui.text(this, "目前为空。", 12, Ui.MUTED);
            empty.setPadding(0, Ui.dp(this, 10), 0, 0);
            listCard.addView(empty);
        }

        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.optJSONObject(i);
            if (c == null) continue;
            LinearLayout row = Ui.horizontal(this);
            row.setPadding(0, Ui.dp(this, 9), 0, 0);
            TextView name = Ui.text(this, c.optString("name"), 14, Ui.TEXT);
            Button del = Ui.button(this, "删除");
            row.addView(name, Ui.weight(1));
            row.addView(del);
            listCard.addView(row);

            String id = c.optString("id");
            del.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("删除分类？")
                    .setMessage("历史流水会保留原分类文字，但不再关联这个分类。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除", (d, w) -> {
                        store.deleteCategory(type, id);
                        render();
                    })
                    .show());
        }
        content.addView(listCard);
    }
}
