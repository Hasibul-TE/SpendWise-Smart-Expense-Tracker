package com.alard.spendwise;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Transaction history with search, filters, details, and confirmed deletion. */
public class TransactionHistoryActivity extends AppCompatActivity {

    private static final int NAVY = 0xFF071739;
    private static final int SURFACE = 0xFF0F274F;
    private static final int ROW_BG = 0xFF102A56;
    private static final int WHITE = 0xFFF8FAFC;
    private static final int MUTED = 0xFF94A3B8;
    private static final int BLUE = 0xFF0AA8FF;
    private static final int GREEN = 0xFF22C55E;
    private static final int RED = 0xFFEF4444;
    private static final TimeZone DHAKA = TimeZone.getTimeZone("Asia/Dhaka");

    // 0 = All, 1 = Income, 2 = Expense.
    private int filter = 0;
    private String search = "";
    private String openedRecordId = null;
    private LinearLayout root;
    private LinearLayout results;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        root = vertical();
        root.setBackgroundColor(NAVY);
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (openedRecordId != null) {
                    showHistory();
                } else {
                    finish();
                }
            }
        });
        if (savedInstanceState != null) {
            filter = savedInstanceState.getInt("filter", 0);
            search = savedInstanceState.getString("search", "");
            openedRecordId = savedInstanceState.getString("openedRecordId");
        }
        if (openedRecordId == null) showHistory();
        else showDetails(openedRecordId);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("filter", filter);
        state.putString("search", search);
        state.putString("openedRecordId", openedRecordId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (root == null) return;
        if (openedRecordId != null) showDetails(openedRecordId);
        else if (results != null) renderRows();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout vertical() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private LinearLayout horizontal() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private GradientDrawable background(int fill, int border, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (border != Color.TRANSPARENT) d.setStroke(dp(1), border);
        return d;
    }

    private TextView text(String content, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(content);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private void topBar() {
        FrameLayout header = new FrameLayout(this);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(62)));

        TextView back = text("‹", 34, WHITE, false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setBackground(background(SURFACE, BLUE, 12));
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(
                dp(42), dp(42), Gravity.START | Gravity.CENTER_VERTICAL);
        backParams.leftMargin = dp(17);
        header.addView(back, backParams);
        back.setOnClickListener(v -> {
            if (openedRecordId == null) finish();
            else showHistory();
        });

        LinearLayout brand = horizontal();
        brand.setGravity(Gravity.CENTER);
        header.addView(brand, new FrameLayout.LayoutParams(
                -2, -1, Gravity.CENTER));
        LinearLayout bars = horizontal();
        bars.setGravity(Gravity.BOTTOM);
        for (int i = 0; i < 3; i++) {
            View pillar = new View(this);
            pillar.setBackground(background(BLUE, Color.TRANSPARENT, 2));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(5), dp(12 + i * 6));
            p.rightMargin = dp(3);
            bars.addView(pillar, p);
        }
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(-2, dp(26));
        logoParams.rightMargin = dp(7);
        brand.addView(bars, logoParams);
        brand.addView(text("Spend", 20, WHITE, true));
        brand.addView(text("Wise", 20, BLUE, true));
    }

    private void title(String name) {
        TextView heading = text(name, 26, WHITE, true);
        heading.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(16), dp(7), dp(16), dp(18));
        root.addView(heading, p);
    }

    private TextView pill(String label, boolean active, int color) {
        TextView chip = text(label, 14, active ? WHITE : MUTED, active);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(background(active ? color : ROW_BG,
                active ? color : 0xFF1E3A5F, 18));
        return chip;
    }

    private void showHistory() {
        openedRecordId = null;
        root.removeAllViews();
        topBar();
        title("Transactions");

        LinearLayout tabs = horizontal();
        tabs.setPadding(dp(16), 0, dp(16), 0);
        root.addView(tabs, new LinearLayout.LayoutParams(-1, dp(45)));
        String[] labels = {"All", "Income", "Expense"};
        for (int i = 0; i < labels.length; i++) {
            final int selectedFilter = i;
            int color = i == 1 ? GREEN : (i == 2 ? RED : BLUE);
            TextView tab = pill(labels[i], filter == i, color);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1);
            p.setMargins(dp(3), 0, dp(3), 0);
            tabs.addView(tab, p);
            tab.setOnClickListener(v -> {
                filter = selectedFilter;
                for (int j = 0; j < tabs.getChildCount(); j++) {
                    TextView item = (TextView) tabs.getChildAt(j);
                    boolean active = j == filter;
                    int c = j == 1 ? GREEN : (j == 2 ? RED : BLUE);
                    item.setBackground(background(active ? c : ROW_BG,
                            active ? c : 0xFF1E3A5F, 18));
                    item.setTextColor(active ? WHITE : MUTED);
                    item.setTypeface(Typeface.DEFAULT,
                            active ? Typeface.BOLD : Typeface.NORMAL);
                }
                renderRows();
            });
        }

        EditText searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setTextSize(14);
        searchBox.setTextColor(WHITE);
        searchBox.setHintTextColor(MUTED);
        searchBox.setHint("⌕  Search transactions...");
        searchBox.setPadding(dp(14), 0, dp(14), 0);
        searchBox.setBackground(background(SURFACE, 0xFF315985, 14));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(51));
        searchParams.setMargins(dp(18), dp(14), dp(18), dp(14));
        root.addView(searchBox, searchParams);
        searchBox.setText(search);
        searchBox.setSelection(searchBox.length());
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                search = s.toString();
                renderRows();
            }
            @Override public void afterTextChanged(Editable editable) {}
        });

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        results = vertical();
        results.setPadding(dp(16), 0, dp(16), dp(24));
        scroll.addView(results);
        renderRows();
    }

    private void renderRows() {
        if (results == null || openedRecordId != null) return;
        results.removeAllViews();
        List<TransactionStore.Record> records;
        try {
            records = TransactionStore.getAll(this);
        } catch (RuntimeException ex) {
            results.addView(text("Unable to load transactions. Sign in and try again.",
                    14, RED, false));
            return;
        }
        int visible = 0;
        String previousDay = null;
        String query = search.trim().toLowerCase(Locale.ROOT);
        for (TransactionStore.Record record : records) {
            if ((filter == 1 && !record.isIncome) || (filter == 2 && record.isIncome)) {
                continue;
            }
            String searchable = (record.categoryName + " " + record.subcategory + " " + record.note)
                    .toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !searchable.contains(query)) continue;
            String day = date(record.timestamp, "EEE, dd MMM yyyy");
            if (!day.equals(previousDay)) {
                TextView dayTitle = text(day, 13, MUTED, true);
                LinearLayout.LayoutParams d = new LinearLayout.LayoutParams(-1, -2);
                d.setMargins(dp(7), dp(19), 0, dp(8));
                results.addView(dayTitle, d);
                previousDay = day;
            }
            results.addView(transactionRow(record));
            visible++;
        }
        if (visible == 0) {
            TextView empty = text("No matching transactions\nAdd a transaction or change your search.",
                    14, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(10), dp(60), dp(10), dp(25));
            results.addView(empty);
        }
    }

    private LinearLayout transactionRow(TransactionStore.Record record) {
        LinearLayout row = horizontal();
        row.setPadding(dp(12), dp(12), dp(8), dp(12));
        row.setBackground(background(SURFACE, 0xFF1E3A5F, 15));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.bottomMargin = dp(8);
        row.setTag(record.id);
        row.setOnClickListener(v -> showDetails(record.id));

        int accent = record.isIncome ? GREEN : RED;
        TextView icon = text(record.isIncome ? "↓" : "↑", 22, WHITE, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(background(record.isIncome ? 0xFF086044 : 0xFF8B2635,
                Color.TRANSPARENT, 12));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        iconParams.rightMargin = dp(10);
        row.addView(icon, iconParams);

        LinearLayout names = vertical();
        LinearLayout.LayoutParams namesParams = new LinearLayout.LayoutParams(0, -2, 1);
        row.addView(names, namesParams);
        String name = record.note.trim().isEmpty() ? record.subcategory : record.note;
        TextView primary = text(name, 14, WHITE, true);
        primary.setSingleLine(true);
        primary.setEllipsize(android.text.TextUtils.TruncateAt.END);
        names.addView(primary);
        TextView secondary = text(record.categoryName + " · " + record.subcategory,
                11, MUTED, false);
        secondary.setSingleLine(true);
        secondary.setEllipsize(android.text.TextUtils.TruncateAt.END);
        names.addView(secondary);

        LinearLayout right = vertical();
        right.setGravity(Gravity.END);
        row.addView(right);
        TextView amount = text((record.isIncome ? "+" : "−") + money(record.amountPaisa),
                14, accent, true);
        right.addView(amount);
        TextView more = text("⋮", 24, MUTED, true);
        more.setGravity(Gravity.RIGHT);
        more.setPadding(dp(12), dp(2), dp(5), dp(2));
        right.addView(more);
        more.setContentDescription("Transaction options");
        more.setOnClickListener(v -> openMenu(more, record));
        return row;
    }

    private void openMenu(View anchor, TransactionStore.Record record) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("View Details");
        menu.getMenu().add("Edit");
        menu.getMenu().add("Delete");
        menu.setOnMenuItemClickListener(item -> {
            String action = item.getTitle().toString();
            if ("View Details".equals(action)) showDetails(record.id);
            else if ("Edit".equals(action)) editRecord(record.id);
            else deleteConfirm(record.id);
            return true;
        });
        menu.show();
    }

    private void showDetails(String id) {
        TransactionStore.Record record;
        try {
            record = TransactionStore.findById(this, id);
        } catch (RuntimeException ex) {
            Toast.makeText(this, "Unable to open transaction", Toast.LENGTH_LONG).show();
            showHistory();
            return;
        }
        if (record == null) {
            Toast.makeText(this, "Transaction no longer exists", Toast.LENGTH_SHORT).show();
            showHistory();
            return;
        }
        openedRecordId = id;
        results = null;
        root.removeAllViews();
        topBar();
        title("Transaction Details");

        int accent = record.isIncome ? GREEN : RED;
        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout content = vertical();
        content.setPadding(dp(17), 0, dp(17), dp(15));
        scroll.addView(content);

        LinearLayout summary = vertical();
        summary.setGravity(Gravity.CENTER);
        summary.setPadding(dp(16), dp(25), dp(16), dp(25));
        summary.setBackground(background(record.isIncome ? 0xFF103C32 : 0xFF452033,
                accent, 16));
        content.addView(summary, new LinearLayout.LayoutParams(-1, -2));
        TextView summaryName = text(record.subcategory, 17, WHITE, true);
        summaryName.setGravity(Gravity.CENTER);
        summary.addView(summaryName);
        TextView summaryCategory = text(record.categoryName, 12, MUTED, false);
        summaryCategory.setGravity(Gravity.CENTER);
        summary.addView(summaryCategory);
        TextView amount = text((record.isIncome ? "+" : "−") + money(record.amountPaisa),
                30, accent, true);
        amount.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams amountParams = new LinearLayout.LayoutParams(-1, -2);
        amountParams.topMargin = dp(12);
        summary.addView(amount, amountParams);

        LinearLayout info = vertical();
        info.setPadding(dp(16), dp(18), dp(16), dp(18));
        info.setBackground(background(SURFACE, 0xFF315985, 15));
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(-1, -2);
        infoParams.topMargin = dp(20);
        content.addView(info, infoParams);
        detailRow(info, "Date", date(record.timestamp, "dd MMM yyyy"));
        detailRow(info, "Type", record.isIncome ? "Income" : "Expense");
        detailRow(info, "Category", record.categoryName);
        detailRow(info, "Subcategory", record.subcategory);
        detailRow(info, "Description", record.note.trim().isEmpty() ? "—" : record.note);

        LinearLayout actions = horizontal();
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, dp(52));
        actionsParams.topMargin = dp(26);
        content.addView(actions, actionsParams);
        TextView edit = pill("✎  Edit", true, BLUE);
        TextView delete = pill("▤  Delete", true, RED);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -1, 1);
        left.rightMargin = dp(8);
        actions.addView(edit, left);
        actions.addView(delete, new LinearLayout.LayoutParams(0, -1, 1));
        edit.setOnClickListener(v -> editRecord(id));
        delete.setOnClickListener(v -> deleteConfirm(id));
    }

    private void detailRow(LinearLayout parent, String label, String value) {
        LinearLayout row = horizontal();
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(13);
        parent.addView(row, p);
        TextView l = text(label, 13, MUTED, false);
        row.addView(l, new LinearLayout.LayoutParams(0, -2, 1));
        TextView v = text(value, 13, WHITE, true);
        v.setGravity(Gravity.RIGHT);
        row.addView(v, new LinearLayout.LayoutParams(0, -2, 2));
    }

    /** Open Edit Mode for an existing transaction. */
    private void editRecord(String id) {
        if (id == null || id.isEmpty()) {
            Toast.makeText(this, "Invalid transaction", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(
                TransactionHistoryActivity.this,
                TransactionEntryActivity.class
        );

        // Pass the existing transaction ID to enable Edit Mode.
        intent.putExtra("transaction_id", id);

        startActivity(intent);
    }


    private void deleteConfirm(String id) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Delete Transaction")
                .setMessage("Are you sure you want to delete this transaction?")
                .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                .setPositiveButton("Delete", (d, which) -> {
                    try {
                        boolean removed = TransactionStore.delete(this, id);
                        if (removed) {
                            Toast.makeText(this, "Transaction deleted", Toast.LENGTH_SHORT).show();
                            showHistory();
                        } else {
                            Toast.makeText(this, "Could not delete transaction", Toast.LENGTH_LONG).show();
                        }
                    } catch (RuntimeException ex) {
                        Toast.makeText(this, "Could not delete transaction", Toast.LENGTH_LONG).show();
                    }
                })
                .create();
        dialog.setOnShowListener(v -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(RED);
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(background(NAVY, RED, 18));
            }
        });
        dialog.show();
    }

    private String date(long timestamp, String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ENGLISH);
        format.setTimeZone(DHAKA);
        return format.format(timestamp);
    }

    private String money(long paisa) {
        DecimalFormat decimal = new DecimalFormat("#,##0.00",
                DecimalFormatSymbols.getInstance(Locale.US));
        return "৳" + decimal.format(BigDecimal.valueOf(paisa, 2));
    }
}
