package com.virtixstudio.kruxai.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.virtixstudio.kruxai.R;
import com.virtixstudio.kruxai.api.GroqApiClient;
import com.virtixstudio.kruxai.models.StudioAgent;

import java.util.ArrayList;
import java.util.List;

public class StudioActivity extends AppCompatActivity {

    private static final String GROQ_API_KEY = com.virtixstudio.kruxai.BuildConfig.GROQ_API_KEY;

    private EditText etAgentName, etAgentRole, etStudioTask;
    private Button btnAddAgent;
    private ImageButton btnRunStudio;
    private TextView tvAgentsList, tvStudioOutput;

    private List<StudioAgent> agents = new ArrayList<>();
    private GroqApiClient groqClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_studio);

        groqClient = new GroqApiClient(GROQ_API_KEY);

        etAgentName = findViewById(R.id.etAgentName);
        etAgentRole = findViewById(R.id.etAgentRole);
        etStudioTask = findViewById(R.id.etStudioTask);
        btnAddAgent = findViewById(R.id.btnAddAgent);
        btnRunStudio = findViewById(R.id.btnRunStudio);
        tvAgentsList = findViewById(R.id.tvAgentsList);
        tvStudioOutput = findViewById(R.id.tvStudioOutput);

        btnAddAgent.setOnClickListener(v -> addAgent());
        btnRunStudio.setOnClickListener(v -> runStudioProcess());
    }

    private void addAgent() {
        String name = etAgentName.getText().toString().trim();
        String role = etAgentRole.getText().toString().trim();

        if (name.isEmpty() || role.isEmpty()) {
            Toast.makeText(this, "Renseignez le nom et le rôle de l'agent.", Toast.LENGTH_SHORT).show();
            return;
        }

        agents.add(new StudioAgent(name, role));
        etAgentName.setText("");
        etAgentRole.setText("");

        StringBuilder sb = new StringBuilder("Agents actifs : ");
        for (StudioAgent a : agents) {
            sb.append(a.getName()).append(" (").append(a.getRole()).append("), ");
        }
        tvAgentsList.setText(sb.toString());
    }

    private void runStudioProcess() {
        String task = etStudioTask.getText().toString().trim();
        if (task.isEmpty()) return;

        if (agents.isEmpty()) {
            Toast.makeText(this, "Ajoutez au moins un agent avant de démarrer le studio.", Toast.LENGTH_SHORT).show();
            return;
        }

        tvStudioOutput.append("--- PROJET STUDIO INITIALISÉ ---\nObjectif : " + task + "\n\n");
        etStudioTask.setText("");

        for (StudioAgent agent : agents) {
            String systemPrompt = "Tu es " + agent.getName() + ", membre de l'équipe Virtix Studio. Ton rôle est : " 
                    + agent.getRole() + ". Sois concis, ultra-spécialisé dans ton domaine et collabore efficacement.";

            groqClient.sendMessage("llama-3.3-70b-versatile", systemPrompt, task, new GroqApiClient.GroqCallback() {
                @Override
                public void onSuccess(String responseText) {
                    tvStudioOutput.append("[" + agent.getName() + " - " + agent.getRole() + "] :\n" + responseText + "\n\n");
                }

                @Override
                public void onError(String errorMessage) {
                    tvStudioOutput.append("[" + agent.getName() + "] Erreur : " + errorMessage + "\n\n");
                }
            });
        }
    }
}
