package com.virtixstudio.kruxai.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.virtixstudio.kruxai.R;
import com.virtixstudio.kruxai.api.ApiClient;
import com.virtixstudio.kruxai.models.StudioAgent;
import com.virtixstudio.kruxai.models.KruxModel;

import java.util.ArrayList;
import java.util.List;

public class StudioActivity extends AppCompatActivity {


    private EditText etAgentName, etAgentRole, etStudioTask;
    private Button btnAddAgent, btnRunStudio;
    private TextView tvAgentsList, tvStudioOutput;

    private List<StudioAgent> agents = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_studio);
        KruxTheme.apply(this);


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

        agents.add(new StudioAgent(name, role, "", KruxModel.KRUX_33_70B));
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
        EditText etName = findViewById(R.id.etStudioName);
        EditText etObj = findViewById(R.id.etStudioObjective);
        String studioName = etName != null ? etName.getText().toString().trim() : "";
        String objective = etObj != null ? etObj.getText().toString().trim() : "";

        tvStudioOutput.append("--- " + (studioName.isEmpty() ? "PROJET STUDIO" : studioName) + " ---\n"
                + (objective.isEmpty() ? "" : "Objectif : " + objective + "\n")
                + "Tâche : " + task + "\n\n");
        etStudioTask.setText("");
        btnRunStudio.setEnabled(false);
        runAgentStep(0, task, objective, new StringBuilder());
    }

    private void runAgentStep(final int index, final String task, final String objective, final StringBuilder previous) {
        final TextView status = findViewById(R.id.tvStudioStatus);
        if (index >= agents.size()) {
            runOnUiThread(() -> {
                if (status != null) status.setText("Terminé");
                btnRunStudio.setEnabled(true);
            });
            return;
        }
        final StudioAgent agent = agents.get(index);
        runOnUiThread(() -> {
            if (status != null) status.setText("Agent " + (index + 1) + "/" + agents.size() + " : " + agent.getName() + "…");
        });

        String systemPrompt = "Tu es " + agent.getName() + ", membre de l'équipe Virtix Studio. Ton rôle est : "
                + agent.getRole() + ". "
                + (objective.isEmpty() ? "" : "Objectif global du studio : " + objective + ". ")
                + "Sois concis et ultra-spécialisé. Appuie-toi sur le travail des agents précédents sans le répéter.";
        String prompt = previous.length() == 0
                ? task
                : "TÂCHE : " + task + "\n\nTRAVAIL DES AGENTS PRÉCÉDENTS :\n" + previous;

        ApiClient.sendRequest(
                KruxModel.KRUX_33_70B,
                systemPrompt,
                prompt,
                new ApiClient.ApiCallback() {
                    @Override
                    public void onSuccess(String responseText, String modelBrand) {
                        runOnUiThread(() -> tvStudioOutput.append(
                                "[" + agent.getName() + " - " + agent.getRole() + "] (" + modelBrand + ") :\n"
                                        + responseText + "\n\n"));
                        previous.append("[").append(agent.getName()).append("] ").append(responseText).append("\n\n");
                        if (previous.length() > 6000) previous.delete(0, previous.length() - 6000);
                        runAgentStep(index + 1, task, objective, previous);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        runOnUiThread(() -> tvStudioOutput.append(
                                "[" + agent.getName() + "] Erreur : " + errorMessage + "\n\n"));
                        runAgentStep(index + 1, task, objective, previous);
                    }
                }
        );
    }
}
