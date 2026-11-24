package pe.glp.avisador;

import android.content.ComponentName;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

/**
 * Servicio que Android mantiene vivo mientras tenga el permiso de "acceso a
 * notificaciones". Recibe cada notificación nueva (y cuando se quita) y se
 * la pasa al Anunciador. No toca WhatsApp ni responde nada.
 */
public class AvisoService extends NotificationListenerService {
    private static final String TAG = "Avisador";

    @Override
    public void onListenerConnected() {
        Anunciador a = Anunciador.get(this);
        a.setConectado(true);
        // Lo que ya estaba en la barra no se vuelve a anunciar
        try {
            StatusBarNotification[] activas = getActiveNotifications();
            if (activas != null) for (StatusBarNotification s : activas) a.marcarVisto(s);
        } catch (Exception e) {
            Log.w(TAG, "No se pudieron leer las notificaciones activas", e);
        }
    }

    @Override
    public void onListenerDisconnected() {
        Anunciador.get(this).setConectado(false);
        // Pedir a Android que vuelva a conectarlo
        requestRebind(new ComponentName(this, AvisoService.class));
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            Anunciador.get(this).alPublicar(sbn);
        } catch (Throwable t) {
            Log.e(TAG, "Error procesando notificación", t);   // nunca dejar que un error lo detenga
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        try {
            Anunciador.get(this).alQuitar(sbn);
        } catch (Throwable t) {
            Log.e(TAG, "Error al quitar notificación", t);
        }
    }
}
