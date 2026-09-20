package com.freeledger.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import android.widget.DatePicker;
import android.widget.EditText;
import android.text.InputType;
import android.app.DatePickerDialog;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_EXPORT = 401;
    private static final int REQ_IMPORT = 402;

    private LedgerStore store;
    private LinearLayout content;
    private YearMonth month = YearMonth.now();
    private int tab = 0;
    private int statsTab = 0;
    private LinearLayout bottomNav;
    private java.time.LocalDate day = java.time.LocalDate.now();
    private int trendScrollX = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new LedgerStore(this);

        LinearLayout root = Ui.vertical(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setScrollbarFadingEnabled(false);
        scroll.setBackgroundColor(Ui.BG);

        content = Ui.vertical(this);
        Ui.applySystemInsets(this, content, 18, 18, 28);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        bottomNav = makeBottomNav();
        root.addView(bottomNav);
        setContentView(root);

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 700);
        }

        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null) render();
    }

    private void render() {
        content.removeAllViews();
        refreshBottomNav();

        if (tab == 1) { renderPlansPage(); return; }
        if (tab == 2) { renderStatsPage(); return; }
        if (tab == 3) { renderSettingsPage(); return; }

        TextView title = Ui.title(this, "自由账本", 28);
        content.addView(title);
        TextView sub = Ui.text(this,
                "支付检测 → 点一下确认 → 自动核销你的月计划",
                13, Ui.MUTED);
        content.addView(sub);
        content.addView(Ui.spacer(this, 16));

        LinearLayout nav = Ui.horizontal(this);
        Button prev = Ui.button(this, "‹");
        Button next = Ui.button(this, "›");
        String[] weekdays = {"一", "二", "三", "四", "五", "六", "日"};
        TextView monthTitle = Ui.title(this,
                day + "  周" + weekdays[day.getDayOfWeek().getValue() - 1], 18);
        monthTitle.setGravity(Gravity.CENTER);
        nav.addView(prev, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 44)));
        nav.addView(monthTitle, Ui.weight(1));
        nav.addView(next, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 44)));
        content.addView(nav);
        prev.setOnClickListener(v -> { day = day.minusDays(1); month = YearMonth.from(day); render(); });
        next.setOnClickListener(v -> { day = day.plusDays(1); month = YearMonth.from(day); render(); });
        monthTitle.setOnClickListener(v -> chooseDay());

        content.addView(Ui.spacer(this, 12));
        content.addView(makeSummaryCard());
        content.addView(Ui.spacer(this, 12));
        LinearLayout quick = Ui.horizontal(this);
        Button expense = Ui.button(this, "记支出"); expense.setTextColor(Ui.ACCENT); expense.setBackground(Ui.roundRect(0xfffceaf0, 14, this));
        Button income = Ui.button(this, "记收入"); income.setTextColor(0xff54b89b); income.setBackground(Ui.roundRect(0xfff2eefc, 14, this));
        quick.addView(expense, Ui.weight(1)); quick.addView(new View(this), new LinearLayout.LayoutParams(Ui.dp(this, 8),1)); quick.addView(income, Ui.weight(1)); content.addView(quick); content.addView(Ui.spacer(this,12));
        expense.setOnClickListener(v -> openQuick("expense")); income.setOnClickListener(v -> openQuick("income"));
        content.addView(makeRecentCard(true));
    }

    private void openQuick(String type){ Intent i=new Intent(this,QuickEntryActivity.class); i.putExtra("month",month.toString()); i.putExtra("date",day.toString()); i.putExtra("type",type); startActivity(i); }

    private View makeBudgetProgressCard(){ LinearLayout c=Ui.card(this); c.addView(Ui.title(this,"预算执行",16)); double p=store.getMonthPlannedAmount(month.toString()),a=store.getMonthBudgetExpense(month.toString()); if(p<=0){c.addView(Ui.text(this,"本月尚未设置计划",12,Ui.MUTED));return c;} c.addView(Ui.text(this,"计划 "+money(p)+"   计划内支出 "+money(a),12,Ui.TEXT)); BudgetProgressView bar=new BudgetProgressView(this,(float)Math.min(1,a/p),a>p); LinearLayout.LayoutParams bp=Ui.match(); bp.height=Ui.dp(this,14); bp.topMargin=Ui.dp(this,10); bp.bottomMargin=Ui.dp(this,8); c.addView(bar,bp); c.addView(Ui.text(this,String.format(Locale.CHINA,"使用率 %.0f%% · %s %s",a*100/p,a>p?"超支":"剩余",money(Math.abs(p-a))),12,a>p?0xffe45168:Ui.MUTED)); return c; }

    private static class BudgetProgressView extends View { private final Paint paint=new Paint(1); private final float progress; private final boolean over; BudgetProgressView(Context c,float p,boolean o){super(c);progress=p;over=o;} protected void onDraw(Canvas c){float r=getHeight()/2f; paint.setColor(0xffddd4f5); c.drawRoundRect(0,0,getWidth(),getHeight(),r,r,paint); paint.setColor(over?0xffe45168:0xffe86a92); c.drawRoundRect(0,0,getWidth()*progress,getHeight(),r,r,paint);} }

    private void refreshBottomNav() {
        if (bottomNav == null) return;
        for (int i = 0; i < bottomNav.getChildCount(); i++) {
            bottomNav.getChildAt(i).setAlpha(i == tab ? 1f : 0.72f);
            if (bottomNav.getChildAt(i) instanceof TextView) ((TextView) bottomNav.getChildAt(i)).setTextColor(i == tab ? Ui.ACCENT : Ui.TEXT);
        }
    }

    private LinearLayout makeBottomNav() {
        LinearLayout bar = Ui.horizontal(this);
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 10));
        bar.setBackgroundColor(Ui.BG);
        String[] labels = {"记账", "计划", "统计", "设置"};
        String[] icons = {"⌂", "▣", "◔", "⚙"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView b = Ui.text(this, icons[i] + "\n" + labels[i], 11, i == tab ? Ui.ACCENT : Ui.TEXT);
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
            b.setTextSize(11);
            b.setTextColor(i == tab ? Ui.ACCENT : Ui.TEXT);
            bar.addView(b, Ui.weight(1));
            b.setOnClickListener(v -> { tab = index; render(); });
        }
        return bar;
    }

    private void chooseDay() {
        DatePickerDialog d = new DatePickerDialog(this, (v, y, m, dayOfMonth) -> {
            day = java.time.LocalDate.of(y, m + 1, dayOfMonth); month = YearMonth.of(y, m + 1); render();
        }, day.getYear(), day.getMonthValue() - 1, day.getDayOfMonth());
        d.setTitle("选择日期"); d.show();
    }

    private void chooseMonth() {
        LinearLayout box = Ui.horizontal(this);
        android.widget.NumberPicker y = new android.widget.NumberPicker(this); y.setMinValue(2000); y.setMaxValue(2100); y.setValue(month.getYear());
        android.widget.NumberPicker m = new android.widget.NumberPicker(this); m.setMinValue(1); m.setMaxValue(12); m.setValue(month.getMonthValue());
        box.addView(y, Ui.weight(1)); box.addView(m, Ui.weight(1));
        new AlertDialog.Builder(this).setTitle("选择月份").setView(box).setNegativeButton("取消", null)
                .setPositiveButton("确定", (d, w) -> { month = YearMonth.of(y.getValue(), m.getValue()); day = month.atDay(1); render(); }).show();
    }

    private void renderPlansPage() {
        content.addView(Ui.title(this, "月计划预算", 28));
        Button selector = Ui.button(this, "▣  " + month.getYear() + "年" + month.getMonthValue() + "月"); selector.setOnClickListener(v -> chooseMonth());
        content.addView(selector, Ui.match()); content.addView(Ui.spacer(this, 14)); content.addView(makePlanCard());
        content.addView(Ui.spacer(this, 12));
        Button manage = Ui.darkButton(this, "＋ 新建或管理计划"); content.addView(manage, Ui.match());
        manage.setOnClickListener(v -> { Intent i = new Intent(this, ManagePlansActivity.class); i.putExtra("month", month.toString()); startActivity(i); });
    }

    private void renderStatsPage() {
        content.addView(Ui.title(this, "统计", 28));
        Button selector = Ui.button(this, "▣  " + month.getYear() + "年" + month.getMonthValue() + "月"); selector.setOnClickListener(v -> chooseMonth());
        content.addView(selector, Ui.match()); content.addView(Ui.spacer(this, 12));
        LinearLayout tabs=Ui.horizontal(this); String[] names={"月度","分类","趋势"};
        for(int i=0;i<3;i++){ final int x=i; TextView b=Ui.text(this,names[i],13,i==statsTab?Ui.ACCENT:Ui.MUTED); b.setGravity(Gravity.CENTER); b.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,10)); tabs.addView(b,Ui.weight(1)); b.setOnClickListener(v->{statsTab=x;render();}); } content.addView(tabs);
        content.addView(Ui.spacer(this, 10));
        if(statsTab==0){ content.addView(makeStatsOverview()); content.addView(Ui.spacer(this,12)); content.addView(makePlanExecutionCard()); }
        else if(statsTab==1){ content.addView(makePieCard()); content.addView(Ui.spacer(this,12)); content.addView(makeCategoryRanking()); }
        else { content.addView(makeTrendCard()); }
    }

    private View makeCategoryRanking(){ LinearLayout c=Ui.card(this); c.addView(Ui.title(this,"支出分类排行",16)); JSONArray txs=store.getMonthTransactions(month.toString()); java.util.LinkedHashMap<String,Double> m=new java.util.LinkedHashMap<>(); double total=0; for(int i=0;i<txs.length();i++){JSONObject o=txs.optJSONObject(i); if(o==null||!"expense".equals(o.optString("type")))continue; String k=o.optString("categoryName","未分类"); double a=o.optDouble("amount"); m.put(k,m.containsKey(k)?m.get(k)+a:a); total+=a;} for(java.util.Map.Entry<String,Double> e:m.entrySet()){ double ratio=e.getValue()/Math.max(total,1); LinearLayout line=Ui.vertical(this); LinearLayout head=Ui.horizontal(this); head.addView(Ui.text(this,e.getKey(),12,Ui.TEXT),Ui.weight(1)); head.addView(Ui.text(this,money(e.getValue())+"  "+String.format(Locale.CHINA,"%.0f%%",ratio*100),12,Ui.MUTED)); line.addView(head); BudgetProgressView bar=new BudgetProgressView(this,(float)ratio,false); LinearLayout.LayoutParams bp=Ui.match(); bp.height=Ui.dp(this,8); bp.topMargin=Ui.dp(this,5); bp.bottomMargin=Ui.dp(this,10); line.addView(bar,bp); c.addView(line);} if(m.isEmpty())c.addView(Ui.text(this,"暂无分类数据",12,Ui.MUTED)); return c; }
    private View makeTrendCard(){
        LinearLayout c=Ui.card(this); c.addView(Ui.title(this,"月内支出趋势",16));
        double[] daily = new double[month.lengthOfMonth()];
        for (int i = 1; i <= daily.length; i++) {
            JSONArray txs = store.getDayTransactions(month.atDay(i).toString());
            for (int j = 0; j < txs.length(); j++) {
                JSONObject tx = txs.optJSONObject(j);
                if (tx != null && "expense".equals(tx.optString("type"))) daily[i - 1] += tx.optDouble("amount", 0);
            }
        }
        HorizontalScrollView chartScroll = new HorizontalScrollView(this);
        chartScroll.setHorizontalScrollBarEnabled(true);
        chartScroll.setFillViewport(false);
        chartScroll.setBackgroundColor(Ui.CARD);
        chartScroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> trendScrollX = scrollX);
        DailyTrendView chart = new DailyTrendView(this, daily, month);
        chart.setMinimumHeight(Ui.dp(this, 250));
        chartScroll.addView(chart, new HorizontalScrollView.LayoutParams(Math.max(Ui.dp(this, 420), Ui.dp(this, daily.length * 44)), Ui.dp(this, 250)));
        c.addView(chartScroll, new LinearLayout.LayoutParams(-1, Ui.dp(this, 250)));
        chartScroll.post(() -> chartScroll.scrollTo(trendScrollX, 0));
        c.addView(Ui.spacer(this, 12));
        c.addView(Ui.title(this,"近三个月对比",16));
        for(int i=2;i>=0;i--){YearMonth ym=month.minusMonths(i); c.addView(Ui.text(this,ym.getYear()+"年"+ym.getMonthValue()+"月   "+money(store.getMonthActualExpense(ym.toString())),13,Ui.TEXT));}
        return c;
    }

    private static class DailyTrendView extends View {
        private final double[] values; private final YearMonth month; private final Paint p = new Paint(1); private int selected = -1;
        DailyTrendView(Context c, double[] v, YearMonth m) { super(c); values = v; month = m; p.setStrokeWidth(Ui.dp(c, 2)); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            if (MeasureSpec.getMode(widthSpec) != MeasureSpec.EXACTLY) width = Math.max(width, Ui.dp(getContext(), values.length * 34));
            setMeasuredDimension(width, Ui.dp(getContext(), 250));
        }
        protected void onDraw(Canvas c) {
            super.onDraw(c); float left=Ui.dp(getContext(), 28), right=getWidth()-Ui.dp(getContext(), 10), top=Ui.dp(getContext(), 14), bottom=getHeight()-Ui.dp(getContext(), 28);
            double max=1; for(double v:values) max=Math.max(max,v); p.setStyle(Paint.Style.STROKE); p.setColor(0xffe5e0e1); p.setStrokeWidth(1);
            for (int grid=0; grid<=4; grid++) { float y=bottom-(bottom-top)*grid/4f; c.drawLine(left,y,right,y,p); }
            p.setStyle(Paint.Style.FILL); p.setColor(Ui.MUTED); p.setTextSize(Ui.dp(getContext(),10));
            for (int grid=0; grid<=4; grid++) { float y=bottom-(bottom-top)*grid/4f; c.drawText(String.format(Locale.CHINA,"%.0f",max*grid/4), 2, y+4, p); }
            p.setColor(Ui.ACCENT); p.setStrokeWidth(Ui.dp(getContext(), 2)); p.setStyle(Paint.Style.STROKE); Path path=new Path();
            for(int i=0;i<values.length;i++){float x=left+(right-left)*i/Math.max(values.length-1,1); float y=bottom-(float)(values[i]/max)*(bottom-top); if(i==0)path.moveTo(x,y); else path.lineTo(x,y);}
            c.drawPath(path,p); p.setStyle(Paint.Style.FILL); p.setColor(Ui.ACCENT);
            for(int i=0;i<values.length;i++){float x=left+(right-left)*i/Math.max(values.length-1,1); float y=bottom-(float)(values[i]/max)*(bottom-top); c.drawCircle(x,y,Ui.dp(getContext(),2.5f),p);}
            p.setColor(Ui.MUTED); p.setTextSize(Ui.dp(getContext(),10));
            for(int i=0;i<values.length;i+=5){float x=left+(right-left)*i/Math.max(values.length-1,1); c.drawText(String.valueOf(i+1), x-6, bottom+Ui.dp(getContext(),18), p);}
            if (selected >= 0 && selected < values.length) {
                float x=left+(right-left)*selected/Math.max(values.length-1,1); float y=bottom-(float)(values[selected]/max)*(bottom-top);
                p.setColor(Ui.TEXT); p.setTextSize(Ui.dp(getContext(),11));
                float labelX = x < left + 90 ? x + 12 : (x > right - 90 ? x - 105 : x - 42);
                c.drawText(month.atDay(selected + 1).toString(), labelX, Math.max(top+14, y-24), p);
                c.drawText(String.format(Locale.CHINA,"¥%.2f", values[selected]), labelX, Math.max(top+29, y-9), p);
                p.setColor(Ui.ACCENT); c.drawCircle(x,y,Ui.dp(getContext(),5),p);
            }
            boolean hasData = false; for (double value : values) if (value > 0) { hasData = true; break; }
            if (!hasData) { p.setColor(Ui.MUTED); p.setTextSize(Ui.dp(getContext(), 12)); c.drawText("本月暂无支出数据", left + Ui.dp(getContext(), 20), (top + bottom) / 2, p); }
        }
        public boolean onTouchEvent(MotionEvent e) { if (e.getAction() == MotionEvent.ACTION_UP) { float left=Ui.dp(getContext(),28), right=getWidth()-Ui.dp(getContext(),10); selected=Math.max(0,Math.min(values.length-1,Math.round((e.getX()-left)/(right-left)*(values.length-1)))); invalidate(); return true; } return true; }
    }
    private View makeStatsOverview() {
        LinearLayout card = Ui.card(this); card.addView(Ui.title(this, "月度分析", 16));
        double planned=store.getMonthPlannedAmount(month.toString()), actual=store.getMonthActualExpense(month.toString());
        double income=0; JSONArray txs=store.getMonthTransactions(month.toString()); for(int i=0;i<txs.length();i++){JSONObject o=txs.optJSONObject(i); if(o!=null&&"income".equals(o.optString("type"))) income+=o.optDouble("amount");}
        LinearLayout row=Ui.horizontal(this); row.addView(statBox("总支出",money(actual),Ui.ACCENT),Ui.weight(1)); row.addView(statBox("总收入",money(income),0xff54b89b),Ui.weight(1)); row.addView(statBox("结余",money(income-actual),0xff8a72d6),Ui.weight(1)); card.addView(row); double budgetSpent=store.getMonthBudgetExpense(month.toString()); card.addView(Ui.text(this, planned<=0?"尚未设置预算":"计划内支出 " + money(budgetSpent) + " · 预算使用率 " + String.format(Locale.CHINA,"%.0f%%", budgetSpent*100/Math.max(planned,1)) + (budgetSpent>planned?" · 超支":""),12,budgetSpent>planned?0xffe45168:Ui.MUTED));
        return card;
    }

    private View makePlanExecutionCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.title(this, "各计划执行", 16));
        JSONArray plans = store.getPlans(month.toString());
        if (plans.length() == 0) { card.addView(Ui.text(this, "本月尚未设置计划", 12, Ui.MUTED)); return card; }
        for (int i = 0; i < plans.length(); i++) {
            JSONObject plan = plans.optJSONObject(i); if (plan == null) continue;
            double budget = plan.optDouble("amount", 0), spent = store.spentForPlan(month.toString(), plan.optString("id"));
            boolean over = spent > budget;
            card.addView(Ui.text(this, plan.optString("name", "未命名计划") + "  · 计划 " + money(budget) + " · 已花 " + money(spent), 12, Ui.TEXT));
            BudgetProgressView bar = new BudgetProgressView(this, (float)Math.min(1, budget <= 0 ? 0 : spent / budget), over);
            LinearLayout.LayoutParams bp = Ui.match(); bp.height = Ui.dp(this, 10); bp.topMargin = Ui.dp(this, 5); bp.bottomMargin = Ui.dp(this, 3); card.addView(bar, bp);
            card.addView(Ui.text(this, over ? "超支 " + money(spent - budget) : "剩余 " + money(budget - spent), 11, over ? Ui.DANGER : Ui.MUTED));
            card.addView(Ui.spacer(this, 8));
        }
        return card;
    }

    private View makePieCard() {
        LinearLayout card = Ui.card(this); card.addView(Ui.title(this, "支出分类", 16));
        JSONArray txs = store.getMonthTransactions(month.toString());
        java.util.LinkedHashMap<String, Float> values = new java.util.LinkedHashMap<>(); float total = 0;
        for (int i=0;i<txs.length();i++) { JSONObject o=txs.optJSONObject(i); if(o==null||!"expense".equals(o.optString("type"))) continue; String k=o.optString("categoryName","未分类"); float a=(float)o.optDouble("amount"); values.put(k, values.containsKey(k)?values.get(k)+a:a); total+=a; }
        final float totalAmount = total;
        PieView pie = new PieView(this, values, totalAmount, (name, amount) -> Toast.makeText(this, name + "  " + money(amount) + "（" + String.format(Locale.CHINA, "%.1f%%", amount * 100 / Math.max(totalAmount, 1)) + "）", Toast.LENGTH_SHORT).show()); card.addView(pie, new LinearLayout.LayoutParams(-1, Ui.dp(this, 210)));
        if(values.isEmpty()) card.addView(Ui.text(this,"暂无支出数据",12,Ui.MUTED)); return card;
    }

    private void renderSettingsPage() {
        content.addView(Ui.title(this, "设置", 28)); content.addView(Ui.text(this,"支付检测、数据迁移和基础资料",13,Ui.MUTED)); content.addView(Ui.spacer(this,16));
        View detection=makeDetectionCard(); content.addView(detection); content.addView(Ui.spacer(this,12)); content.addView(makeActionCard()); content.addView(Ui.spacer(this,12)); content.addView(makeDataCard());
    }

    private static class PieView extends View {
        private final java.util.Map<String,Float> values; private final float total; private final Paint p=new Paint(1); private final int[] colors={0xff9b8f8c,0xffa7a89a,0xffb5a29a,0xff8f9ca3,0xffb4a7b8,0xffc1ad9d}; private final OnSliceClick listener;
        interface OnSliceClick { void onClick(String name, float amount); }
        PieView(Context c, java.util.Map<String,Float> v,float t, OnSliceClick l){super(c);values=v;total=t;listener=l;}
        protected void onDraw(Canvas c){super.onDraw(c); float size=Math.min(getWidth(),getHeight())-24, l=(getWidth()-size)/2f, top=12; RectF r=new RectF(l,top,l+size,top+size); float start=-90; int i=0; for(float v:values.values()){p.setColor(colors[i++%colors.length]); c.drawArc(r,start,360*v/Math.max(total,1),true,p); start+=360*v/Math.max(total,1);} p.setColor(Ui.BG); c.drawCircle(getWidth()/2f,top+size/2f,size*.28f,p); if(values.isEmpty()){p.setColor(0xffe6e4dc);c.drawCircle(getWidth()/2f,top+size/2f,size/2f,p);} }
        public boolean onTouchEvent(MotionEvent e){ if(e.getAction()!=MotionEvent.ACTION_UP || values.isEmpty()) return true; float dx=e.getX()-getWidth()/2f, dy=e.getY()-getHeight()/2f; double angle=Math.toDegrees(Math.atan2(dy,dx))+90; if(angle<0) angle+=360; float cursor=0; for(java.util.Map.Entry<String,Float> x:values.entrySet()){float sweep=360*x.getValue()/Math.max(total,1); if(angle>=cursor&&angle<cursor+sweep){listener.onClick(x.getKey(),x.getValue()); break;} cursor+=sweep;} return true; }
    }

    private View makeDetectionCard() {
        LinearLayout card = Ui.card(this);
        TextView h = Ui.title(this, "支付检测", 16);
        card.addView(h);

        boolean enabled = notificationAccessEnabled();
        TextView status = Ui.text(this,
                enabled
                        ? "已开启通知使用权。检测到含金额的支付通知时，会弹出“确认记账”。"
                        : "尚未开启。需要先允许本 App 读取通知，才能在支付后提醒你记账。",
                12, enabled ? Ui.ACCENT : Ui.MUTED);
        status.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 10));
        card.addView(status);
        android.content.SharedPreferences lp = getSharedPreferences(PaymentNotificationListener.PREFS, MODE_PRIVATE);
        boolean connected = lp.getBoolean("connected", false);
        long lastTime = lp.getLong("last_time", 0);
        String lastPkg = lp.getString("last_package", "");
        String detail = connected ? "监听服务已连接" : "监听服务未连接（打开通知使用权后会自动重连）";
        if (lastTime > 0) detail += "\n最近收到：" + new java.text.SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(new java.util.Date(lastTime)) + (lastPkg.isEmpty() ? "" : " · " + lastPkg);
        TextView listenerInfo = Ui.text(this, detail, 11, connected ? Ui.MUTED : Ui.DANGER);
        listenerInfo.setPadding(0, 0, 0, Ui.dp(this, 10));
        card.addView(listenerInfo);

        LinearLayout row = Ui.horizontal(this);
        Button access = Ui.darkButton(this, enabled ? "通知访问设置" : "开启支付检测");
        Button settings = Ui.button(this, "检测规则");
        row.addView(access, Ui.weight(1));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(Ui.dp(this, 8), 1);
        row.addView(new View(this), sp);
        row.addView(settings, Ui.weight(1));
        card.addView(row);

        access.setOnClickListener(v -> openNotificationAccess());
        settings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        return card;
    }

    private View makeSummaryCard() {
        JSONArray txs = store.getDayTransactions(day.toString());
        double income = 0, expense = 0;
        for (int i = 0; i < txs.length(); i++) {
            JSONObject tx = txs.optJSONObject(i);
            if (tx == null) continue;
            if ("income".equals(tx.optString("type"))) income += tx.optDouble("amount", 0);
            else if ("expense".equals(tx.optString("type"))) expense += tx.optDouble("amount", 0);
        }

        LinearLayout card = Ui.card(this);
        card.addView(Ui.title(this, "本日记录", 16));

        LinearLayout row = Ui.horizontal(this);
        row.setPadding(0, Ui.dp(this, 10), 0, 0);
        row.addView(statBox("收入", money(income), Ui.ACCENT), Ui.weight(1));
        row.addView(statBox("支出", money(expense), Ui.TEXT), Ui.weight(1));
        row.addView(statBox("结余", money(income - expense),
                income - expense < 0 ? Ui.DANGER : Ui.TEXT), Ui.weight(1));
        card.addView(row);
        return card;
    }

    private View statBox(String label, String value, int color) {
        LinearLayout box = Ui.vertical(this);
        TextView l = Ui.text(this, label, 11, Ui.MUTED);
        TextView v = Ui.title(this, value, 17);
        v.setTextColor(color);
        box.addView(l);
        box.addView(v);
        return box;
    }

    private View makeActionCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.title(this, "快速操作", 16));
        card.addView(Ui.spacer(this, 9));

        LinearLayout r1 = Ui.horizontal(this);
        Button cats = Ui.button(this, "自定义分类");
        r1.addView(cats, Ui.weight(1));
        r1.addView(space8(), new LinearLayout.LayoutParams(Ui.dp(this, 8), 1));
        card.addView(r1);

        card.addView(Ui.spacer(this, 8));
        cats.setOnClickListener(v ->
                startActivity(new Intent(this, ManageCategoriesActivity.class)));
        return card;
    }

    private View makePlanCard() {
        LinearLayout card = Ui.card(this);
        LinearLayout head = Ui.horizontal(this);
        TextView title = Ui.title(this, "月计划", 16);
        head.addView(title, Ui.weight(1));
        card.addView(head);


        JSONArray plans = store.getPlans(month.toString());
        if (plans.length() == 0) {
            TextView empty = Ui.text(this,
                    "还没有计划。你可以建立“房租”“旅行”“订阅”等任何自己定义的预算项。",
                    12, Ui.MUTED);
            empty.setPadding(0, Ui.dp(this, 10), 0, 0);
            card.addView(empty);
            return card;
        }

        for (int i = 0; i < plans.length(); i++) {
            JSONObject p = plans.optJSONObject(i);
            if (p == null) continue;
            double budget = p.optDouble("amount", 0);
            double spent = store.spentForPlan(month.toString(), p.optString("id"));
            double left = budget - spent;

            LinearLayout row = Ui.vertical(this);
            row.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 5));
            TextView name = Ui.title(this, p.optString("name", "未命名计划"), 14);
            row.addView(name);
            String detail = "计划 " + money(budget) + " · 已花 " + money(spent) +
                    " · " + (left >= 0 ? "剩余 " : "超支 ") + money(Math.abs(left));
            row.addView(Ui.text(this, detail, 11, left >= 0 ? Ui.MUTED : Ui.DANGER));
            card.addView(row);
        }
        return card;
    }

    private View makeRecentCard(boolean editable) {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.title(this, day + " 流水", 16));

        JSONArray txs = store.getDayTransactions(day.toString());
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < txs.length(); i++) {
            JSONObject o = txs.optJSONObject(i);
            if (o != null) list.add(o);
        }
        list.sort((a, b) -> Long.compare(b.optLong("createdAt", 0), a.optLong("createdAt", 0)));

        if (list.isEmpty()) {
            TextView empty = Ui.text(this, "这一天还没有记录。", 12, Ui.MUTED);
            empty.setPadding(0, Ui.dp(this, 10), 0, 0);
            card.addView(empty);
            return card;
        }

        for (int i = 0; i < list.size(); i++) {
            JSONObject tx = list.get(i);
            LinearLayout row = Ui.vertical(this);
            row.setPadding(0, Ui.dp(this, 11), 0, Ui.dp(this, 9));

            LinearLayout left = Ui.vertical(this);
            String merchant = "";
            String category = tx.optString("categoryName");
            if (category.isEmpty()) {
                category = store.getCategoryName(tx.optString("type"), tx.optString("categoryId"));
            }
            String primary = !merchant.isEmpty() ? merchant :
                    (!category.isEmpty() ? category : "未分类");
            left.addView(Ui.title(this, primary, 14));

            String meta = tx.optString("date") +
                    (!merchant.isEmpty() && !category.isEmpty() ? " · " + category : "") +
                    (!tx.optString("planId").isEmpty()
                            ? " · 计划：" + store.getPlanName(month.toString(), tx.optString("planId"))
                            : "");
            TextView amount = Ui.title(this,
                    ("income".equals(tx.optString("type")) ? "+" : "-") +
                            money(tx.optDouble("amount", 0)),
                    14);
            if ("income".equals(tx.optString("type"))) amount.setTextColor(Ui.ACCENT);

            LinearLayout first = Ui.horizontal(this);
            LinearLayout details = Ui.vertical(this);
            details.addView(left);
            details.addView(Ui.text(this, meta, 10, Ui.MUTED));
            first.addView(details, Ui.weight(2));
            LinearLayout amountBox = Ui.vertical(this);
            amountBox.setGravity(Gravity.CENTER_VERTICAL);
            amountBox.addView(amount);
            first.addView(amountBox, Ui.weight(1));
            if (editable) {
                Button edit = actionButton("编辑", 0xff8b70d1, 0xfff5f0ff, 0xffddd1f5);
                Button del = actionButton("删除", 0xffd94f76, 0xfffff0f4, 0xfff4cbd6);
                first.addView(edit, Ui.weight(1));
                first.addView(del, Ui.weight(1));
                edit.setOnClickListener(v -> showTransactionEditor(tx));
                del.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("删除这笔流水？").setNegativeButton("取消", null).setPositiveButton("删除", (d,w) -> { store.deleteTransaction(tx.optString("id")); render(); }).show());
            }
            row.addView(first);
            card.addView(row);
        }
        return card;
    }

    private Button actionButton(String label, int color, int fill, int stroke) {
        Button b = Ui.textButton(this, label, 11, color);
        b.setMinHeight(0); b.setMinimumHeight(0); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setPadding(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
        b.setBackground(Ui.roundStroke(fill, stroke, 1, 18, this));
        return b;
    }

    private void showTransactionEditor(JSONObject tx) {
        Intent i = new Intent(this, QuickEntryActivity.class);
        i.putExtra("edit_id", tx.optString("id"));
        i.putExtra("amount", tx.optDouble("amount"));
        i.putExtra("date", tx.optString("date"));
        i.putExtra("category_id", tx.optString("categoryId"));
        i.putExtra("account_id", tx.optString("accountId"));
        i.putExtra("plan_id", tx.optString("planId"));
        i.putExtra("note", tx.optString("note"));
        i.putExtra("type", tx.optString("type", "expense"));
        startActivity(i);
    }

    private View makeDataCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.title(this, "数据", 16));
        card.addView(Ui.text(this,
                "所有数据保存在本机。JSON 可以和之前的自由账本网页版互相迁移。",
                11, Ui.MUTED));
        card.addView(Ui.spacer(this, 9));

        LinearLayout row = Ui.horizontal(this);
        Button export = Ui.button(this, "导出 JSON");
        Button importBtn = Ui.button(this, "导入 JSON");
        row.addView(export, Ui.weight(1));
        row.addView(space8(), new LinearLayout.LayoutParams(Ui.dp(this, 8), 1));
        row.addView(importBtn, Ui.weight(1));
        card.addView(row);

        export.setOnClickListener(v -> exportJson());
        importBtn.setOnClickListener(v -> importJson());
        return card;
    }

    private View space8() {
        return new View(this);
    }

    private boolean notificationAccessEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(getPackageName());
    }

    private void openNotificationAccess() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开通知访问设置", Toast.LENGTH_SHORT).show();
        }
    }

    private void exportJson() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "自由账本_" + month + ".json");
        startActivityForResult(i, REQ_EXPORT);
    }

    private void importJson() {
        new AlertDialog.Builder(this)
                .setTitle("导入 JSON")
                .setMessage("导入会覆盖当前 App 内的账本数据。继续吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("继续", (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("application/json");
                    startActivityForResult(i, REQ_IMPORT);
                })
                .show();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        try {
            if (requestCode == REQ_EXPORT) {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out != null) {
                        out.write(store.exportJson().getBytes(StandardCharsets.UTF_8));
                        Toast.makeText(this, "JSON 已导出", Toast.LENGTH_SHORT).show();
                    }
                }
            } else if (requestCode == REQ_IMPORT) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(
                        getContentResolver().openInputStream(uri), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line).append('\n');
                }
                if (store.importJson(sb.toString())) {
                    Toast.makeText(this, "导入成功", Toast.LENGTH_SHORT).show();
                    render();
                } else {
                    Toast.makeText(this, "不是有效的自由账本 JSON", Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "操作失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static String money(double v) {
        return String.format(Locale.CHINA, "¥%.2f", v);
    }
}
