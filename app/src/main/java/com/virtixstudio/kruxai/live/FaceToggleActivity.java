package com.virtixstudio.kruxai.live;

import android.app.Activity;
import android.os.Bundle;

/** Raccourci de l'icône de l'app (appui long) : affiche ou masque le visage, sans ouvrir KRUX. */
public class FaceToggleActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        boolean wasRunning = FloatingKruxService.isRunning();
        FloatingKruxService.toggle(this);
        // si le micro est demandé, on attend la réponse avant de fermer cette fenêtre invisible
        if (wasRunning || android.os.Build.VERSION.SDK_INT < 23
                || checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (res.length > 0 && res[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            FloatingKruxService.toggle(this);
        }
        finish();
    }
}
