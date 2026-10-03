package com.virtixstudio.kruxai.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.virtixstudio.kruxai.R;
import com.virtixstudio.kruxai.utils.LanguageManager;

public class AuthWelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LanguageManager.applySavedLanguage(this);
        setContentView(R.layout.activity_auth_welcome);

        Button btnEmail = findViewById(R.id.btnAuthEmail);
        Button btnGoogle = findViewById(R.id.btnAuthGoogle);
        TextView tvLanguage = findViewById(R.id.tvWelcomeLanguage);

        tvLanguage.setOnClickListener(v ->
                LanguageManager.showLanguageDialog(this)
        );

        btnEmail.setOnClickListener(v -> {
            startActivity(new Intent(
                    AuthWelcomeActivity.this,
                    LoginActivity.class
            ));
        });

        btnGoogle.setOnClickListener(v -> {
            // Google sera configuré plus tard.
        });
    }
}
