package com.imontalvodev.a022sistemalogin;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class InicioSesion extends AppCompatActivity {

    private EditText etNombre, etPassword;
    private CheckBox ckRecordar;
    private Button btn;
    private SharedPreferences sharedPreferences;
    private Intent intent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_inicio_sesion);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        etNombre = findViewById(R.id.etUsuario);
        etPassword = findViewById(R.id.etPassword);
        ckRecordar = findViewById(R.id.cbRecordar);
        btn = findViewById(R.id.btnLogin);
        sharedPreferences = getSharedPreferences("datos", MODE_PRIVATE);
        intent = new Intent(this, MainActivity.class);

        // AUTOLOGIN
        if (existenDatos()) {
            startActivity(intent);
        }

        btn.setOnClickListener(v -> {
            if (etNombre.getText().toString().isEmpty() || etPassword.getText().toString().isEmpty()) {
                Toast.makeText(this, "Por favor rellene los datos", Toast.LENGTH_LONG).show();
                return;
            }

            @SuppressLint("CommitPrefEdits") SharedPreferences.Editor editor = sharedPreferences.edit();
            if (ckRecordar.isChecked()) {
                editor.putString("user", etNombre.getText().toString());
                editor.putString("pass", etPassword.getText().toString());
                editor.apply();
            }

            startActivity(intent);

        });
    }

    boolean existenDatos() {
        return sharedPreferences.contains("user") && sharedPreferences.contains("pass");
    }
}