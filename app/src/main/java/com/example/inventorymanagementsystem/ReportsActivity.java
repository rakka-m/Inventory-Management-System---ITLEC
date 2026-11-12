package com.example.inventorymanagementsystem;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.Locale;

public class ReportsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String role = getIntent().getStringExtra("role");
        if (role == null || role.equals("staff")) { finish(); return; }
        setContentView(R.layout.activity_reports);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        DBHelper db = new DBHelper(this);
        List<DBHelper.Product> all = db.getProducts("", "All");
        StringBuilder low = new StringBuilder();
        int units = 0;
        double value = 0;
        for (DBHelper.Product p : all) {
            units += p.qty;
            value += p.qty * p.price;
            if (p.qty <= p.min)
                low.append("• ").append(p.name).append(" — ").append(p.qty).append(" left (min ")
                        .append(p.min).append(") [").append(p.status()).append("]\n");
        }
        ((TextView) findViewById(R.id.tvLow)).setText(low.length() == 0 ? "All products are well stocked." : low.toString());
        ((TextView) findViewById(R.id.tvSummary)).setText("Total products: " + all.size() + "\nTotal units in stock: " + units
                + "\nInventory value: ₱" + String.format(Locale.US, "%.2f", value));
        double[] s = db.salesSummary();
        ((TextView) findViewById(R.id.tvSales)).setText("Transactions: " + (int) s[0] + "\nUnits sold: " + (int) s[1]
                + "\nRevenue: ₱" + String.format(Locale.US, "%.2f", s[2]) + "\n\nRecent sales:\n" + db.recentSales());
    }
}