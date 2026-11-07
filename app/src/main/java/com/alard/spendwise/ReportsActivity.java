package com.alard.spendwise;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Reports screen: calendar summaries and monthly income/expense trends. */
public final class ReportsActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(7, 23, 57);
    private static final int SURFACE = Color.rgb(15, 39, 79);
    private static final int SECONDARY = Color.rgb(16, 42, 86);
    private static final int BORDER = Color.rgb(30, 58, 95);
    private static final int BLUE = Color.rgb(59, 130, 246);
    private static final int GREEN = Color.rgb(34, 197, 94);
    private static final int RED = Color.rgb(239, 68, 68);
    private static final int WHITE = Color.rgb(248, 250, 252);
    private static final int MUTED = Color.rgb(148, 163, 184);
    private static final TimeZone DHAKA = TimeZone.getTimeZone("Asia/Dhaka");

    private enum Period { MONTH, YEAR, TOTAL }
    private Period selectedPeriod = Period.MONTH;
    private final NumberFormat takaFormat = NumberFormat.getNumberInstance(Locale.US);
    private LinearLayout pageContent;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        takaFormat.setMinimumFractionDigits(2);
        takaFormat.setMaximumFractionDigits(2);
        buildScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        showReport();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);

        FrameLayout header = new FrameLayout(this);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(-1, dp(62));
        headerParams.setMargins(dp(15), dp(6), dp(15), 0);
        root.addView(header, headerParams);

        TextView brand = text("◆ SpendWise", 21, WHITE, true);
        SpannableString logo = new SpannableString("◆ SpendWise");
        logo.setSpan(new ForegroundColorSpan(BLUE), 0, 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        logo.setSpan(new ForegroundColorSpan(BLUE), 7, logo.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        brand.setText(logo);
        brand.setGravity(Gravity.CENTER);
        header.addView(brand, new FrameLayout.LayoutParams(-1, -1));
        // Back button returns to the Dashboard without making a second instance.
        TextView back = text("‹", 36, WHITE, false);
        back.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(dp(45), -1, Gravity.START);
        header.addView(back, backParams);
        back.setContentDescription("Back to Dashboard");
        back.setOnClickListener(v -> finish());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        pageContent = new LinearLayout(this);
        pageContent.setOrientation(LinearLayout.VERTICAL);
        pageContent.setPadding(dp(16), dp(8), dp(16), dp(24));
        scroll.addView(pageContent);

        root.addView(bottomNavigation(), new LinearLayout.LayoutParams(-1, dp(64)));
    }

    private void showReport() {
        if (pageContent == null) return;
        pageContent.removeAllViews();
        List<TransactionStore.Record> records;
        try {
            records = TransactionStore.getAll(this);
        } catch (RuntimeException error) {
            pageContent.addView(text("Unable to load reports. Sign in and try again.",
                    15, RED, false));
            return;
        }

        pageContent.addView(text("Reports", 27, WHITE, true), spaced(-1, -2, 0, 0, 0, 0));
        pageContent.addView(text("Your income, expenses and spending trends", 13, MUTED, false),
                spaced(-1, -2, 0, 3, 0, 16));
        pageContent.addView(periodSelector(), spaced(-1, dp(45), 0, 0, 0, 14));

        long now = System.currentTimeMillis();
        long start = periodStart(now);
        long totalIncome = 0, totalExpense = 0;
        Map<String, Long> expenseTotals = new HashMap<>();
        Map<String, Long> incomeTotals = new HashMap<>();
        try {
            for (TransactionStore.Record record : records) {
                if (record.timestamp < start || record.timestamp > now) continue;
                if (record.isIncome) {
                    totalIncome = Math.addExact(totalIncome, record.amountPaisa);
                    addTotal(incomeTotals, record.categoryName, record.amountPaisa);
                } else {
                    totalExpense = Math.addExact(totalExpense, record.amountPaisa);
                    addTotal(expenseTotals, record.categoryName, record.amountPaisa);
                }
            }
        } catch (ArithmeticException error) {
            pageContent.addView(text("Amount exceeds the supported reporting range.",
                    14, RED, false));
            return;
        }

        LinearLayout totals = new LinearLayout(this);
        totals.setOrientation(LinearLayout.HORIZONTAL);
        totals.addView(summaryCard("Total Income", money(totalIncome), GREEN),
                new LinearLayout.LayoutParams(0, dp(108), 1));
        View gap = new View(this);
        totals.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        totals.addView(summaryCard("Total Expense", money(totalExpense), RED),
                new LinearLayout.LayoutParams(0, dp(108), 1));
        pageContent.addView(totals);

        LinearLayout netCard = card(SECONDARY, BORDER);
        netCard.addView(text("Net for Selected Period", 13, MUTED, false));
        netCard.addView(text(money(totalIncome - totalExpense), 23, WHITE, true),
                spaced(-1, -2, 0, 8, 0, 0));
        pageContent.addView(netCard, spaced(-1, -2, 0, 12, 0, 18));

        // Six real calendar months, including the current month.
        LinearLayout trend = card(SURFACE, BORDER);
        trend.addView(text("Monthly Income & Expense", 18, WHITE, true));
        trend.addView(text("Last 6 calendar months (Asia/Dhaka)", 12, MUTED, false),
                spaced(-1, -2, 0, 3, 0, 15));
        addMonthlyChart(trend, records, now);
        pageContent.addView(trend, spaced(-1, -2, 0, 0, 0, 16));

        pageContent.addView(categoryCard("Top Income Sources", incomeTotals, totalIncome, GREEN));
        pageContent.addView(categoryCard("Top Expense Categories", expenseTotals, totalExpense, RED),
                spaced(-1, -2, 0, 14, 0, 0));
    }

    private LinearLayout periodSelector() {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.HORIZONTAL);
        holder.setPadding(dp(4), dp(4), dp(4), dp(4));
        holder.setBackground(shape(SECONDARY, BORDER, 13));
        addPeriodButton(holder, "This Month", Period.MONTH);
        addPeriodButton(holder, "This Year", Period.YEAR);
        addPeriodButton(holder, "Total", Period.TOTAL);
        return holder;
    }

    private void addPeriodButton(LinearLayout row, String name, Period period) {
        boolean selected = period == selectedPeriod;
        TextView button = text(name, 13, selected ? WHITE : MUTED, selected);
        button.setGravity(Gravity.CENTER);
        button.setBackground(shape(selected ? BLUE : SECONDARY, selected ? BLUE : SECONDARY, 10));
        row.addView(button, new LinearLayout.LayoutParams(0, -1, 1));
        button.setOnClickListener(v -> {
            selectedPeriod = period;
            showReport();
        });
    }

    private LinearLayout summaryCard(String label, String value, int accent) {
        int tint = accent == GREEN ? Color.rgb(12, 62, 48) : Color.rgb(75, 32, 53);
        LinearLayout card = card(tint, accent);
        card.addView(text(label, 12, MUTED, false));
        card.addView(text(value, 19, accent, true), spaced(-1, -2, 0, 12, 0, 0));
        return card;
    }

    private void addMonthlyChart(LinearLayout target, List<TransactionStore.Record> records,
                                 long now) {
        Calendar first = Calendar.getInstance(DHAKA);
        first.setTimeInMillis(now);
        first.set(Calendar.DAY_OF_MONTH, 1);
        clearTime(first);
        first.add(Calendar.MONTH, -5);
        long[] starts = new long[7];
        Calendar cursor = (Calendar) first.clone();
        for (int i = 0; i < starts.length; i++) {
            starts[i] = cursor.getTimeInMillis();
            cursor.add(Calendar.MONTH, 1);
        }
        long[] income = new long[6];
        long[] expense = new long[6];
        for (TransactionStore.Record record : records) {
            if (record.timestamp > now) continue;
            for (int i = 0; i < 6; i++) {
                if (record.timestamp >= starts[i] && record.timestamp < starts[i + 1]) {
                    try {
                        if (record.isIncome) income[i] = Math.addExact(income[i], record.amountPaisa);
                        else expense[i] = Math.addExact(expense[i], record.amountPaisa);
                    } catch (ArithmeticException ignored) {
                        // This row will display the capped value.
                        if (record.isIncome) income[i] = Long.MAX_VALUE;
                        else expense[i] = Long.MAX_VALUE;
                    }
                    break;
                }
            }
        }
        long maximum = 0;
        for (int i = 0; i < 6; i++) {
            maximum = Math.max(maximum, Math.max(income[i], expense[i]));
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM yyyy", Locale.US);
        dateFormat.setTimeZone(DHAKA);
        for (int i = 0; i < 6; i++) {
            target.addView(text(dateFormat.format(new Date(starts[i])), 13, WHITE, true),
                    spaced(-1, -2, 0, i == 0 ? 0 : 12, 0, 5));
            target.addView(chartLine("Income", income[i], maximum, GREEN));
            target.addView(chartLine("Expense", expense[i], maximum, RED),
                    spaced(-1, -2, 0, 3, 0, 0));
        }
    }

    private LinearLayout chartLine(String label, long amount, long maximum, int color) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.addView(text(label, 12, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(text(money(amount), 12, color, true));
        line.addView(top);
        ProgressBar progress = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgress(maximum == 0 ? 0 : (int) Math.min(1000,
                Math.round(1000.0 * ((double) amount / (double) maximum))));
        progress.setProgressTintList(ColorStateList.valueOf(color));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(BORDER));
        line.addView(progress, spaced(-1, dp(8), 0, 3, 0, 0));
        return line;
    }

    private LinearLayout categoryCard(String title, Map<String, Long> amounts,
                                      long total, int accent) {
        LinearLayout card = card(SURFACE, BORDER);
        card.addView(text(title, 17, WHITE, true));
        if (amounts.isEmpty()) {
            card.addView(text("No transactions in this period", 13, MUTED, false),
                    spaced(-1, -2, 0, 10, 0, 2));
            return card;
        }
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(amounts.entrySet());
        Collections.sort(sorted, (left, right) -> Long.compare(right.getValue(), left.getValue()));
        int count = Math.min(5, sorted.size());
        for (int i = 0; i < count; i++) {
            Map.Entry<String, Long> entry = sorted.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(text(entry.getKey(), 12, WHITE, false),
                    new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(text(money(entry.getValue()), 12, accent, true));
            card.addView(row, spaced(-1, -2, 0, 12, 0, 2));
            card.addView(categoryProgress(entry.getValue(), total, accent));
        }
        return card;
    }

    private ProgressBar categoryProgress(long value, long total, int accent) {
        ProgressBar progress = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgress(total <= 0 ? 0 : (int) Math.min(1000,
                Math.round(1000.0 * ((double) value / (double) total))));
        progress.setProgressTintList(ColorStateList.valueOf(accent));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(BORDER));
        progress.setLayoutParams(spaced(-1, dp(8), 0, 0, 0, 0));
        return progress;
    }

    private LinearLayout bottomNavigation() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(shape(SURFACE, BORDER, 0));
        addNavigationItem(bar, "⌂\nHome", false, () -> finish());
        addNavigationItem(bar, "☷\nTransactions", false,
                () -> startActivity(new Intent(this, TransactionHistoryActivity.class)));
        addNavigationItem(bar, "▥\nReports", true, () -> { });
        addNavigationItem(bar, "⚙\nSettings", false,
                () -> startActivity(new Intent(this, SettingsActivity.class)));
        return bar;
    }

    private void addNavigationItem(LinearLayout bar, String label, boolean selected,
                                   Runnable action) {
        TextView item = text(label, 12, selected ? BLUE : MUTED, selected);
        item.setGravity(Gravity.CENTER);
        bar.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
        item.setOnClickListener(v -> action.run());
    }

    private long periodStart(long now) {
        if (selectedPeriod == Period.TOTAL) return Long.MIN_VALUE;
        Calendar calendar = Calendar.getInstance(DHAKA);
        calendar.setTimeInMillis(now);
        if (selectedPeriod == Period.YEAR) calendar.set(Calendar.DAY_OF_YEAR, 1);
        else calendar.set(Calendar.DAY_OF_MONTH, 1);
        clearTime(calendar);
        return calendar.getTimeInMillis();
    }

    private static void clearTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private static void addTotal(Map<String, Long> map, String name, long amount) {
        map.put(name, Math.addExact(map.containsKey(name) ? map.get(name) : 0L, amount));
    }

    private String money(long paisa) {
        return "৳" + takaFormat.format(BigDecimal.valueOf(paisa, 2));
    }

    private LinearLayout card(int background, int stroke) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(15), dp(14), dp(15));
        card.setBackground(shape(background, stroke, 15));
        return card;
    }

    private GradientDrawable shape(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (stroke != fill) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams spaced(int width, int height,
                                             int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
