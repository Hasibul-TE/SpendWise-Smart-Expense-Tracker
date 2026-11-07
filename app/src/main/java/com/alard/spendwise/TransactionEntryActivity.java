package com.alard.spendwise;

import android.app.DatePickerDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Select Type -> Main Category -> Subcategory -> Amount -> Save. */
public class TransactionEntryActivity extends AppCompatActivity {

    private static final int NAVY = 0xFF071739;
    private static final int SURFACE = 0xFF0F274F;
    private static final int ROW = 0xFF102A56;
    private static final int WHITE = 0xFFF8FAFC;
    private static final int MUTED = 0xFF94A3B8;
    private static final int BLUE = 0xFF0AA8FF;
    private static final int RED = 0xFFEF4444;
    private static final int GREEN = 0xFF22C55E;
    private static final TimeZone DHAKA = TimeZone.getTimeZone("Asia/Dhaka");

    // 0: Type, 1: Main Category, 2: Subcategory, 3: Amount/Date/Description.
    private int step = 0;
    private boolean isIncome;
    private CategoryCatalog.Category selectedCategory;
    private String selectedSubcategory;
    private long chosenDate = System.currentTimeMillis();
    private String draftAmount = "";
    private String draftDescription = "";

    private LinearLayout root;
    private LinearLayout listRows;
    private ScrollView listScroll;
    private EditText amountInput;
    private EditText descriptionInput;
    private EditText searchInput;
    private boolean saving;
    // Non-null only when editing an existing record; never create a duplicate.
    private String editingTransactionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        root = vertical();
        root.setBackgroundColor(NAVY);
        setContentView(root);
        // Handle system Back gestures and the on-screen Back control consistently.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleStepBack();
            }
        });
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });
        if (savedInstanceState != null) {
            editingTransactionId = savedInstanceState.getString("editingTransactionId");
            step = savedInstanceState.getInt("step", 0);
            isIncome = savedInstanceState.getBoolean("income", false);
            String categoryId = savedInstanceState.getString("categoryId");
            selectedCategory = categoryId == null ? null
                    : CategoryCatalog.findCategory(categoryId, isIncome);
            selectedSubcategory = savedInstanceState.getString("subcategory");
            chosenDate = savedInstanceState.getLong("date", System.currentTimeMillis());
            draftAmount = savedInstanceState.getString("amount", "");
            draftDescription = savedInstanceState.getString("description", "");
            if (step >= 2 && selectedCategory == null) step = 1;
            if (step >= 3 && selectedSubcategory == null) step = 2;
        } else {
            editingTransactionId = getIntent().getStringExtra("transaction_id");
            if (editingTransactionId != null && !editingTransactionId.isEmpty()) {
                // Edit screen opens with the previously saved values.
                if (!loadTransactionForEditing()) return;
            } else {
                editingTransactionId = null;
            }
        }
        drawStep();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        captureForm();
        out.putString("editingTransactionId", editingTransactionId);
        out.putInt("step", step);
        out.putBoolean("income", isIncome);
        if (selectedCategory != null) out.putString("categoryId", selectedCategory.getId());
        out.putString("subcategory", selectedSubcategory);
        out.putLong("date", chosenDate);
        out.putString("amount", draftAmount);
        out.putString("description", draftDescription);
    }

    /** Load a saved transaction once and keep its stable ID for update(). */
    private boolean loadTransactionForEditing() {
        final TransactionStore.Record record;
        try {
            record = TransactionStore.findById(this, editingTransactionId);
        } catch (RuntimeException error) {
            Toast.makeText(this, "Unable to open transaction", Toast.LENGTH_LONG).show();
            finish();
            return false;
        }
        if (record == null) {
            Toast.makeText(this, "Transaction not found", Toast.LENGTH_LONG).show();
            finish();
            return false;
        }
        CategoryCatalog.Category category = CategoryCatalog.findCategory(
                record.categoryId, record.isIncome);
        if (category == null || !category.getSubcategories().contains(record.subcategory)) {
            Toast.makeText(this, "Saved category is no longer available", Toast.LENGTH_LONG).show();
            finish();
            return false;
        }
        isIncome = record.isIncome;
        selectedCategory = category;
        selectedSubcategory = record.subcategory;
        chosenDate = record.timestamp;
        draftAmount = BigDecimal.valueOf(record.amountPaisa, 2).toPlainString();
        draftDescription = record.note;
        step = 3;
        return true;
    }

    private int dp(float value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private int accent() {
        return isIncome ? GREEN : RED;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width < 0 ? width : dp(width),
                height < 0 ? height : dp(height));
    }

    private GradientDrawable shape(int fill, int border, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (border != Color.TRANSPARENT) d.setStroke(dp(1), border);
        return d;
    }

    private GradientDrawable gradient(int from, int to, int border, int radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{from, to});
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), border);
        return d;
    }

    private TextView text(String content, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(content);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private void hideKeyboard() {
        InputMethodManager input = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (input != null && getCurrentFocus() != null) {
            input.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    private void captureForm() {
        if (step == 3 && amountInput != null && descriptionInput != null) {
            draftAmount = amountInput.getText().toString();
            draftDescription = descriptionInput.getText().toString();
        }
    }

    private void drawStep() {
        root.removeAllViews();
        amountInput = null;
        descriptionInput = null;
        addTopBrand();
        switch (step) {
            case 0: drawType(); break;
            case 1: drawCategories(); break;
            case 2: drawSubcategories(); break;
            default: drawAmountForm(); break;
        }
    }

    /** Centered blue SpendWise bars and wordmark on every screen. */
    private void addTopBrand() {
        FrameLayout bar = new FrameLayout(this);
        root.addView(bar, lp(-1, 64));
        TextView back = text("‹", 35, WHITE, false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setBackground(shape(SURFACE, BLUE, 13));
        FrameLayout.LayoutParams backLp = new FrameLayout.LayoutParams(
                dp(42), dp(42), Gravity.START | Gravity.CENTER_VERTICAL);
        backLp.leftMargin = dp(17);
        bar.addView(back, backLp);
        back.setOnClickListener(v -> handleStepBack());

        LinearLayout brand = horizontal();
        brand.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams brandLp = new FrameLayout.LayoutParams(
                -2, -1, Gravity.CENTER);
        bar.addView(brand, brandLp);
        LinearLayout logo = horizontal();
        logo.setGravity(Gravity.BOTTOM);
        for (int i = 0; i < 3; i++) {
            View pillar = new View(this);
            pillar.setBackground(shape(BLUE, Color.TRANSPARENT, 3));
            LinearLayout.LayoutParams p = lp(5, 12 + i * 6);
            p.rightMargin = dp(3);
            logo.addView(pillar, p);
        }
        LinearLayout.LayoutParams logoLp = lp(-2, 26);
        logoLp.rightMargin = dp(7);
        brand.addView(logo, logoLp);
        brand.addView(text("Spend", 21, WHITE, true));
        brand.addView(text("Wise", 21, BLUE, true));
    }

    private LinearLayout heading(String title, String subtitle, int color) {
        LinearLayout group = vertical();
        group.setPadding(dp(18), dp(12), dp(18), dp(12));
        root.addView(group, lp(-1, -2));
        TextView name = text(title, 25, WHITE, true);
        name.setGravity(Gravity.CENTER);
        group.addView(name, lp(-1, -2));
        if (subtitle != null) {
            TextView description = text(subtitle, 13, color, false);
            description.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams p = lp(-1, -2);
            p.topMargin = dp(4);
            group.addView(description, p);
        }
        return group;
    }

    private void drawType() {
        heading("Add Transaction", "Choose transaction type", MUTED);
        LinearLayout content = vertical();
        content.setPadding(dp(18), dp(28), dp(18), 0);
        root.addView(content, lp(-1, -2));
        typeCard(content, true, "Add Income", "Add money you received", "e.g. salary, business, crop sales");
        typeCard(content, false, "Add Expense", "Add money you spent", "e.g. food, transport, farm costs");
        TextView decoration = text("৳", 76, BLUE, true);
        decoration.setGravity(Gravity.CENTER);
        decoration.setBackground(gradient(0xFF102F63, NAVY, BLUE, 28));
        LinearLayout.LayoutParams p = lp(-1, 165);
        p.topMargin = dp(50);
        content.addView(decoration, p);
    }

    private void typeCard(LinearLayout parent, boolean income, String title,
                          String subtitle, String examples) {
        int color = income ? GREEN : RED;
        LinearLayout row = horizontal();
        row.setPadding(dp(15), dp(18), dp(15), dp(18));
        row.setBackground(gradient(income ? 0xFF075A36 : 0xFF641B30,
                income ? 0xFF092B27 : 0xFF2D1424, color, 19));
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.bottomMargin = dp(18);
        parent.addView(row, p);
        TextView symbol = text(income ? "৳" : "↗", 32, WHITE, true);
        symbol.setGravity(Gravity.CENTER);
        symbol.setBackground(shape(income ? 0xFF059669 : 0xFFC5223D,
                Color.TRANSPARENT, 14));
        row.addView(symbol, lp(58, 62));
        LinearLayout labels = vertical();
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(0, -2, 1);
        detailLp.leftMargin = dp(14);
        row.addView(labels, detailLp);
        labels.addView(text(title, 20, WHITE, true));
        TextView line = text(subtitle, 13, WHITE, false);
        LinearLayout.LayoutParams lineLp = lp(-1, -2);
        lineLp.topMargin = dp(3);
        labels.addView(line, lineLp);
        labels.addView(text(examples, 11, MUTED, false));
        row.addView(text("›", 29, WHITE, true));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> {
            isIncome = income;
            selectedCategory = null;
            selectedSubcategory = null;
            step = 1;
            drawStep();
        });
    }

    private void drawCategories() {
        heading(isIncome ? "Add Income" : "Add Expense", "Select a category", accent());
        listArea(true);
        footer("Select a Main Category to see its Subcategories in the next step.");
    }

    private void drawSubcategories() {
        heading(isIncome ? "Add Income" : "Add Expense",
                "Subcategory of " + selectedCategory.getName(), accent());
        LinearLayout banner = horizontal();
        banner.setPadding(dp(12), dp(12), dp(12), dp(12));
        banner.setBackground(gradient(isIncome ? 0xFF075A36 : 0xFF641B30,
                SURFACE, accent(), 16));
        LinearLayout.LayoutParams bp = lp(-1, -2);
        bp.setMargins(dp(18), dp(3), dp(18), dp(8));
        root.addView(banner, bp);
        TextView symbol = text(categoryIcon(selectedCategory.getId()), 25, WHITE, true);
        symbol.setGravity(Gravity.CENTER);
        symbol.setBackground(shape(accent(), Color.TRANSPARENT, 12));
        banner.addView(symbol, lp(46, 46));
        LinearLayout info = vertical();
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, -2, 1);
        ip.leftMargin = dp(12);
        banner.addView(info, ip);
        info.addView(text(selectedCategory.getName(), 17, WHITE, true));
        info.addView(text("Select a subcategory for your " + (isIncome ? "income" : "expense"),
                12, MUTED, false));
        listArea(false);
        TextView next = action("Continue  ›");
        LinearLayout.LayoutParams p = lp(-1, 54);
        p.setMargins(dp(18), dp(8), dp(18), dp(16));
        root.addView(next, p);
        next.setOnClickListener(v -> {
            if (selectedSubcategory == null) {
                Toast.makeText(this, "Select one subcategory", Toast.LENGTH_SHORT).show();
                return;
            }
            hideKeyboard();
            step = 3;
            drawStep();
        });
    }

    private void listArea(boolean main) {
        LinearLayout container = vertical();
        container.setPadding(dp(18), dp(9), dp(18), 0);
        root.addView(container, new LinearLayout.LayoutParams(-1, 0, 1));
        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        searchInput.setTextSize(15);
        searchInput.setTextColor(WHITE);
        searchInput.setHintTextColor(MUTED);
        searchInput.setHint(main ? "⌕  Search categories..." : "⌕  Search subcategories...");
        searchInput.setPadding(dp(15), 0, dp(15), 0);
        searchInput.setBackground(shape(SURFACE, BLUE, 16));
        container.addView(searchInput, lp(-1, 47));

        listScroll = new ScrollView(this);
        listScroll.setVerticalScrollBarEnabled(true);
        LinearLayout.LayoutParams sc = new LinearLayout.LayoutParams(-1, 0, 1);
        sc.topMargin = dp(12);
        container.addView(listScroll, sc);
        listRows = vertical();
        listRows.setPadding(0, 0, 0, dp(12));
        listScroll.addView(listRows);
        populateRows("", main);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                populateRows(s.toString(), main);
                listScroll.scrollTo(0, 0);
            }
            @Override public void afterTextChanged(Editable e) { }
        });
    }

    private void populateRows(String query, boolean main) {
        listRows.removeAllViews();
        int matches = 0;
        String needle = query.toLowerCase(Locale.ROOT).trim();
        if (main) {
            List<CategoryCatalog.Category> categories = CategoryCatalog.getCategories(isIncome);
            for (CategoryCatalog.Category category : categories) {
                if (!category.getName().toLowerCase(Locale.ROOT).contains(needle)) continue;
                matches++;
                listRow(category.getName(), categoryIcon(category.getId()), false, () -> {
                    hideKeyboard();
                    selectedCategory = category;
                    selectedSubcategory = null;
                    step = 2;
                    drawStep();
                });
            }
        } else if (selectedCategory != null) {
            for (String subcategory : selectedCategory.getSubcategories()) {
                if (!subcategory.toLowerCase(Locale.ROOT).contains(needle)) continue;
                matches++;
                boolean picked = subcategory.equals(selectedSubcategory);
                listRow(subcategory, picked ? "◉" : "○", picked, () -> {
                    selectedSubcategory = subcategory;
                    int scrollY = listScroll.getScrollY();
                    populateRows(searchInput.getText().toString(), false);
                    listScroll.post(() -> listScroll.scrollTo(0, scrollY));
                });
            }
        }
        if (matches == 0) {
            TextView empty = text("No matching categories", 14, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(20), 0, dp(20));
            listRows.addView(empty);
        }
    }

    private void listRow(String name, String symbol, boolean selected, Runnable clicked) {
        LinearLayout row = horizontal();
        row.setPadding(dp(10), dp(6), dp(8), dp(6));
        row.setBackground(gradient(selected ? (isIncome ? 0xFF086A3D : 0xFF8B1C37)
                        : (isIncome ? 0xFF0A423A : 0xFF401A2B),
                ROW, selected ? accent() : 0xFF1E3A5F, 14));
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.bottomMargin = dp(7);
        listRows.addView(row, p);
        TextView glyph = text(symbol, 22, WHITE, true);
        glyph.setGravity(Gravity.CENTER);
        glyph.setBackground(shape(isIncome ? 0xFF059669 : 0xFFBE2840,
                Color.TRANSPARENT, 11));
        row.addView(glyph, lp(40, 40));
        View line = new View(this);
        line.setBackgroundColor(accent());
        LinearLayout.LayoutParams d = lp(2, 25);
        d.leftMargin = dp(9);
        d.rightMargin = dp(11);
        row.addView(line, d);
        TextView label = text(name, 14, WHITE, true);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, -2, 1);
        row.addView(label, l);
        TextView arrow = text(selected ? "✓" : "›", 26, WHITE, true);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, lp(26, 42));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> clicked.run());
    }

    private String categoryIcon(String id) {
        if (id.contains("food")) return "♨";
        if (id.contains("salary")) return "▣";
        if (id.contains("farming")) return "❀";
        if (id.contains("business")) return "▤";
        if (id.contains("transport")) return "▰";
        if (id.contains("shopping")) return "▢";
        if (id.contains("bills")) return "▧";
        if (id.contains("education")) return "✎";
        if (id.contains("health")) return "+";
        if (id.contains("assets")) return "◇";
        if (id.contains("rental") || id.contains("housing")) return "⌂";
        if (id.contains("finance") || id.contains("investments")) return "৳";
        if (id.contains("family") || id.contains("gifts")) return "♡";
        if (id.contains("freelanc")) return "⌘";
        return "•••";
    }

    private TextView action(String title) {
        TextView button = text(title, 18, WHITE, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(gradient(isIncome ? 0xFF16A34A : 0xFFEF4444,
                isIncome ? 0xFF047857 : 0xFF991B1B, accent(), 16));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private void footer(String message) {
        TextView line = text("ⓘ  " + message, 12, MUTED, false);
        line.setPadding(dp(12), dp(13), dp(12), dp(13));
        line.setBackground(shape(SURFACE, BLUE, 12));
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.setMargins(dp(18), dp(8), dp(18), dp(18));
        root.addView(line, p);
    }

    private void drawAmountForm() {
        heading(editingTransactionId == null ? "Add Transaction" : "Edit Transaction",
                isIncome ? "Income" : "Expense", accent());
        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(12), dp(18), dp(24));
        scroll.addView(form);

        LinearLayout category = vertical();
        category.setPadding(dp(16), dp(15), dp(16), dp(15));
        category.setBackground(gradient(isIncome ? 0xFF075A36 : 0xFF641B30,
                SURFACE, accent(), 16));
        form.addView(category, lp(-1, -2));
        category.addView(text(isIncome ? "INCOME" : "EXPENSE", 12, accent(), true));
        TextView selection = text(selectedCategory.getName() + "  ›  " + selectedSubcategory,
                16, WHITE, true);
        LinearLayout.LayoutParams sl = lp(-1, -2);
        sl.topMargin = dp(8);
        category.addView(selection, sl);
        TextView change = text("Change subcategory", 13, BLUE, true);
        LinearLayout.LayoutParams cp = lp(-1, -2);
        cp.topMargin = dp(10);
        category.addView(change, cp);
        change.setOnClickListener(v -> { captureForm(); step = 2; drawStep(); });
        if (editingTransactionId != null) {
            // Editing can reassign the record without creating a second transaction.
            TextView changeCategory = text("Change main category", 13, BLUE, true);
            LinearLayout.LayoutParams cc = lp(-1, -2);
            cc.topMargin = dp(9);
            category.addView(changeCategory, cc);
            changeCategory.setOnClickListener(v -> { captureForm(); step = 1; drawStep(); });
            TextView changeType = text("Change income / expense type", 13, BLUE, true);
            LinearLayout.LayoutParams ct = lp(-1, -2);
            ct.topMargin = dp(9);
            category.addView(changeType, ct);
            changeType.setOnClickListener(v -> { captureForm(); step = 0; drawStep(); });
        }

        TextView amountLabel = text("Amount (৳) *", 14, WHITE, true);
        LinearLayout.LayoutParams al = lp(-1, -2);
        al.topMargin = dp(23);
        form.addView(amountLabel, al);
        amountInput = input("0.00", true);
        LinearLayout.LayoutParams a = lp(-1, 59);
        a.topMargin = dp(8);
        form.addView(amountInput, a);
        amountInput.setText(draftAmount);
        amountInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(17)});

        TextView dateLabel = text("Date *", 14, WHITE, true);
        LinearLayout.LayoutParams dl = lp(-1, -2);
        dl.topMargin = dp(22);
        form.addView(dateLabel, dl);
        TextView date = text("▣   " + formatDate(chosenDate) + "        ▾", 16, WHITE, true);
        date.setGravity(Gravity.CENTER_VERTICAL);
        date.setPadding(dp(16), 0, dp(16), 0);
        date.setBackground(shape(SURFACE, BLUE, 12));
        LinearLayout.LayoutParams dateLp = lp(-1, 54);
        dateLp.topMargin = dp(8);
        form.addView(date, dateLp);
        date.setOnClickListener(v -> chooseDate(date));

        TextView descriptionLabel = text("Description (Optional)", 14, WHITE, true);
        LinearLayout.LayoutParams nl = lp(-1, -2);
        nl.topMargin = dp(22);
        form.addView(descriptionLabel, nl);
        descriptionInput = input("Add details about this transaction", false);
        descriptionInput.setSingleLine(false);
        descriptionInput.setMinLines(2);
        descriptionInput.setMaxLines(4);
        descriptionInput.setGravity(Gravity.TOP);
        descriptionInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout.LayoutParams n = lp(-1, -2);
        n.topMargin = dp(8);
        form.addView(descriptionInput, n);
        descriptionInput.setText(draftDescription);
        descriptionInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});

        if (!isIncome && "Credit Card Payments".equals(selectedSubcategory)) {
            TextView warning = text("Record a credit-card purchase only once. If you already added the original purchase as an expense, don't add the bill payment again.",
                    12, MUTED, false);
            LinearLayout.LayoutParams wp = lp(-1, -2);
            wp.topMargin = dp(12);
            form.addView(warning, wp);
        }
        TextView save = action(editingTransactionId == null
                ? "✓  Save Transaction" : "✓  Update Transaction");
        LinearLayout.LayoutParams sp = lp(-1, 60);
        sp.topMargin = dp(30);
        form.addView(save, sp);
        save.setOnClickListener(v -> save());
        TextView status = text("Transactions are stored locally for now; Firebase Database sync will be added later.",
                11, MUTED, false);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams st = lp(-1, -2);
        st.topMargin = dp(12);
        form.addView(status, st);
    }

    private EditText input(String hint, boolean number) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setTextSize(number ? 25 : 15);
        edit.setTextColor(WHITE);
        edit.setHintTextColor(MUTED);
        edit.setHint(hint);
        edit.setPadding(dp(15), dp(12), dp(15), dp(12));
        edit.setBackground(shape(SURFACE, BLUE, 12));
        edit.setInputType(number ? InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL : InputType.TYPE_CLASS_TEXT);
        return edit;
    }

    private String formatDate(long time) {
        SimpleDateFormat f = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);
        f.setTimeZone(DHAKA);
        return f.format(time);
    }

    private void chooseDate(TextView display) {
        Calendar c = Calendar.getInstance(DHAKA);
        c.setTimeInMillis(chosenDate);
        DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar chosen = Calendar.getInstance(DHAKA);
            chosen.set(year, month, day, 12, 0, 0);
            chosen.set(Calendar.MILLISECOND, 0);
            // Use now for the present date if today's noon has not passed.
            chosenDate = Math.min(chosen.getTimeInMillis(), System.currentTimeMillis());

            display.setText(
                    getString(
                            R.string.transaction_selected_date,
                            formatDate(chosenDate)
                    )
            );

        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        picker.getDatePicker().setMaxDate(System.currentTimeMillis());
        picker.show();
    }

    private void save() {
        if (saving) return;
        if (selectedCategory == null || selectedSubcategory == null
                || !selectedCategory.getSubcategories().contains(selectedSubcategory)) {
            Toast.makeText(this, "Select exactly one category and subcategory", Toast.LENGTH_SHORT).show();
            return;
        }
        String raw = amountInput.getText().toString().trim();
        if (!raw.matches("[0-9]+(\\.[0-9]{1,2})?")) {
            amountInput.setError("Enter a valid amount (up to 2 decimals)");
            amountInput.requestFocus();
            return;
        }
        long paisa;
        try {
            paisa = new BigDecimal(raw).movePointRight(2)
                    .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException ex) {
            amountInput.setError("Amount is too large");
            return;
        }
        if (paisa <= 0) {
            amountInput.setError("Amount must be greater than zero");
            return;
        }
        if (chosenDate <= 0 || chosenDate > System.currentTimeMillis()) {
            Toast.makeText(this, "Select a valid date", Toast.LENGTH_SHORT).show();
            return;
        }
        saving = true;
        boolean ok;
        try {
            if (editingTransactionId == null) {
                ok = TransactionStore.add(this, isIncome, selectedCategory,
                        selectedSubcategory, paisa, chosenDate,
                        descriptionInput.getText().toString());
            } else {
                // Update the same ID; recalculation reads the replaced record once.
                ok = TransactionStore.update(this, editingTransactionId, isIncome,
                        selectedCategory, selectedSubcategory, paisa, chosenDate,
                        descriptionInput.getText().toString());
            }
        } catch (IllegalStateException ex) {
            ok = false;
        } finally {
            saving = false;
        }
        if (ok) {
            hideKeyboard();
            Toast.makeText(this,
                    editingTransactionId == null ? "Transaction saved" : "Transaction updated",
                    Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        } else {
            Toast.makeText(this, "Unable to save. Check login and try again.", Toast.LENGTH_LONG).show();
        }
    }

    /** Navigate backwards through the entry steps without leaving a duplicate record. */
    private void handleStepBack() {
        hideKeyboard();
        if (editingTransactionId != null) {
            // Back from the Edit form returns to Transaction History / Details.
            if (step == 3) {
                finish();
            } else if (selectedCategory != null && selectedSubcategory != null) {
                step = 3;
                drawStep();
            } else if (step > 0) {
                step--;
                drawStep();
            } else {
                finish();
            }
        } else if (step > 0) {
            captureForm();
            step--;
            drawStep();
        } else {
            // The type-selection step is the root of this Activity.
            finish();
        }
    }
}
