package pe.glp.avisador;

import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Botón en el panel rápido de Android (donde están Wi-Fi y Linterna):
 * un toque silencia la voz, otro toque la activa. No hace falta abrir la app.
 */
public class BotonRapido extends TileService {
    @Override
    public void onStartListening() {
        pintar();
    }

    @Override
    public void onClick() {
        Ajustes a = new Ajustes(this);
        boolean activar = !a.activo();
        a.setActivo(activar);
        if (activar) a.setPausaHasta(0);
        Anunciador.get(this).alCambiarSilencio(activar);
        pintar();
    }

    private void pintar() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean activo = new Ajustes(this).activo();
        t.setState(activo ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel(activo ? "Voz activa" : "Voz silenciada");
        t.setIcon(Icon.createWithResource(this, activo ? R.drawable.ic_voz : R.drawable.ic_silencio));
        t.updateTile();
    }
}
