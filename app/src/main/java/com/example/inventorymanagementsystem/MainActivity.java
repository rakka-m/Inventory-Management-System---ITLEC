package com.example.inventorymanagementsystem;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    static final String[] CATS = {"Snacks", "Drinks", "Instant & Canned", "Condiments", "Household", "Others"};
    static final String[] FILTER = {"All", "Snacks", "Drinks", "Instant & Canned", "Condiments", "Household", "Others"};

    DBHelper db;
    String user, role;
    boolean canManage;
    List<DBHelper.Product> list = new ArrayList<>();
    ListView lv;
    EditText etSearch;
    Spinner spCat;
    TextView tvEmpty;
    ActivityResultLauncher<String> picker;
    Bitmap pickedBmp;
    ImageView dlgImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = new DBHelper(this);
        user = getIntent().getStringExtra("user");
        role = getIntent().getStringExtra("role");
        if (user == null || role == null) { goLogin(); return; }
        canManage = role.equals("admin") || role.equals("manager");

        lv = findViewById(R.id.lv);
        etSearch = findViewById(R.id.etSearch);
        spCat = findViewById(R.id.spCat);
        tvEmpty = findViewById(R.id.tvEmpty);
        picker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null) return;
            Bitmap bm = decodeScaled(uri);
            if (bm == null) { toast("Could not load that image."); return; }
            pickedBmp = bm;
            if (dlgImage != null) dlgImage.setImageBitmap(bm);
        });
        ((TextView) findViewById(R.id.tvUser)).setText("Logged in: " + user + " (" + role + ")");

        findViewById(R.id.btnAdd).setVisibility(canManage ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnReports).setVisibility(canManage ? View.VISIBLE : View.GONE);

        spCat.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, FILTER));
        spCat.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View v, int pos, long id) { refresh(); }
            public void onNothingSelected(AdapterView<?> a) { }
        });
        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { refresh(); }
            public void afterTextChanged(Editable s) { }
        });
        lv.setOnItemClickListener((a, v, pos, id) -> showActions(list.get(pos)));
        findViewById(R.id.btnAdd).setOnClickListener(v -> productDialog(null));
        findViewById(R.id.btnLogout).setOnClickListener(v -> goLogin());
        findViewById(R.id.btnReports).setOnClickListener(v -> {
            Intent i = new Intent(this, ReportsActivity.class);
            i.putExtra("role", role);
            startActivity(i);
        });
    }

    @Override
    protected void onResume() { super.onResume(); if (db != null && lv != null) refresh(); }

    void goLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    /** True when the stored value is a photo file we saved (not a bundled drawable name). */
    boolean isPhotoFile(String image) {
        return image != null && image.indexOf('/') >= 0 && new File(image).exists();
    }

    /** Shows a bundled product drawable, a saved photo file, or the placeholder. */
    void showPhoto(ImageView iv, String image) {
        if (image != null && !image.isEmpty()) {
            int res = DBHelper.photoRes(this, image);
            if (res != 0) { iv.setImageResource(res); return; }
            if (new File(image).exists()) {
                Bitmap b = BitmapFactory.decodeFile(image);
                if (b != null) { iv.setImageBitmap(b); return; }
            }
        }
        iv.setImageResource(R.drawable.ic_placeholder);
    }

    void refresh() {
        list = db.getProducts(etSearch.getText().toString().trim(), spCat.getSelectedItem().toString());
        lv.setAdapter(new ArrayAdapter<DBHelper.Product>(this, 0, list) {
            @Override
            public View getView(int pos, View v, ViewGroup parent) {
                if (v == null) v = getLayoutInflater().inflate(R.layout.item_product, parent, false);
                DBHelper.Product p = getItem(pos);
                ((TextView) v.findViewById(R.id.tvName)).setText(p.name);
                ImageView iv = v.findViewById(R.id.ivThumb);
                showPhoto(iv, p.image);
                ((TextView) v.findViewById(R.id.tvInfo)).setText(p.category + " • ₱" + String.format(Locale.US, "%.2f", p.price)
                        + " • Qty: " + p.qty + " (min " + p.min + ")");
                TextView st = v.findViewById(R.id.tvStatus);
                st.setText(p.status());
                int col = p.qty == 0 ? 0xFFD64545 : p.qty <= p.min ? 0xFFD98216 : 0xFF2E7D32;
                st.setTextColor(col);
                v.findViewById(R.id.vStatus).setBackgroundColor(col);
                return v;
            }
        });
        List<DBHelper.Product> all = db.getProducts("", "All");
        int lowCount = 0;
        double total = 0;
        for (DBHelper.Product x : all) { if (x.qty <= x.min) lowCount++; total += x.qty * x.price; }
        ((TextView) findViewById(R.id.tvTileProducts)).setText(String.valueOf(all.size()));
        ((TextView) findViewById(R.id.tvTileLow)).setText(String.valueOf(lowCount));
        ((TextView) findViewById(R.id.tvTileValue)).setText("₱" + String.format(Locale.US, "%,.0f", total));
        tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    void showActions(DBHelper.Product p) {
        List<String> opts = new ArrayList<>(Arrays.asList("Sell", "Restock"));
        if (canManage) { opts.add("Edit"); opts.add("Delete"); }
        new AlertDialog.Builder(this).setTitle(p.name)
                .setItems(opts.toArray(new String[0]), (d, w) -> {
                    String o = opts.get(w);
                    if (o.equals("Sell")) qtyDialog(p, true);
                    else if (o.equals("Restock")) qtyDialog(p, false);
                    else if (o.equals("Edit")) productDialog(p);
                    else confirmDelete(p);
                }).show();
    }

    void qtyDialog(DBHelper.Product p, boolean sell) {
        EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_NUMBER);
        in.setText("1");
        in.setPadding(48, 32, 48, 32);
        new AlertDialog.Builder(this).setTitle((sell ? "Sell: " : "Restock: ") + p.name + " (stock " + p.qty + ")")
                .setView(in)
                .setPositiveButton("Confirm", (d, w) -> {
                    int n;
                    try { n = Integer.parseInt(in.getText().toString().trim()); } catch (Exception e) { n = 0; }
                    if (n < 1) { toast("Enter a whole number of 1 or more."); return; }
                    if (sell) {
                        if (!db.sell(p, n, user)) { toast("Not enough stock."); return; }
                        toast("Sale recorded");
                    } else {
                        db.restock(p.id, n);
                        toast("Stock added");
                    }
                    refresh();
                }).setNegativeButton("Cancel", null).show();
    }

    void confirmDelete(DBHelper.Product p) {
        if (!canManage) return;
        new AlertDialog.Builder(this).setTitle("Delete product?")
                .setMessage("Remove " + p.name + " from inventory? This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> { if (isPhotoFile(p.image)) new File(p.image).delete(); db.delete(p.id); toast("Product deleted"); refresh(); })
                .setNegativeButton("Cancel", null).show();
    }

    void productDialog(DBHelper.Product p) {
        if (!canManage) return;
        View v = getLayoutInflater().inflate(R.layout.dialog_product, null);
        EditText name = v.findViewById(R.id.etName), price = v.findViewById(R.id.etPrice);
        EditText qty = v.findViewById(R.id.etQty), min = v.findViewById(R.id.etMin);
        Spinner sp = v.findViewById(R.id.spCategory);
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, CATS));
        if (p != null) {
            name.setText(p.name); price.setText(String.valueOf(p.price));
            qty.setText(String.valueOf(p.qty)); min.setText(String.valueOf(p.min));
            sp.setSelection(Math.max(0, Arrays.asList(CATS).indexOf(p.category)));
        } else min.setText("5");
        pickedBmp = null;
        dlgImage = v.findViewById(R.id.ivPreview);
        showPhoto(dlgImage, p == null ? null : p.image);
        v.findViewById(R.id.btnPhoto).setOnClickListener(bv -> picker.launch("image/*"));

        AlertDialog dlg = new AlertDialog.Builder(this).setTitle(p == null ? "Add product" : "Edit product")
                .setView(v).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            String n = name.getText().toString().trim();
            double pr; int q, m;
            try {
                pr = Double.parseDouble(price.getText().toString().trim());
                q = Integer.parseInt(qty.getText().toString().trim());
                m = Integer.parseInt(min.getText().toString().trim());
            } catch (Exception e) { toast("Please fill in valid numbers."); return; }
            if (n.isEmpty()) { toast("Product name is required."); return; }
            if (pr < 0 || q < 0 || m < 0) { toast("Values cannot be negative."); return; }
            DBHelper.Product np = new DBHelper.Product();
            np.id = p == null ? 0 : p.id;
            np.name = n; np.category = sp.getSelectedItem().toString(); np.price = pr; np.qty = q; np.min = m;
            String old = p == null ? null : p.image;
            String newPath = pickedBmp != null ? saveImage(pickedBmp) : null;
            np.image = newPath != null ? newPath : old;
            if (!db.save(np)) {
                if (newPath != null) new File(newPath).delete();
                toast("That product name already exists.");
                return;
            }
            if (newPath != null && isPhotoFile(old)) new File(old).delete();
            dlg.dismiss();
            toast("Product saved");
            refresh();
        });
    }

    Bitmap decodeScaled(android.net.Uri uri) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
            int s = 1;
            while (o.outWidth / s > 512 || o.outHeight / s > 512) s *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = s;
            try (InputStream in = getContentResolver().openInputStream(uri)) { return BitmapFactory.decodeStream(in, null, o); }
        } catch (Exception e) { return null; }
    }

    String saveImage(Bitmap b) {
        try {
            float r = Math.min(1f, 256f / Math.max(b.getWidth(), b.getHeight()));
            if (r < 1f) b = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * r), Math.round(b.getHeight() * r), true);
            File f = new File(getFilesDir(), "img_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream out = new FileOutputStream(f)) { b.compress(Bitmap.CompressFormat.JPEG, 85, out); }
            return f.getAbsolutePath();
        } catch (Exception e) { return null; }
    }
}