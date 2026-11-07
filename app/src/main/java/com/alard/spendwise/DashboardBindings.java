package com.alard.spendwise;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.WeakHashMap;

/** Refreshes dashboard totals from saved transactions without altering the fixed XML design. */
public final class DashboardBindings {
    private static final int WHITE = 0xFFF8FAFC;
    private static final int MUTED = 0xFF94A3B8;
    private static final int INCOME_GREEN = 0xFF22C55E;
    private static final int EXPENSE_RED = 0xFFEF4444;
    private static final int INACTIVE = 0xFF102A56;
    private static final int ROW_BG = 0xFF112E58;
    private static final TimeZone DHAKA = TimeZone.getTimeZone("Asia/Dhaka");
    private static final Map<Activity, Period> PERIODS = new WeakHashMap<>();
    private static final Map<Activity, Boolean> RECENT_EXPANDED = new WeakHashMap<>();

    public enum Period { MONTH, YEAR, TOTAL }

    private DashboardBindings() {
        // No instances.
    }

    /** Call from MainActivity.onResume() to refresh after Add/Edit/Delete. */
    public static void bind(Activity activity) {
        Period initial = PERIODS.containsKey(activity) ? PERIODS.get(activity) : Period.MONTH;
        PERIODS.put(activity, initial);

        connect(activity, R.id.btnIncomeMonth, Period.MONTH);
        connect(activity, R.id.btnIncomeYear, Period.YEAR);
        connect(activity, R.id.btnIncomeAll, Period.TOTAL);
        connect(activity, R.id.btnExpenseMonth, Period.MONTH);
        connect(activity, R.id.btnExpenseYear, Period.YEAR);
        connect(activity, R.id.btnExpenseAll, Period.TOTAL);
        connectRecentToggle(activity);

        refresh(activity);
    }

    public static Period getSelectedPeriod(Activity activity) {
        Period period = PERIODS.get(activity);
        return period == null ? Period.MONTH : period;
    }

    private static void connect(Activity activity, int viewId, Period period) {
        View view = activity.findViewById(viewId);
        if (view != null) {
            view.setOnClickListener(v -> {
                PERIODS.put(activity, period);
                refresh(activity);
            });
        }
    }

    /** Expand/collapse the last seven calendar days without leaving Dashboard. */
    private static void connectRecentToggle(Activity activity) {
        View view = activity.findViewById(R.id.txtViewAll);
        if (view != null) {
            view.setOnClickListener(v -> {
                boolean expanded = Boolean.TRUE.equals(RECENT_EXPANDED.get(activity));
                RECENT_EXPANDED.put(activity, !expanded);
                refresh(activity);
            });
        }
    }

    private static void refresh(Activity activity) {
        Period period = getSelectedPeriod(activity);
        long now = System.currentTimeMillis();
        long periodStart = startOfPeriod(period, now);

        final List<TransactionStore.Record> records;
        try {
            records = TransactionStore.getAll(activity);
        } catch (RuntimeException error) {
            Toast.makeText(activity, "Unable to load saved transactions", Toast.LENGTH_LONG).show();
            return;
        }

        long lifetimeIncome = 0;
        long lifetimeExpense = 0;
        long periodIncome = 0;
        long periodExpense = 0;
        Map<String, Long> incomeByCategory = new HashMap<>();
        Map<String, Long> expenseByCategory = new HashMap<>();

        for (TransactionStore.Record record : records) {
            // Read each transaction once. A record belongs to one category only.
            if (record.isIncome) {
                lifetimeIncome = Math.addExact(lifetimeIncome, record.amountPaisa);
            } else {
                lifetimeExpense = Math.addExact(lifetimeExpense, record.amountPaisa);
            }
            if (record.timestamp < periodStart || record.timestamp > now) {
                continue;
            }
            if (record.isIncome) {
                periodIncome = Math.addExact(periodIncome, record.amountPaisa);
                addAmount(incomeByCategory, record.categoryId, record.amountPaisa);
            } else {
                periodExpense = Math.addExact(periodExpense, record.amountPaisa);
                addAmount(expenseByCategory, record.categoryId, record.amountPaisa);
            }
        }

        showText(activity, R.id.txtCurrentBalance,
                money(Math.subtractExact(lifetimeIncome, lifetimeExpense)));
        showText(activity, R.id.txtTotalIncome, money(periodIncome));
        showText(activity, R.id.txtTotalExpense, money(periodExpense));

        showHighest(activity, true, incomeByCategory, periodIncome);
        showHighest(activity, false, expenseByCategory, periodExpense);
        updatePeriodButtons(activity, period);
        updateRecentTransactions(activity, records);
    }

    /** Calendar month/year boundaries in Bangladesh time, never rolling 30/365 days. */
    private static long startOfPeriod(Period period, long now) {
        if (period == Period.TOTAL) return Long.MIN_VALUE;
        Calendar cal = Calendar.getInstance(DHAKA);
        cal.setTimeInMillis(now);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (period == Period.MONTH) {
            cal.set(Calendar.DAY_OF_MONTH, 1);
        } else {
            cal.set(Calendar.DAY_OF_YEAR, 1);
        }
        return cal.getTimeInMillis();
    }

    private static void addAmount(Map<String, Long> totals, String categoryId, long amount) {
        long previous = totals.containsKey(categoryId) ? totals.get(categoryId) : 0L;
        totals.put(categoryId, Math.addExact(previous, amount));
    }

    private static void showHighest(Activity activity, boolean isIncome,
                                    Map<String, Long> totals, long overall) {
        int nameId = isIncome ? R.id.txtHighestIncomeCategory : R.id.txtHighestExpenseCategory;
        int amountId = isIncome ? R.id.txtHighestIncomeAmount : R.id.txtHighestExpenseAmount;
        int percentId = isIncome ? R.id.txtHighestIncomePercent : R.id.txtHighestExpensePercent;

        String name = isIncome ? "No income yet" : "No expenses yet";
        long highestAmount = 0;
        // Iterating the frozen catalog makes ties deterministic.
        for (CategoryCatalog.Category category : CategoryCatalog.getCategories(isIncome)) {
            long amount = totals.containsKey(category.getId()) ? totals.get(category.getId()) : 0L;
            if (amount > highestAmount) {
                highestAmount = amount;
                name = category.getName();
            }
        }
        showText(activity, nameId, name);
        showText(activity, amountId, money(highestAmount));
        int percentage = overall > 0
                ? (int) Math.round(100.0 * highestAmount / overall) : 0;
        showText(activity, percentId, percentage + "% of total");
    }

    private static void updatePeriodButtons(Activity activity, Period period) {
        styleButton(activity, R.id.btnIncomeMonth, period == Period.MONTH, INCOME_GREEN);
        styleButton(activity, R.id.btnIncomeYear, period == Period.YEAR, INCOME_GREEN);
        styleButton(activity, R.id.btnIncomeAll, period == Period.TOTAL, INCOME_GREEN);
        styleButton(activity, R.id.btnExpenseMonth, period == Period.MONTH, EXPENSE_RED);
        styleButton(activity, R.id.btnExpenseYear, period == Period.YEAR, EXPENSE_RED);
        styleButton(activity, R.id.btnExpenseAll, period == Period.TOTAL, EXPENSE_RED);
    }

    private static void styleButton(Activity activity, int id, boolean selected, int color) {
        TextView button = activity.findViewById(id);
        if (button == null) return;
        GradientDrawable background = new GradientDrawable();
        background.setColor(selected ? color : INACTIVE);
        background.setCornerRadius(dp(activity, 9));
        if (selected) background.setStroke(dp(activity, 1), color);
        button.setBackground(background);
        button.setTextColor(selected ? WHITE : MUTED);
        button.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    /** Show up to three recent items, or all items from the last 7 calendar days. */
    private static void updateRecentTransactions(Activity activity,
                                                 List<TransactionStore.Record> records) {
        LinearLayout container = activity.findViewById(R.id.layoutRecentTransactions);
        if (container == null) return;

        // 7 calendar dates including today, based on Asia/Dhaka local midnight.
        long now = System.currentTimeMillis();
        Calendar week = Calendar.getInstance(DHAKA);
        week.setTimeInMillis(now);
        week.set(Calendar.HOUR_OF_DAY, 0);
        week.set(Calendar.MINUTE, 0);
        week.set(Calendar.SECOND, 0);
        week.set(Calendar.MILLISECOND, 0);
        week.add(Calendar.DAY_OF_MONTH, -6);
        long weekStart = week.getTimeInMillis();

        List<TransactionStore.Record> recent = new ArrayList<>();
        for (TransactionStore.Record record : records) {
            if (record.timestamp >= weekStart && record.timestamp <= now) {
                recent.add(record);
            }
        }
        // TransactionStore.getAll() returns newest-first, but sort defensively.
        recent.sort((left, right) -> Long.compare(right.timestamp, left.timestamp));

        boolean expanded = Boolean.TRUE.equals(RECENT_EXPANDED.get(activity));
        TextView toggle = activity.findViewById(R.id.txtViewAll);
        if (toggle != null) {
            toggle.setText(expanded ? "View Less  ‹" : "View All  ›");
        }

        container.removeAllViews();
        if (recent.isEmpty()) {
            TextView empty = text(activity,
                    "No transactions in the last 7 days",
                    13, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(activity, 8), dp(activity, 16), dp(activity, 8), dp(activity, 16));
            container.addView(empty);
            return;
        }

        int count = expanded ? recent.size() : Math.min(3, recent.size());
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);
        dateFormat.setTimeZone(DHAKA);
        for (int i = 0; i < count; i++) {
            TransactionStore.Record record = recent.get(i);
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(activity, 12), dp(activity, 11), dp(activity, 12), dp(activity, 11));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(ROW_BG);
            bg.setCornerRadius(dp(activity, 12));
            row.setBackground(bg);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.topMargin = dp(activity, 7);
            container.addView(row, rowParams);

            TextView icon = text(activity, record.isIncome ? "↓" : "↑", 23,
                    record.isIncome ? INCOME_GREEN : EXPENSE_RED, true);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(activity, 30), -2);
            row.addView(icon, iconParams);

            LinearLayout details = new LinearLayout(activity);
            details.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(0, -2, 1f);
            row.addView(details, detailParams);
            String title = record.note.trim().isEmpty() ? record.subcategory : record.note;
            TextView primary = text(activity, title, 13, WHITE, true);
            primary.setSingleLine(true);
            primary.setEllipsize(android.text.TextUtils.TruncateAt.END);
            details.addView(primary);
            details.addView(text(activity,
                    record.categoryName + " · " + dateFormat.format(record.timestamp),
                    11, MUTED, false));

            TextView amount = text(activity,
                    (record.isIncome ? "+" : "−") + money(record.amountPaisa),
                    14, record.isIncome ? INCOME_GREEN : EXPENSE_RED, true);
            amount.setSingleLine(true);
            row.addView(amount);
        }
    }

    private static TextView text(Activity activity, String value, int sizeSp,
                                 int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private static void showText(Activity activity, int id, String value) {
        TextView target = activity.findViewById(id);
        if (target != null) target.setText(value);
    }

    /** Format integer paisa without floating-point money errors. */
    private static String money(long paisa) {
        DecimalFormat format = new DecimalFormat("#,##0.00",
                DecimalFormatSymbols.getInstance(Locale.US));
        String abs = format.format(BigDecimal.valueOf(paisa, 2).abs());
        return paisa < 0 ? "−৳" + abs : "৳" + abs;
    }

    private static int dp(Activity activity, float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}