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

public class ManageAccountsActivity extends Activity {
    private LedgerStore store;
    private LinearLayout content;

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
        content.addView(Ui.title(this, "自定义账户", 27));
        content.addView(Ui.text(this,
                "例如银行卡、微信、现金，也可以用任何你自己的名称。",
                12, Ui.MUTED));
        content.addView(Ui.spacer(this, 14));

        LinearLayout addCard = Ui.card(this);
        EditText input = Ui.input(this, "账户名称");
        Button add = Ui.darkButton(this, "添加");
        addCard.addView(input, Ui.match());
        addCard.addView(Ui.spacer(this, 8));
        addCard.addView(add, Ui.match());
        content.addView(addCard);

        add.setOnClickListener(v -> {
            String id = store.addAccount(input.getText().toString());
            if (id.isEmpty()) Toast.makeText(this, "请输入账户名称", Toast.LENGTH_SHORT).show();
            else render();
        });

        content.addView(Ui.spacer(this, 12));
        LinearLayout list = Ui.card(this);
        list.addView(Ui.title(this, "账户", 16));

        JSONArray arr = store.getAccounts();
        if (arr.length() == 0) {
            TextView e = Ui.text(this,
                    "目前为空。记账时也可以一直使用“未指定账户”。",
                    12, Ui.MUTED);
            e.setPadding(0, Ui.dp(this, 10), 0, 0);
            list.addView(e);
        }

        for (int i = 0; i < arr.length(); i++) {
            JSONObject a = arr.optJSONObject(i);
            if (a == null) continue;
            LinearLayout row = Ui.horizontal(this);
            row.setPadding(0, Ui.dp(this, 9), 0, 0);
            row.addView(Ui.text(this, a.optString("name"), 14, Ui.TEXT), Ui.weight(1));
            Button del = Ui.button(this, "删除");
            row.addView(del);
            list.addView(row);

            String id = a.optString("id");
            del.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("删除账户？")
                    .setMessage("历史流水会保留账户名称。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除", (d, w) -> {
                        store.deleteAccount(id);
                        render();
                    }).show());
        }
        content.addView(list);
    }
}
