package com.freeledger.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private LedgerStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new LedgerStore(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout content = Ui.vertical(this);
        Ui.applySystemInsets(this, content, 18, 18, 26);
        scroll.addView(content);
        setContentView(scroll);

        content.addView(Ui.title(this, "支付检测设置", 27));
        content.addView(Ui.text(this,
                "检测只在本机进行，不上传通知内容，也没有 INTERNET 权限。",
                12, Ui.MUTED));
        content.addView(Ui.spacer(this, 14));

        LinearLayout card = Ui.card(this);
        card.addView(Ui.title(this, "检测关键词", 16));
        TextView hint = Ui.text(this,
                "通知包含支付相关动作和可识别金额时，会提醒你确认；卡号、订单号、日期等数字会自动排除。",
                11, Ui.MUTED);
        hint.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 9));
        card.addView(hint);

        EditText keywords = Ui.input(this, "支付,付款,消费,扣款...");
        keywords.setText(store.getDetectionKeywords());
        card.addView(keywords, Ui.match());
        card.addView(Ui.spacer(this, 9));

        Button save = Ui.darkButton(this, "保存关键词");
        card.addView(save, Ui.match());
        save.setOnClickListener(v -> {
            store.setDetectionKeywords(keywords.getText().toString());
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        });

        content.addView(card);
        content.addView(Ui.spacer(this, 12));

        LinearLayout actions = Ui.card(this);
        Button access = Ui.button(this, "打开通知访问设置");
        Button test = Ui.button(this, "发送一条测试支付检测");
        actions.addView(access, Ui.match());
        actions.addView(Ui.spacer(this, 8));
        actions.addView(test, Ui.match());
        content.addView(actions);

        access.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        test.setOnClickListener(v -> {
            PaymentParser.Detection d = new PaymentParser.Detection();
            d.amount = 38.50;
            d.merchant = "测试商户";
            d.rawText = "支付成功 ￥38.50 给 测试商户";
            d.sourcePackage = getPackageName();
            d.sourceApp = "自由账本测试";
            d.signature = "test-" + System.currentTimeMillis();
            DetectionNotifier.show(this, d);
            Toast.makeText(this, "测试通知已发送", Toast.LENGTH_SHORT).show();
        });

        content.addView(Ui.spacer(this, 12));
        LinearLayout note = Ui.card(this);
        note.addView(Ui.title(this, "检测边界", 16));
        note.addView(Ui.text(this,
                "• 支付 App / 银行必须真的发出一条系统通知。\n" +
                "• 通知里必须包含可读取的金额文字。\n" +
                "• 如果对方只在 App 内展示“支付成功”，系统没有通知，就无法自动识别。\n" +
                "• 本 App 不使用无障碍服务抓屏，也不读取密码、验证码或银行卡内容。",
                11, Ui.MUTED));
        content.addView(note);
    }
}
