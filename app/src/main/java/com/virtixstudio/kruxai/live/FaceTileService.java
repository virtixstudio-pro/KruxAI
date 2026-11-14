package com.virtixstudio.kruxai.live;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import com.virtixstudio.kruxai.ui.MainActivity;

/** Tuile des réglages rapides : affiche ou masque le visage de KRUX sans ouvrir l'app. */
public class FaceTileService extends TileService {

    @Override public void onStartListening() { refresh(); }

    @Override
    public void onClick() {
        boolean wasRunning = FloatingKruxService.isRunning();
        boolean ok = FloatingKruxService.toggle(this);
        if (!ok) {
            // autorisation manquante (affichage par-dessus les apps ou micro) : on ouvre KRUX pour la donner
            Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 5, i, PendingIntent.FLAG_IMMUTABLE));
            } else {
                startActivityAndCollapse(i);
            }
            return;
        }
        Tile t = getQsTile();
        if (t != null) { t.setState(wasRunning ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE); t.updateTile(); }
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(FloatingKruxService.isRunning() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }
}
