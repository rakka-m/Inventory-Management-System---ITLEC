package com.example.inventorymanagementsystem;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class DBHelper extends SQLiteOpenHelper {

    public static class Product {
        public long id;
        public String name, category, image;
        public double price;
        public int qty, min;
        public String status() { return qty == 0 ? "Out of stock" : qty <= min ? "Low stock" : "In stock"; }
    }

    /** Default products seeded in onCreate, paired with their photo drawable in res/drawable-nodpi/. */
    static final String[][] SEED_IMAGES = {
            {"Piatos Cheese 85g", "piatos"},
            {"Chippy BBQ 110g", "chippy"},
            {"Oishi Prawn Crackers 60g", "oishi"},
            {"SkyFlakes Crackers 10s", "skyflakes"},
            {"Nature's Spring Water 500ml", "naturespring"},
            {"C2 Green Tea Apple 230ml", "c2_greentea"},
            {"Great Taste White Coffee 3in1", "great_taste_coffee"},
            {"Milo Powdered Drink 300g", "milo"},
            {"Lucky Me! Pancit Canton Original", "lucky_me_pancit_canton"},
            {"Lucky Me! Beef Mami", "lucky_me_beef_mami"},
            {"Argentina Corned Beef 175g", "argentina_corned_beef"},
            {"Ligo Sardines 155g", "ligo_sardines"},
            {"Datu Puti Vinegar 385ml", "datu_puti_vinegar"},
            {"Silver Swan Soy Sauce 385ml", "silver_swan_soy_sauce"},
            {"Safeguard Bar Soap 60g", "safeguard"},
            {"Surf Powder Detergent 66g", "surf"}};

    static final HashMap<String, Integer> RES_CACHE = new HashMap<>();

    final Context ctx;

    public DBHelper(Context c) { super(c, "inventory.db", null, 2); ctx = c; }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users(username TEXT PRIMARY KEY, password TEXT, role TEXT)");
        db.execSQL("CREATE TABLE products(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE COLLATE NOCASE, category TEXT, price REAL, qty INTEGER, min_qty INTEGER, image TEXT)");
        db.execSQL("CREATE TABLE sales(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, qty INTEGER, total REAL, by_user TEXT, time TEXT)");
        String[][] users = {{"admin", "admin123", "admin"}, {"manager", "manager123", "manager"}, {"staff", "staff123", "staff"}};
        for (String[] u : users) db.execSQL("INSERT INTO users VALUES(?,?,?)", u);
        Object[][] items = {
                {"Piatos Cheese 85g", "Snacks", 40, 36, 10}, {"Chippy BBQ 110g", "Snacks", 38, 28, 10},
                {"Oishi Prawn Crackers 60g", "Snacks", 20, 8, 12}, {"SkyFlakes Crackers 10s", "Snacks", 55, 24, 8},
                {"Nature's Spring Water 500ml", "Drinks", 15, 0, 24}, {"C2 Green Tea Apple 230ml", "Drinks", 22, 30, 10},
                {"Great Taste White Coffee 3in1", "Drinks", 10, 50, 20}, {"Milo Powdered Drink 300g", "Drinks", 125, 14, 5},
                {"Lucky Me! Pancit Canton Original", "Instant & Canned", 18, 60, 20}, {"Lucky Me! Beef Mami", "Instant & Canned", 14, 5, 15},
                {"Argentina Corned Beef 175g", "Instant & Canned", 55, 18, 6}, {"Ligo Sardines 155g", "Instant & Canned", 25, 32, 10},
                {"Datu Puti Vinegar 385ml", "Condiments", 25, 20, 6}, {"Silver Swan Soy Sauce 385ml", "Condiments", 25, 4, 6},
                {"Safeguard Bar Soap 60g", "Household", 35, 22, 8}, {"Surf Powder Detergent 66g", "Household", 12, 40, 15}};
        for (Object[] i : items)
            db.execSQL("INSERT INTO products(name,category,price,qty,min_qty) VALUES(?,?,?,?,?)", i);
        for (String[] m : SEED_IMAGES)
            db.execSQL("UPDATE products SET image=? WHERE name=?", new Object[]{m[1], m[0]});
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
        db.execSQL("DROP TABLE IF EXISTS users");
        db.execSQL("DROP TABLE IF EXISTS products");
        db.execSQL("DROP TABLE IF EXISTS sales");
        onCreate(db);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        migrateLegacyImagePaths(db);
    }

    /**
     * Earlier builds copied the default photos into app storage and saved their file paths.
     * Those rows now point at drawable resources instead, so rewrite them in place and
     * delete the leftover files. No-op once everything has been migrated.
     */
    void migrateLegacyImagePaths(SQLiteDatabase db) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT id,image FROM products WHERE image LIKE '%img_seed_%'", null);
            List<Object[]> updates = new ArrayList<>();
            List<String> staleFiles = new ArrayList<>();
            while (c.moveToNext()) {
                String path = c.getString(1);
                String base = new File(path).getName();
                int dot = base.lastIndexOf('.');
                if (dot < 0) continue;
                String name = base.substring("img_seed_".length(), dot);
                if (photoRes(ctx, name) == 0) continue;
                updates.add(new Object[]{name, c.getLong(0)});
                staleFiles.add(path);
            }
            c.close();
            c = null;
            for (Object[] u : updates) db.execSQL("UPDATE products SET image=? WHERE id=?", u);
            for (String s : staleFiles) {
                try { new File(s).delete(); } catch (Exception ignored) { }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
    }

    /** Maps a bundled photo name ("piatos") to its drawable id, or 0 for file paths / unknown values. */
    static int photoRes(Context c, String name) {
        if (name == null || name.isEmpty() || name.indexOf('/') >= 0) return 0;
        Integer hit = RES_CACHE.get(name);
        if (hit != null) return hit;
        int id;
        try {
            id = c.getResources().getIdentifier(name, "drawable", c.getPackageName());
        } catch (Exception e) {
            id = 0;
        }
        RES_CACHE.put(name, id);
        return id;
    }

    public String login(String user, String pass) {
        Cursor c = getReadableDatabase().rawQuery("SELECT role FROM users WHERE username=? AND password=?", new String[]{user, pass});
        try { return c.moveToFirst() ? c.getString(0) : null; } finally { c.close(); }
    }

    public List<Product> getProducts(String search, String cat) {
        List<Product> list = new ArrayList<>();
        String sql = "SELECT id,name,category,price,qty,min_qty,image FROM products WHERE name LIKE ?";
        List<String> args = new ArrayList<>();
        args.add("%" + search + "%");
        if (!cat.equals("All")) { sql += " AND category=?"; args.add(cat); }
        Cursor c = getReadableDatabase().rawQuery(sql + " ORDER BY name", args.toArray(new String[0]));
        while (c.moveToNext()) {
            Product p = new Product();
            p.id = c.getLong(0); p.name = c.getString(1); p.category = c.getString(2);
            p.price = c.getDouble(3); p.qty = c.getInt(4); p.min = c.getInt(5); p.image = c.getString(6);
            list.add(p);
        }
        c.close();
        return list;
    }

    public boolean save(Product p) {
        ContentValues v = new ContentValues();
        v.put("name", p.name); v.put("category", p.category); v.put("price", p.price);
        v.put("qty", p.qty); v.put("min_qty", p.min); v.put("image", p.image);
        try {
            SQLiteDatabase db = getWritableDatabase();
            if (p.id == 0) return db.insertOrThrow("products", null, v) != -1;
            return db.update("products", v, "id=?", new String[]{String.valueOf(p.id)}) > 0;
        } catch (Exception e) { return false; }
    }

    public void delete(long id) { getWritableDatabase().delete("products", "id=?", new String[]{String.valueOf(id)}); }

    public void restock(long id, int n) {
        getWritableDatabase().execSQL("UPDATE products SET qty=qty+? WHERE id=?", new Object[]{n, id});
    }

    public boolean sell(Product p, int n, String user) {
        SQLiteDatabase db = getWritableDatabase();
        SQLiteStatement st = db.compileStatement("UPDATE products SET qty=qty-? WHERE id=? AND qty>=?");
        st.bindLong(1, n); st.bindLong(2, p.id); st.bindLong(3, n);
        if (st.executeUpdateDelete() == 0) return false;
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        db.execSQL("INSERT INTO sales(name,qty,total,by_user,time) VALUES(?,?,?,?,?)", new Object[]{p.name, n, n * p.price, user, now});
        return true;
    }

    public double[] salesSummary() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*),IFNULL(SUM(qty),0),IFNULL(SUM(total),0) FROM sales", null);
        c.moveToFirst();
        double[] r = {c.getDouble(0), c.getDouble(1), c.getDouble(2)};
        c.close();
        return r;
    }

    public String recentSales() {
        Cursor c = getReadableDatabase().rawQuery("SELECT qty,name,total,by_user,time FROM sales ORDER BY id DESC LIMIT 10", null);
        StringBuilder sb = new StringBuilder();
        while (c.moveToNext())
            sb.append("• ").append(c.getInt(0)).append(" × ").append(c.getString(1)).append(" — ₱")
                    .append(String.format(Locale.US, "%.2f", c.getDouble(2))).append("\n   ")
                    .append(c.getString(4)).append(" by ").append(c.getString(3)).append("\n");
        c.close();
        return sb.length() == 0 ? "No sales yet." : sb.toString();
    }
}