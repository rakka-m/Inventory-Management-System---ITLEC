package com.example.inventorymanagementsystem;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class LoginActivity extends AppCompatActivity {

    private boolean showPass = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        DBHelper db = new DBHelper(this);
        EditText etUser = findViewById(R.id.etUser);
        EditText etPass = findViewById(R.id.etPass);
        ImageView ivEye = findViewById(R.id.ivEye);

        ivEye.setOnClickListener(v -> {
            showPass = !showPass;
            etPass.setTransformationMethod(showPass ? HideReturnsTransformationMethod.getInstance() : PasswordTransformationMethod.getInstance());
            etPass.setSelection(etPass.getText().length());
            ivEye.setImageResource(showPass ? R.drawable.ic_eye_off : R.drawable.ic_eye);
        });

        findViewById(R.id.btnLogin).setOnClickListener(v -> {
            String user = etUser.getText().toString().trim().toLowerCase();
            String role = db.login(user, etPass.getText().toString());
            if (role == null) {
                Toast.makeText(this, "Wrong username or password.", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(this, MainActivity.class);
            i.putExtra("user", user);
            i.putExtra("role", role);
            startActivity(i);
            finish();
        });
    }
}