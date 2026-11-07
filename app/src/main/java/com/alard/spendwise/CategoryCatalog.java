
package com.alard.spendwise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Fixed, ordered Income and Expense categories for SpendWise. */
public final class CategoryCatalog {

    private CategoryCatalog() {
        // Utility class.
    }

    public static final class Category {
        private final String id;
        private final String name;
        private final List<String> subcategories;

        private Category(String id, String name, String... subcategories) {
            this.id = id;
            this.name = name;
            this.subcategories = Collections.unmodifiableList(
                    new ArrayList<>(Arrays.asList(subcategories)));
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public List<String> getSubcategories() {
            return subcategories;
        }
    }

    private static final List<Category> EXPENSE_CATEGORIES = createExpenseCategories();
    private static final List<Category> INCOME_CATEGORIES = createIncomeCategories();

    public static List<Category> getExpenseCategories() {
        return EXPENSE_CATEGORIES;
    }

    public static List<Category> getIncomeCategories() {
        return INCOME_CATEGORIES;
    }

    public static List<Category> getCategories(boolean isIncome) {
        return isIncome ? INCOME_CATEGORIES : EXPENSE_CATEGORIES;
    }

    public static Category findCategory(String id, boolean isIncome) {
        for (Category category : getCategories(isIncome)) {
            if (category.getId().equals(id)) {
                return category;
            }
        }
        return null;
    }

    public static int countSubcategories(boolean isIncome) {
        int count = 0;
        for (Category category : getCategories(isIncome)) {
            count += category.getSubcategories().size();
        }
        return count;
    }

    private static List<Category> createExpenseCategories() {
        List<Category> categories = new ArrayList<>();

        categories.add(new Category("expense_food", "Food",
                "Groceries", "Restaurants & Dining", "Snacks & Drinks",
                "Food Delivery", "Other Food Expenses"));

        categories.add(new Category("expense_transport_travel", "Transport & Travel",
                "Public Transport", "Rickshaw, CNG & Ride Sharing", "Fuel",
                "Vehicle Maintenance", "Travel Tickets", "Hotels & Accommodation",
                "Tours & Activities", "Other Transport & Travel"));

        categories.add(new Category("expense_shopping_personal_care", "Shopping & Personal Care",
                "Clothing & Footwear", "Fashion & Accessories", "Beauty & Grooming",
                "Skincare & Cosmetics", "Personal Hygiene", "Household Shopping",
                "Other Shopping"));

        categories.add(new Category("expense_bills_utilities", "Bills & Utilities",
                "Electricity Bill", "Gas Bill", "Water Bill",
                "Mobile Recharge & Packages", "Internet & Broadband", "TV & Cable Bills",
                "Other Utility Bills"));

        categories.add(new Category("expense_rent_housing", "Rent / Housing",
                "House / Apartment Rent", "Hostel / Mess Rent",
                "Home Maintenance & Repairs", "Building Service Charges",
                "House Shifting", "Other Housing Expenses"));

        categories.add(new Category("expense_education", "Education",
                "Tuition & Course Fees", "Books & Study Materials",
                "Exam & Registration Fees", "Coaching & Training",
                "Educational Subscriptions", "Other Education Expenses"));

        categories.add(new Category("expense_health_medical", "Health / Medical",
                "Doctor & Consultation", "Medicines & Pharmacy",
                "Diagnostic Tests & Lab", "Hospital & Surgery",
                "Dental & Eye Care", "Therapy & Rehabilitation",
                "Other Medical Expenses"));

        categories.add(new Category("expense_entertainment", "Entertainment",
                "Movies & Shows", "Gaming", "Streaming & Media Subscriptions",
                "Events & Concerts", "Hobbies & Recreation",
                "Other Entertainment Expenses"));

        categories.add(new Category("expense_family_donations", "Family Support & Donations",
                "Parents Support", "Children & Childcare", "Elderly Care",
                "Relatives Support", "Other Family Support", "Charitable Donations",
                "Zakat & Sadaqah", "Religious & Community Contributions",
                "Educational Donations", "Emergency & Disaster Relief",
                "Other Donations"));

        categories.add(new Category("expense_farming_livestock", "Farming & Livestock",
                "Seeds, Crops & Plants", "Chemical Fertilizers & Pest Control",
                "Organic Fertilizers", "Organic Fertilizer Production Costs",
                "Land Rent / Lease",
                "Vehicle & Machinery Rent", "Transport & Storage", "Farming Labor",
                "Irrigation & Water", "Livestock Purchase", "Animal Feed & Care",
                "Fish Fry & Fingerlings Purchase", "Fish Feed & Care",
                "Farm Equipment Maintenance & Repair", "Other Farming Expenses"));

        categories.add(new Category("expense_business_professional", "Business & Professional Expenses",
                "Raw Materials & Inventory", "Office & Workspace Expenses",
                "Employee Salaries & Wages", "Business Rent",
                "Marketing & Advertising", "Professional Services",
                "Software & Subscriptions", "Business Transport & Delivery",
                "Equipment Maintenance & Repair", "Other Business Expenses"));

        categories.add(new Category("expense_finance_obligations", "Finance & Obligations",
                "Bank Loan Payments", "Personal Loan & Debt Repayment",
                "Credit Card Payments", "Loan Interest & Charges",
                "Life Insurance", "Health Insurance", "Vehicle & Property Insurance",
                "Income Tax", "Land & Property Tax", "Government Fees & Licenses",
                "Bank & Financial Service Charges", "Other Financial Obligations"));

        categories.add(new Category("expense_assets_major_purchases", "Assets & Major Purchases",
                "Land Purchase", "House / Flat / Apartment Purchase",
                "Vehicle Purchase", "Farm Machinery & Equipment Purchase",
                "Business Machinery & Equipment Purchase",
                "Electronics & Major Appliances", "Other Asset Purchases"));

        categories.add(new Category("expense_other", "Other Expenses",
                "Unexpected Expenses", "Miscellaneous Expenses",
                "Other Unclassified Expenses"));

        return Collections.unmodifiableList(categories);
    }

    private static List<Category> createIncomeCategories() {
        List<Category> categories = new ArrayList<>();

        categories.add(new Category("income_salary_employment", "Salary & Employment",
                "Monthly Salary", "Bonus & Incentives", "Overtime Pay",
                "Allowances", "Commission & Performance Pay",
                "Pension & Retirement Benefits", "Other Employment Income"));

        categories.add(new Category("income_farming_livestock_sales", "Farming & Livestock Sales",
                "Rice & Grain Sales", "Vegetables Sales", "Fruits Sales",
                "Pulses & Oilseeds Sales", "Spices & Other Crops Sales",
                "Livestock Sales", "Poultry & Egg Sales", "Milk & Dairy Sales",
                "Fish & Aquaculture Sales", "Seeds, Seedlings & Plant Sales",
                "Organic Fertilizer Sales", "Other Farming Income"));

        categories.add(new Category("income_business", "Business Income",
                "Shop & Retail Sales", "Wholesale & Trading Sales",
                "Online / E-commerce Sales", "Manufacturing & Product Sales",
                "Business Service Fees", "Other Business Income"));

        categories.add(new Category("income_freelancing_services", "Freelancing & Services",
                "Freelance Project Payments", "Software & IT Services",
                "Design & Content Creation", "Tuition & Teaching",
                "Consulting & Professional Services", "Delivery & Gig Work",
                "Other Service Income"));

        categories.add(new Category("income_rental", "Rental Income",
                "House / Apartment Rent", "Land Rent / Lease Income",
                "Commercial Property Rent", "Vehicle Rental Income",
                "Machinery & Equipment Rental", "Other Rental Income"));

        categories.add(new Category("income_investments_returns", "Investments & Returns",
                "Bank Interest & Profit", "Stock & Share Dividends",
                "Mutual Fund Returns", "Savings Scheme Returns",
                "Other Investment Returns"));

        categories.add(new Category("income_assets_major_sales", "Assets & Major Sales",
                "Land Sales", "House / Flat / Apartment Sales", "Vehicle Sales",
                "Farm Machinery & Equipment Sales",
                "Business Machinery & Equipment Sales",
                "Electronics & Major Appliances Sales", "Other Asset Sales"));

        categories.add(new Category("income_gifts_financial_receipts", "Gifts & Financial Receipts",
                "Gifts Received", "Family Support Received", "Loan Received",
                "Debt Repayment Received", "Refunds & Reimbursements",
                "Other Financial Receipts"));

        categories.add(new Category("income_other", "Other Income",
                "Scholarships & Stipends", "Prizes & Rewards",
                "Tips & Occasional Earnings", "Other Unclassified Income"));

        return Collections.unmodifiableList(categories);
    }
}
