package com.alard.spendwise;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Income/Expense details: calendar periods, expandable categories and fixed navigation. */
public class BreakdownActivity extends AppCompatActivity {

    public static final String EXTRA_IS_INCOME = "is_income";
    public static final String EXTRA_PERIOD = "period"; // MONTH, YEAR, TOTAL

    private static final int NAVY = 0xFF071739;
    private static final int SURFACE = 0xFF0F274F;
    private static final int ROW = 0xFF102A56;
    private static final int WHITE = 0xFFF8FAFC;
    private static final int MUTED = 0xFF94A3B8;
    private static final int CYAN = 0xFF0AA8FF;
    private static final int GREEN = 0xFF22C55E;
    private static final int RED = 0xFFEF4444;
    private static final int TRACK = 0xFF1E3A5F;
    private static final TimeZone DHAKA = TimeZone.getTimeZone("Asia/Dhaka");

    private boolean isIncome;
    private DashboardBindings.Period period = DashboardBindings.Period.MONTH;
    private long anchorMillis;
    private String expandedCategoryId;
    private LinearLayout root;
    private ScrollView scroll;
    private long earliestRecordedTimestamp = Long.MAX_VALUE;
    private long selectedTotalPaisa;
    private final Map<String, Long> mainTotals = new HashMap<>();
    private final Map<String, Map<String, Long>> subTotals = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NAVY);
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });

        if (savedInstanceState != null) {
            isIncome = savedInstanceState.getBoolean("isIncome", false);
            period = parsePeriod(savedInstanceState.getString("period"));
            anchorMillis = savedInstanceState.getLong("anchorMillis", System.currentTimeMillis());
            expandedCategoryId = savedInstanceState.getString("expandedCategoryId");
        } else {
            isIncome = getIntent().getBooleanExtra(EXTRA_IS_INCOME, false);
            period = parsePeriod(getIntent().getStringExtra(EXTRA_PERIOD));
            anchorMillis = System.currentTimeMillis();
        }
        render(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (root != null) render(true);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("isIncome", isIncome);
        out.putString("period", period.name());
        out.putLong("anchorMillis", anchorMillis);
        out.putString("expandedCategoryId", expandedCategoryId);
    }

    private DashboardBindings.Period parsePeriod(String value) {
        if ("YEAR".equalsIgnoreCase(value)) return DashboardBindings.Period.YEAR;
        if ("TOTAL".equalsIgnoreCase(value)) return DashboardBindings.Period.TOTAL;
        return DashboardBindings.Period.MONTH;
    }

    private int accent() {
        return isIncome ? GREEN : RED;
    }

    private int dp(float dimension) {
        return Math.round(dimension * getResources().getDisplayMetrics().density);
    }

    private LinearLayout vertical() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private LinearLayout horizontal() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private GradientDrawable shape(int color, int border, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        if (border != 0) bg.setStroke(dp(1), border);
        return bg;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView result = new TextView(this);
        result.setText(value);
        result.setTextSize(sizeSp);
        result.setTextColor(color);
        if (bold) result.setTypeface(null, Typeface.BOLD);
        return result;
    }

    private LinearLayout.LayoutParams margins(int width, int height, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.topMargin = dp(top);
        params.bottomMargin = dp(bottom);
        return params;
    }

    private void render(boolean keepScrollPosition) {
        int oldY = keepScrollPosition && scroll != null ? scroll.getScrollY() : 0;
        if (!loadTotals()) return;
        root.removeAllViews();
        addHeader();

        scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(true);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout content = vertical();
        content.setPadding(dp(14), dp(4), dp(14), dp(12));
        scroll.addView(content);

        TextView title = text(isIncome ? "Income Breakdown" : "Expense Breakdown", 23, WHITE, true);
        title.setGravity(Gravity.CENTER);
        content.addView(title);
        TextView periodText = text("▦  " + periodLabel(), 13, WHITE, false);
        periodText.setGravity(Gravity.CENTER);
        content.addView(periodText, margins(-1, -2, 4, 13));
        addHighestCard(content);
        addCategoryList(content);
        addBottomFooter();
        if (keepScrollPosition) {
            scroll.post(() -> scroll.scrollTo(0, oldY));
        }
    }

    private void addHeader() {
        FrameLayout header = new FrameLayout(this);
        header.setMinimumHeight(dp(57));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(57)));

        TextView back = text("‹", 32, WHITE, false);
        back.setGravity(Gravity.CENTER);
        back.setBackground(shape(SURFACE, 0xFF3467A0, 12));
        FrameLayout.LayoutParams backLp = new FrameLayout.LayoutParams(dp(42), dp(42));
        backLp.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        backLp.leftMargin = dp(14);
        header.addView(back, backLp);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());

        TextView brand = text("▂▅▇  SpendWise", 18, WHITE, true);
        SpannableString styled = new SpannableString("▂▅▇  SpendWise");
        styled.setSpan(new ForegroundColorSpan(CYAN), 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        styled.setSpan(new ForegroundColorSpan(CYAN), 10, styled.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        brand.setText(styled);
        brand.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams brandLp = new FrameLayout.LayoutParams(-2, -1);
        brandLp.gravity = Gravity.CENTER;
        header.addView(brand, brandLp);
    }

    private boolean loadTotals() {
        final List<TransactionStore.Record> transactions;
        try {
            transactions = TransactionStore.getAll(this);
        } catch (RuntimeException error) {
            Toast.makeText(this, "Unable to load transactions", Toast.LENGTH_LONG).show();
            finish();
            return false;
        }
        earliestRecordedTimestamp = Long.MAX_VALUE;
        mainTotals.clear();
        subTotals.clear();
        selectedTotalPaisa = 0L;
        long start = periodStart();
        long end = periodEnd();
        long now = System.currentTimeMillis();
        for (TransactionStore.Record item : transactions) {
            earliestRecordedTimestamp = Math.min(earliestRecordedTimestamp, item.timestamp);
            if (item.isIncome != isIncome || item.timestamp < start || item.timestamp >= end
                    || item.timestamp > now) continue;
            try {
                selectedTotalPaisa = Math.addExact(selectedTotalPaisa, item.amountPaisa);
                mainTotals.put(item.categoryId, Math.addExact(
                        mainTotals.containsKey(item.categoryId) ? mainTotals.get(item.categoryId) : 0L,
                        item.amountPaisa));
                Map<String, Long> subtotals = subTotals.get(item.categoryId);
                if (subtotals == null) {
                    subtotals = new HashMap<>();
                    subTotals.put(item.categoryId, subtotals);
                }
                subtotals.put(item.subcategory, Math.addExact(
                        subtotals.containsKey(item.subcategory) ? subtotals.get(item.subcategory) : 0L,
                        item.amountPaisa));
            } catch (ArithmeticException overflow) {
                Toast.makeText(this, "Transaction totals exceed supported amount", Toast.LENGTH_LONG).show();
                finish();
                return false;
            }
        }
        return true;
    }

    private Calendar calendar(long timestamp) {
        Calendar c = Calendar.getInstance(DHAKA, Locale.ENGLISH);
        c.setTimeInMillis(timestamp);
        return c;
    }

    private Calendar selectedStartCalendar() {
        Calendar c = calendar(anchorMillis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (period == DashboardBindings.Period.MONTH) {
            c.set(Calendar.DAY_OF_MONTH, 1);
        } else {
            c.set(Calendar.DAY_OF_YEAR, 1);
        }
        return c;
    }

    private long periodStart() {
        return period == DashboardBindings.Period.TOTAL ? Long.MIN_VALUE
                : selectedStartCalendar().getTimeInMillis();
    }

    private long periodEnd() {
        if (period == DashboardBindings.Period.TOTAL) return Long.MAX_VALUE;
        Calendar next = selectedStartCalendar();
        next.add(period == DashboardBindings.Period.MONTH ? Calendar.MONTH : Calendar.YEAR, 1);
        return next.getTimeInMillis();
    }

    private String periodLabel() {
        if (period == DashboardBindings.Period.TOTAL) return "Total · All Time";
        Calendar c = selectedStartCalendar();
        SimpleDateFormat f = new SimpleDateFormat(
                period == DashboardBindings.Period.MONTH ? "MMMM yyyy" : "yyyy", Locale.ENGLISH);
        f.setTimeZone(DHAKA);
        String label = f.format(c.getTime());
        Calendar now = calendar(System.currentTimeMillis());
        boolean current = period == DashboardBindings.Period.MONTH
                ? now.get(Calendar.YEAR) == c.get(Calendar.YEAR)
                && now.get(Calendar.MONTH) == c.get(Calendar.MONTH)
                : now.get(Calendar.YEAR) == c.get(Calendar.YEAR);
        return (current ? (period == DashboardBindings.Period.MONTH ? "This Month · " : "This Year · ") : "")
                + label;
    }

    private void addHighestCard(LinearLayout parent) {
        long best = 0;
        String bestName = isIncome ? "No income yet" : "No expenses yet";
        for (CategoryCatalog.Category category : CategoryCatalog.getCategories(isIncome)) {
            long amount = getAmount(category.getId());
            if (amount > best) {
                best = amount;
                bestName = category.getName();
            }
        }
        LinearLayout card = horizontal();
        card.setPadding(dp(12), dp(13), dp(12), dp(13));
        card.setBackground(shape(isIncome ? 0xFF10382E : 0xFF421D30, accent(), 17));
        parent.addView(card, margins(-1, -2, 0, 12));

        TextView crown = text("♛", 31, 0xFFFACC15, true);
        crown.setGravity(Gravity.CENTER);
        card.addView(crown, new LinearLayout.LayoutParams(dp(42), -2));
        LinearLayout labels = vertical();
        labels.addView(text(isIncome ? "Highest Income Category" : "Highest Expense Category", 11, WHITE, false));
        TextView name = text(bestName, 15, WHITE, true);
        name.setMaxLines(2);
        labels.addView(name);
        card.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout values = vertical();
        values.setGravity(Gravity.RIGHT);
        TextView amount = text(money(best), 17, WHITE, true);
        amount.setGravity(Gravity.RIGHT);
        values.addView(amount);
        TextView share = text(percentage(best) + "% of total", 11, WHITE, true);
        share.setGravity(Gravity.CENTER);
        share.setPadding(dp(7), dp(3), dp(7), dp(3));
        share.setBackground(shape(accent(), 0, 25));
        values.addView(share, margins(-2, -2, 4, 0));
        card.addView(values);
    }

    private void addCategoryList(LinearLayout parent) {
        LinearLayout list = vertical();
        list.setPadding(dp(8), dp(6), dp(8), dp(6));
        list.setBackground(shape(0xFF091D3F, 0xFF28528C, 15));
        parent.addView(list);
        for (CategoryCatalog.Category category : CategoryCatalog.getCategories(isIncome)) {
            addCategoryRow(list, category);
        }
    }

    private void addCategoryRow(LinearLayout list, CategoryCatalog.Category category) {
        final String id = category.getId();
        boolean expanded = id.equals(expandedCategoryId);
        long amount = getAmount(id);
        LinearLayout row = horizontal();
        row.setPadding(dp(8), dp(9), dp(8), dp(9));
        row.setBackground(shape(expanded ? (isIncome ? 0xFF104C38 : 0xFF4C2133) : ROW,
                expanded ? accent() : 0xFF1E3A5F, 11));
        list.addView(row, margins(-1, -2, 3, 3));

        TextView icon = text(categoryIcon(category.getName()), 18, WHITE, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(shape(categoryIconColor(category.getName()), 0, 9));
        row.addView(icon, new LinearLayout.LayoutParams(dp(37), dp(37)));

        LinearLayout middle = vertical();
        LinearLayout.LayoutParams middleLp = new LinearLayout.LayoutParams(0, -2, 1);
        middleLp.leftMargin = dp(8);
        row.addView(middle, middleLp);
        LinearLayout heading = horizontal();
        middle.addView(heading);
        TextView title = text(category.getName(), 12, WHITE, true);
        title.setMaxLines(2);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = text(money(amount) + " (" + percentage(amount) + "%)", 11, WHITE, false);
        value.setGravity(Gravity.RIGHT);
        heading.addView(value);
        addProgress(middle, amount, 6, accent(), 4);

        TextView arrow = text(expanded ? "⌃" : "⌄", 19, WHITE, true);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(26), -1));
        row.setOnClickListener(v -> {
            int previousY = scroll == null ? 0 : scroll.getScrollY();
            expandedCategoryId = expanded ? null : id;
            render(false);
            if (scroll != null) scroll.post(() -> scroll.scrollTo(0, previousY));
        });
        if (expanded) addSubcategoryRows(list, category);
    }

    private void addSubcategoryRows(LinearLayout parent, CategoryCatalog.Category category) {
        Map<String, Long> amounts = subTotals.get(category.getId());
        LinearLayout expanded = vertical();
        expanded.setPadding(dp(14), dp(7), dp(8), dp(10));
        expanded.setBackground(shape(isIncome ? 0xFF071D28 : 0xFF1A1831, accent(), 12));
        parent.addView(expanded, margins(-1, -2, 0, 8));
        for (String name : category.getSubcategories()) {
            long value = amounts == null || !amounts.containsKey(name) ? 0L : amounts.get(name);
            LinearLayout item = vertical();
            expanded.addView(item, margins(-1, -2, 5, 4));
            LinearLayout header = horizontal();
            item.addView(header);
            TextView label = text("•  " + name, 11, WHITE, false);
            label.setMaxLines(2);
            header.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView sum = text(money(value) + " (" + percentage(value) + "%)", 10, WHITE, false);
            sum.setGravity(Gravity.RIGHT);
            header.addView(sum);
            addProgress(item, value, 4, accent(), 3);
        }
    }

    private void addProgress(LinearLayout parent, long amount, int height, int color, int top) {
        FrameLayout track = new FrameLayout(this);
        track.setBackground(shape(TRACK, 0, 20));
        parent.addView(track, margins(-1, dp(height), top, 0));
        int widthPercentage = percentage(amount);
        if (widthPercentage <= 0) return;
        View bar = new View(this);
        bar.setBackground(shape(color, 0, 20));
        track.addView(bar);
        track.post(() -> {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    Math.max(dp(2), (int) Math.round(track.getWidth() * widthPercentage / 100.0)),
                    dp(height));
            bar.setLayoutParams(params);
        });
    }

    private void addBottomFooter() {
        LinearLayout footer = vertical();
        footer.setPadding(dp(14), dp(9), dp(14), dp(13));
        footer.setBackgroundColor(NAVY);
        root.addView(footer);
        LinearLayout totalRow = horizontal();
        totalRow.setPadding(dp(10), dp(11), dp(10), dp(11));
        totalRow.setBackground(shape(SURFACE, 0xFF2860A0, 13));
        footer.addView(totalRow, margins(-1, -2, 0, 12));
        TextView label = text((isIncome ? "Total Income" : "Total Expense")
                + (period == DashboardBindings.Period.TOTAL ? " (Total)" :
                " (" + shortPeriodLabel() + ")"), 13, WHITE, true);
        totalRow.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        totalRow.addView(text(money(selectedTotalPaisa), 17, accent(), true));

        LinearLayout navigation = horizontal();
        footer.addView(navigation);
        boolean canPrev = canMove(-1);
        boolean canNext = canMove(1);
        TextView previous = navigationButton("‹  Previous", false, canPrev);
        TextView next = navigationButton("Next  ›", true, canNext);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(47), 1f);
        left.rightMargin = dp(7);
        navigation.addView(previous, left);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(47), 1f);
        right.leftMargin = dp(7);
        navigation.addView(next, right);
        previous.setOnClickListener(v -> navigate(-1));
        next.setOnClickListener(v -> navigate(1));
    }

    private TextView navigationButton(String label, boolean primary, boolean enabled) {
        TextView button = text(label, 15, enabled ? WHITE : MUTED, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(shape(primary && enabled ? 0xFF075EEA : SURFACE,
                enabled ? 0xFF4D99E4 : 0xFF2A4161, 14));
        button.setAlpha(enabled ? 1f : 0.55f);
        button.setEnabled(enabled);
        return button;
    }

    private boolean canMove(int direction) {
        if (period == DashboardBindings.Period.TOTAL || earliestRecordedTimestamp == Long.MAX_VALUE) {
            return false;
        }
        Calendar candidate = selectedStartCalendar();
        candidate.add(period == DashboardBindings.Period.MONTH ? Calendar.MONTH : Calendar.YEAR,
                direction);
        if (direction > 0) {
            Calendar now = calendar(System.currentTimeMillis());
            return candidate.getTimeInMillis() <= startOfCalendarPeriod(now);
        }
        Calendar earliest = calendar(earliestRecordedTimestamp);
        return candidate.getTimeInMillis() >= startOfCalendarPeriod(earliest);
    }

    private long startOfCalendarPeriod(Calendar date) {
        Calendar temp = calendar(date.getTimeInMillis());
        temp.set(Calendar.HOUR_OF_DAY, 0);
        temp.set(Calendar.MINUTE, 0);
        temp.set(Calendar.SECOND, 0);
        temp.set(Calendar.MILLISECOND, 0);
        if (period == DashboardBindings.Period.MONTH) temp.set(Calendar.DAY_OF_MONTH, 1);
        else temp.set(Calendar.DAY_OF_YEAR, 1);
        return temp.getTimeInMillis();
    }

    private void navigate(int direction) {
        if (!canMove(direction)) return;
        Calendar move = selectedStartCalendar();
        move.add(period == DashboardBindings.Period.MONTH ? Calendar.MONTH : Calendar.YEAR,
                direction);
        anchorMillis = move.getTimeInMillis();
        expandedCategoryId = null;
        render(false);
    }

    private String shortPeriodLabel() {
        Calendar c = selectedStartCalendar();
        Calendar now = calendar(System.currentTimeMillis());
        boolean current = period == DashboardBindings.Period.MONTH
                ? now.get(Calendar.YEAR) == c.get(Calendar.YEAR)
                && now.get(Calendar.MONTH) == c.get(Calendar.MONTH)
                : now.get(Calendar.YEAR) == c.get(Calendar.YEAR);
        if (current) return period == DashboardBindings.Period.MONTH ? "This Month" : "This Year";
        SimpleDateFormat formatter = new SimpleDateFormat(
                period == DashboardBindings.Period.MONTH ? "MMM yyyy" : "yyyy", Locale.ENGLISH);
        formatter.setTimeZone(DHAKA);
        return formatter.format(c.getTime());
    }

    private int percentage(long amount) {
        if (selectedTotalPaisa <= 0L || amount <= 0L) return 0;
        return (int) Math.min(100L, Math.round(100.0 * amount / selectedTotalPaisa));
    }

    private long getAmount(String categoryId) {
        Long amount = mainTotals.get(categoryId);
        return amount == null ? 0L : amount;
    }

    private String money(long paisa) {
        DecimalFormat formatter = new DecimalFormat("#,##0.00",
                DecimalFormatSymbols.getInstance(Locale.US));
        String number = formatter.format(BigDecimal.valueOf(paisa, 2).abs());
        return paisa < 0 ? "−৳" + number : "৳" + number;
    }

    private String categoryIcon(String name) {
        if (name.contains("Food")) return "♨";
        if (name.contains("Transport") || name.contains("Travel")) return "▣";
        if (name.contains("Shopping")) return "▤";
        if (name.contains("Salary")) return "▣";
        if (name.contains("Farming")) return "♣";
        if (name.contains("Business")) return "▣";
        if (name.contains("Education")) return "◇";
        if (name.contains("Health")) return "✚";
        if (name.contains("Assets")) return "⬡";
        if (name.contains("Rental") || name.contains("Housing")) return "⌂";
        return "●";
    }

    private int categoryIconColor(String name) {
        if (name.contains("Food")) return 0xFFD93D4C;
        if (name.contains("Transport") || name.contains("Travel")) return 0xFF4361D9;
        if (name.contains("Shopping")) return 0xFF9D4EDD;
        if (name.contains("Farming")) return 0xFF00A78C;
        if (name.contains("Bills")) return 0xFFFF9F1C;
        if (name.contains("Health")) return 0xFF00A6D6;
        if (name.contains("Assets")) return 0xFF6255DD;
        return isIncome ? 0xFF0A9764 : 0xFF3B82F6;
    }
}
