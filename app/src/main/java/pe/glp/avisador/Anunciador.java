package pe.glp.avisador;

import android.app.Notification;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * El corazón de la app: recibe cada notificación, decide si es un mensaje o
 * una llamada de alguien, y lo dice en voz alta:
 *
 *   "Cliente Juan Pérez te escribe."
 *   "Familia: Mamá te escribe por WhatsApp."
 *   "Número nuevo 9 8 7, 6 5 4, 3 2 1 te escribe. Puede ser un cliente."
 *   "Llamada de cliente Juan Pérez."
 *   "Cliente Juan Pérez sigue esperando respuesta desde hace 10 minutos."
 *
 * Todo ocurre dentro del teléfono: la app no tiene permiso de internet.
 */
public final class Anunciador implements TextToSpeech.OnInitListener {
    private static final String TAG = "Avisador";
    private static Anunciador instancia;

    /** Apps de mensajes que se escuchan → nombre que se dice. */
    static final Map<String, String> APPS_MENSAJES = new HashMap<>();
    static {
        APPS_MENSAJES.put("com.whatsapp", "WhatsApp");
        APPS_MENSAJES.put("com.whatsapp.w4b", "WhatsApp Business");
        APPS_MENSAJES.put("com.facebook.orca", "Messenger");
        APPS_MENSAJES.put("com.facebook.mlite", "Messenger");
        APPS_MENSAJES.put("com.facebook.pages.app", "Facebook");
        APPS_MENSAJES.put("com.google.android.apps.messaging", "SMS");
        APPS_MENSAJES.put("com.samsung.android.messaging", "SMS");
        APPS_MENSAJES.put("com.android.mms", "SMS");
    }

    private static final long AGRUPAR_MS = 2500;     // junta ráfagas de mensajes de la misma persona
    private static final long REPETIR_LLAMADA_MS = 7000;
    private static final int MAX_REPETIR_LLAMADA = 2;

    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Ajustes ajustes;
    private final Contactos contactos;
    private final Registro registro;
    private final AudioManager audio;

    private TextToSpeech tts;
    private boolean ttsListo = false;
    private String problemaVoz = null;
    private int contadorFrases = 0;

    private int volumenAnterior = -1;
    private AudioFocusRequest foco;

    private boolean conectado = false;

    /** Última hora de mensaje ya anunciada por cada notificación (evita repetir). */
    private final Map<String, Long> ultimoVisto = new HashMap<>();
    /** Último texto visto por notificación (apps que no dan la lista de mensajes). */
    private final Map<String, String> ultimoTexto = new HashMap<>();
    /** Mensajes juntándose antes de hablar: quien → pendiente. */
    private final Map<String, Pendiente> porAnunciar = new LinkedHashMap<>();
    /** Mensajes de clientes sin atender: clave de notificación → pendiente. */
    private final Map<String, Pendiente> sinAtender = new HashMap<>();
    /** Llamadas sonando ahora. */
    private final Set<String> llamadasSonando = new HashSet<>();

    private static final class Pendiente {
        String quien, app, categoria, clave, ultimoTexto, grupo;
        int cantidad;
        long desde;
        boolean esLlamadaPerdida;
    }

    public static synchronized Anunciador get(Context c) {
        if (instancia == null) instancia = new Anunciador(c.getApplicationContext());
        return instancia;
    }

    private Anunciador(Context c) {
        ctx = c;
        ajustes = new Ajustes(c);
        contactos = new Contactos(c);
        registro = Registro.get(c);
        audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        iniciarVoz();
    }

    /* ======================= Voz ======================= */

    private void iniciarVoz() {
        ttsListo = false;
        try {
            tts = new TextToSpeech(ctx, this);
        } catch (Exception e) {
            problemaVoz = "Este teléfono no tiene motor de voz.";
        }
    }

    @Override
    public void onInit(int estado) {
        if (estado != TextToSpeech.SUCCESS) {
            problemaVoz = "No se pudo iniciar la voz del teléfono. Instala o activa «Servicios de voz de Google».";
            return;
        }
        Locale[] idiomas = { new Locale("es", "PE"), new Locale("es", "US"), new Locale("es", "MX"), new Locale("es", "ES"), new Locale("es") };
        boolean ok = false;
        for (Locale l : idiomas) {
            int r = tts.setLanguage(l);
            if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) { ok = true; break; }
        }
        if (!ok) problemaVoz = "Falta la voz en español. Ajustes → Idioma → Salida de texto a voz → descarga «Español».";
        tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        tts.setSpeechRate(0.95f);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { h.post(() -> alTerminarFrase(id)); }
            @Override public void onError(String id) { h.post(() -> alTerminarFrase(id)); }
        });
        ttsListo = true;
        if (ok) problemaVoz = null;
        procesarCola();
    }

    public String problemaVoz() { return problemaVoz; }

    /* ---------- Cola propia de frases: una a la vez, y se puede cortar ---------- */

    private static final class Frase {
        final String texto, clave, id;
        Frase(String texto, String clave, String id) { this.texto = texto; this.clave = clave; this.id = id; }
    }

    private final List<Frase> cola = new ArrayList<>();
    private Frase actual = null;

    /** Dice una frase fuerte y clara, aunque el timbre esté en silencio. */
    public void hablar(String frase) { hablar(frase, null); }

    /**
     * @param clave notificación de la que trata la frase: si esa persona ya fue
     *              atendida (contestó la llamada, abrió el chat), la frase se corta.
     */
    public void hablar(String frase, String clave) {
        h.post(() -> {
            cola.add(new Frase(frase, clave, "f" + (++contadorFrases)));
            if (tts == null) iniciarVoz();
            procesarCola();
        });
    }

    private void procesarCola() {
        if (!ttsListo || actual != null) return;
        if (cola.isEmpty()) {
            h.postDelayed(this::restaurarAudio, 400);
            return;
        }
        // En plena conversación telefónica no se habla encima (los recordatorios repiten después)
        int modo = audio.getMode();
        if (modo == AudioManager.MODE_IN_CALL || modo == AudioManager.MODE_IN_COMMUNICATION) {
            cola.clear();
            restaurarAudio();
            return;
        }
        actual = cola.remove(0);
        subirVolumen();
        if (ajustes.tono()) {
            try {
                ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_ALARM, 90);
                tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 350);
                h.postDelayed(tg::release, 800);
            } catch (Exception ignorado) { /* sin tono, igual habla */ }
            tts.playSilentUtterance(450, TextToSpeech.QUEUE_FLUSH, "s" + actual.id);
        }
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f);
        tts.speak(actual.texto, ajustes.tono() ? TextToSpeech.QUEUE_ADD : TextToSpeech.QUEUE_FLUSH, params, actual.id);
    }

    private void alTerminarFrase(String id) {
        if (actual != null && actual.id.equals(id)) {
            actual = null;
            procesarCola();
        }
    }

    /** Se contestó / se abrió el chat: se borra lo pendiente de esa persona y, si está hablando de ella, se calla. */
    private void callar(String clave) {
        if (clave == null) return;
        for (int i = cola.size() - 1; i >= 0; i--) {
            if (clave.equals(cola.get(i).clave)) cola.remove(i);
        }
        if (actual != null && clave.equals(actual.clave)) {
            actual = null;
            if (tts != null) tts.stop();
            procesarCola();
        }
    }

    private void subirVolumen() {
        try {
            if (volumenAnterior < 0) volumenAnterior = audio.getStreamVolume(AudioManager.STREAM_ALARM);
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            int deseado = Math.max(1, Math.round(max * ajustes.volumen() / 100f));
            audio.setStreamVolume(AudioManager.STREAM_ALARM, deseado, 0);
        } catch (Exception e) {
            Log.w(TAG, "No se pudo cambiar el volumen", e);
        }
        if (foco == null) {
            foco = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .build();
            audio.requestAudioFocus(foco); // baja la música o el video mientras habla
        }
    }

    /** Devuelve el volumen como estaba, cuando ya no queda nada por decir. */
    private void restaurarAudio() {
        if (actual != null || !cola.isEmpty()) return;
        try {
            if (volumenAnterior >= 0) audio.setStreamVolume(AudioManager.STREAM_ALARM, volumenAnterior, 0);
        } catch (Exception ignorado) { }
        volumenAnterior = -1;
        if (foco != null) {
            audio.abandonAudioFocusRequest(foco);
            foco = null;
        }
    }

    /* ======================= Estado del servicio ======================= */

    public void setConectado(boolean c) { conectado = c; }
    public boolean conectado() { return conectado; }

    /** Al conectarse, las notificaciones que ya estaban se marcan como vistas (no se repiten). */
    public void marcarVisto(StatusBarNotification sbn) {
        try {
            Notification n = sbn.getNotification();
            List<Msg> msgs = mensajes(n);
            long max = n.when;
            for (Msg m : msgs) max = Math.max(max, m.hora);
            ultimoVisto.put(sbn.getKey(), max);
            CharSequence t = n.extras.getCharSequence(Notification.EXTRA_TEXT);
            if (t != null) ultimoTexto.put(sbn.getKey(), t.toString());
        } catch (Exception ignorado) { }
    }

    /* ======================= Notificaciones ======================= */

    private static final class Msg {
        String emisor;   // null = mensaje propio (lo que respondió el jefe)
        String texto;
        long hora;
    }

    public void alPublicar(StatusBarNotification sbn) {
        if (sbn.getPackageName().equals(ctx.getPackageName())) return;
        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) return;

        if (esLlamadaPerdida(n)) {
            alPerderLlamada(sbn);
            return;
        }
        if (Notification.CATEGORY_CALL.equals(n.category)) {
            if (esLlamadaEntrante(n)) alLlamar(sbn);
            else if (llamadasSonando.remove(sbn.getKey())) callar(sbn.getKey());   // contestó: se calla al instante
            return;
        }

        String app = APPS_MENSAJES.get(sbn.getPackageName());
        if (app == null) return;
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;   // "5 mensajes de 3 chats"
        if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) return;   // "Buscando mensajes nuevos", copias...

        Bundle ex = n.extras;
        String titulo = limpiarTitulo(texto(ex.getCharSequence(Notification.EXTRA_TITLE)));
        String conversacion = texto(ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
        boolean esGrupo = ex.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
                || (conversacion != null && !conversacion.isEmpty());
        String yo = texto(ex.getCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME));

        String clave = sbn.getKey();
        long visto = ultimoVisto.containsKey(clave) ? ultimoVisto.get(clave) : 0L;
        List<Msg> msgs = mensajes(n);

        List<Msg> nuevos = new ArrayList<>();
        boolean respondio = false;
        long maxHora = visto;

        if (!msgs.isEmpty()) {
            for (Msg m : msgs) {
                if (m.hora <= visto) continue;
                maxHora = Math.max(maxHora, m.hora);
                boolean propio = m.emisor == null || (yo != null && yo.equals(m.emisor));
                if (propio) { respondio = true; nuevos.clear(); }  // respondió después de esos mensajes
                else nuevos.add(m);
            }
            ultimoVisto.put(clave, maxHora);
        } else {
            // Apps que no dan la lista de mensajes: se compara el texto
            String t = texto(ex.getCharSequence(Notification.EXTRA_TEXT));
            if (t == null || t.equals(ultimoTexto.get(clave))) return;
            ultimoTexto.put(clave, t);
            Msg m = new Msg();
            m.emisor = titulo;
            m.texto = t;
            m.hora = System.currentTimeMillis();
            nuevos.add(m);
        }

        if (respondio) atendido(clave);
        if (nuevos.isEmpty()) return;

        // ¿Quién es? En un chat personal: el contacto. En un grupo: el grupo.
        Msg ultimo = nuevos.get(nuevos.size() - 1);
        String quien = esGrupo ? (conversacion != null && !conversacion.isEmpty() ? conversacion : titulo) : (ultimo.emisor != null ? ultimo.emisor : titulo);
        if (quien == null || quien.trim().isEmpty()) return;
        quien = quien.trim();

        String categoria = contactos.categoria(quien);
        registro.registrar("mensaje", app, quien, nuevos.size(), clave);
        if (Contactos.SILENCIAR.equals(categoria)) return;

        Pendiente p = porAnunciar.get(quien);
        if (p == null) {
            p = new Pendiente();
            p.quien = quien;
            p.app = app;
            p.categoria = categoria;
            p.desde = System.currentTimeMillis();
            p.grupo = esGrupo ? ultimo.emisor : null;
            porAnunciar.put(quien, p);
            final String q = quien;
            h.postDelayed(() -> anunciarMensajes(q), AGRUPAR_MS);
        }
        p.cantidad += nuevos.size();
        p.clave = clave;
        p.ultimoTexto = ultimo.texto;
    }

    public void alQuitar(StatusBarNotification sbn) {
        String clave = sbn.getKey();
        llamadasSonando.remove(clave);
        // Se quitó la notificación: contestó/colgó la llamada, abrió el chat o la deslizó. Ya está atendido.
        atendido(clave);
    }

    private void atendido(String clave) {
        sinAtender.remove(clave);
        registro.marcarAtendido(clave);
        callar(clave);
    }

    private void anunciarMensajes(String quien) {
        Pendiente p = porAnunciar.remove(quien);
        if (p == null) return;
        boolean recordar = !Contactos.FAMILIA.equals(p.categoria) && !Contactos.SILENCIAR.equals(p.categoria);
        if (recordar && ajustes.recordatorioMin() > 0) programarRecordatorio(p);
        if (!debeHablar(p.categoria)) return;
        hablar(fraseMensaje(p), p.clave);
    }

    private boolean debeHablar(String categoria) {
        if (!ajustes.activo() || ajustes.callado()) return false;
        if (Contactos.SILENCIAR.equals(categoria)) return false;
        return !Contactos.FAMILIA.equals(categoria) || ajustes.anunciarFamilia();
    }

    private String fraseMensaje(Pendiente p) {
        StringBuilder f = new StringBuilder();
        if (p.grupo != null && !p.grupo.isEmpty()) {
            // "En el grupo Familia Pérez, Pedro escribió 2 mensajes."
            f.append("En el grupo ").append(p.quien).append(", ").append(p.grupo)
             .append(p.cantidad > 1 ? " escribió " + p.cantidad + " mensajes" : " escribió");
        } else {
            f.append(nombreHablado(p.quien, p.categoria));
            f.append(p.cantidad > 1 ? " te escribió " + p.cantidad + " mensajes" : " te escribe");
        }
        if (!"WhatsApp".equals(p.app)) f.append(" por ").append(p.app);
        f.append(".");
        if (Contactos.NUEVO.equals(p.categoria)) f.append(" Puede ser un cliente.");
        boolean leer = ajustes.leerTexto() && p.ultimoTexto != null
                && (Contactos.CLIENTE.equals(p.categoria) || Contactos.NUEVO.equals(p.categoria));
        if (leer) {
            String t = p.ultimoTexto.length() > 140 ? p.ultimoTexto.substring(0, 140) : p.ultimoTexto;
            f.append(" Dice: ").append(t);
        }
        return f.toString();
    }

    /** "Cliente Juan Pérez" · "Familia: Mamá" · "Número nuevo 9 8 7, 6 5 4…" · "Juan Pérez" */
    private static String nombreHablado(String quien, String categoria) {
        switch (categoria) {
            case Contactos.CLIENTE: return "Cliente " + quien;
            case Contactos.FAMILIA: return "Familia: " + quien;
            case Contactos.NUEVO: return "Número nuevo " + Contactos.numeroParaVoz(quien);
            default: return quien;
        }
    }

    /* ---------- Recordatorio de clientes sin respuesta ---------- */

    /** Minutos (desde el primer mensaje) en que se recuerda: el elegido, luego 30 y 60. */
    private int[] horariosRecordatorio() {
        int primero = ajustes.recordatorioMin();
        if (primero <= 0) return new int[0];
        if (primero >= 60) return new int[] { primero };
        if (primero >= 30) return new int[] { primero, 60 };
        return new int[] { primero, 30, 60 };
    }

    private void programarRecordatorio(Pendiente p) {
        Pendiente ya = sinAtender.get(p.clave);
        if (ya != null) {                       // ya hay recordatorios corriendo para ese chat
            ya.cantidad += p.cantidad;
            return;
        }
        sinAtender.put(p.clave, p);
        int[] minutos = horariosRecordatorio();
        for (int m : minutos) {
            long espera = p.desde + m * 60_000L - System.currentTimeMillis();
            final String clave = p.clave;
            h.postDelayed(() -> recordar(clave, p), Math.max(1000, espera));
        }
    }

    /** "Hay un mensaje sin contestar de cliente Juan Pérez, de hace 10 minutos." */
    private void recordar(String clave, Pendiente p) {
        if (sinAtender.get(clave) != p) return;                 // ya fue atendido
        long minutos = Math.max(1, Math.round((System.currentTimeMillis() - p.desde) / 60_000.0));
        int[] horarios = horariosRecordatorio();
        if (horarios.length > 0 && minutos >= horarios[horarios.length - 1]) sinAtender.remove(clave);  // último aviso
        if (!debeHablar(p.categoria)) return;
        String quien = nombreHablado(p.quien, p.categoria);
        String que = p.esLlamadaPerdida ? "una llamada perdida"
                : (p.cantidad > 1 ? p.cantidad + " mensajes sin contestar" : "un mensaje sin contestar");
        hablar("Hay " + que + " de " + quien + ", de hace " + minutos + (minutos == 1 ? " minuto." : " minutos."), clave);
    }

    /* ---------- Llamadas ---------- */

    private static boolean esLlamadaEntrante(Notification n) {
        String t = (texto(n.extras.getCharSequence(Notification.EXTRA_TEXT)) + " "
                + texto(n.extras.getCharSequence(Notification.EXTRA_TITLE))).toLowerCase(Locale.ROOT);
        if (t.contains("en curso") || t.contains("ongoing") || t.contains("llamada actual") || t.contains("perdida") || t.contains("missed")) return false;
        return n.fullScreenIntent != null || t.contains("entrante") || t.contains("incoming");
    }

    private void alLlamar(StatusBarNotification sbn) {
        String clave = sbn.getKey();
        if (llamadasSonando.contains(clave)) return;
        Notification n = sbn.getNotification();
        String titulo = limpiarTitulo(texto(n.extras.getCharSequence(Notification.EXTRA_TITLE)));
        String txt = texto(n.extras.getCharSequence(Notification.EXTRA_TEXT));
        // Algunos teléfonos ponen "Llamada entrante" de título y el nombre abajo
        String quien = titulo;
        if (quien == null || quien.toLowerCase(Locale.ROOT).matches(".*(llamada|incoming|entrante).*")) quien = txt;
        if (quien == null || quien.trim().isEmpty()) quien = "número privado";
        quien = quien.trim();

        llamadasSonando.add(clave);
        String app = APPS_MENSAJES.containsKey(sbn.getPackageName()) ? APPS_MENSAJES.get(sbn.getPackageName()) : "Teléfono";
        registro.registrar("llamada", app, quien, 1, clave);
        String categoria = contactos.categoria(quien);
        if (!debeHablar(categoria)) return;

        String frase = "Llamada" + ("Teléfono".equals(app) ? "" : " de " + app) + " de "
                + nombreHablado(quien, categoria).replaceFirst("^Familia: ", "familia, ") + ".";
        anunciarLlamada(clave, frase, 0);
    }

    private static boolean esLlamadaPerdida(Notification n) {
        if ("missed_call".equals(n.category)) return true;   // Notification.CATEGORY_MISSED_CALL (Android 11+)
        String t = (texto(n.extras.getCharSequence(Notification.EXTRA_TITLE)) + " "
                + texto(n.extras.getCharSequence(Notification.EXTRA_TEXT))).toLowerCase(Locale.ROOT);
        return t.contains("llamada perdida") || t.contains("llamadas perdidas") || t.contains("missed call");
    }

    /** "Llamada perdida de cliente Juan Pérez." — para que no se pase por alto. */
    private void alPerderLlamada(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        String titulo = limpiarTitulo(texto(n.extras.getCharSequence(Notification.EXTRA_TITLE)));
        String txt = texto(n.extras.getCharSequence(Notification.EXTRA_TEXT));
        String firma = titulo + "|" + txt;
        if (firma.equals(ultimoTexto.get(sbn.getKey()))) return;
        ultimoTexto.put(sbn.getKey(), firma);
        // El nombre suele estar en el texto ("Juan Pérez") con título "Llamada perdida", o al revés
        String quien = titulo;
        if (quien == null || quien.toLowerCase(Locale.ROOT).matches(".*(perdida|missed|llamada).*")) quien = txt;
        if (quien == null || quien.trim().isEmpty() || quien.toLowerCase(Locale.ROOT).matches(".*\\d+\\s+llamadas.*")) return;
        quien = quien.trim();
        String app = APPS_MENSAJES.containsKey(sbn.getPackageName()) ? APPS_MENSAJES.get(sbn.getPackageName()) : "Teléfono";
        registro.registrar("perdida", app, quien, 1, sbn.getKey());
        String categoria = contactos.categoria(quien);
        if (!Contactos.FAMILIA.equals(categoria) && !Contactos.SILENCIAR.equals(categoria) && ajustes.recordatorioMin() > 0) {
            Pendiente p = new Pendiente();
            p.quien = quien;
            p.app = app;
            p.categoria = categoria;
            p.clave = sbn.getKey();
            p.cantidad = 1;
            p.desde = System.currentTimeMillis();
            p.esLlamadaPerdida = true;
            programarRecordatorio(p);
        }
        if (!debeHablar(categoria)) return;
        hablar("Llamada perdida de " + nombreHablado(quien, categoria).replaceFirst("^Familia: ", "familia, ") + ".", sbn.getKey());
    }

    private void anunciarLlamada(String clave, String frase, int vez) {
        if (!llamadasSonando.contains(clave)) return;   // ya contestó o colgó
        hablar(frase, clave);
        if (vez < MAX_REPETIR_LLAMADA) h.postDelayed(() -> anunciarLlamada(clave, frase, vez + 1), REPETIR_LLAMADA_MS);
    }

    /* ======================= Utilidades ======================= */

    private static List<Msg> mensajes(Notification n) {
        List<Msg> lista = new ArrayList<>();
        Parcelable[] arr;
        try {
            arr = n.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        } catch (Exception e) {
            return lista;
        }
        if (arr == null) return lista;
        for (Parcelable pa : arr) {
            if (!(pa instanceof Bundle)) continue;
            Bundle b = (Bundle) pa;
            Msg m = new Msg();
            m.texto = texto(b.getCharSequence("text"));
            m.hora = b.getLong("time");
            m.emisor = texto(b.getCharSequence("sender"));
            if (m.emisor == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Object persona = b.get("sender_person");
                if (persona instanceof android.app.Person) {
                    m.emisor = texto(((android.app.Person) persona).getName());
                }
            }
            lista.add(m);
        }
        return lista;
    }

    private static String texto(CharSequence c) {
        return c == null ? null : c.toString();
    }

    /** "Juan Pérez (3 mensajes)" → "Juan Pérez"; quita marcas invisibles de dirección de texto. */
    private static String limpiarTitulo(String t) {
        if (t == null) return null;
        t = t.replaceAll("[\\u202A-\\u202E\\u2066-\\u2069\\u200E\\u200F]", "");
        t = t.replaceAll("\\s*\\(\\d+\\s+mensajes?\\)\\s*$", "");
        t = t.replaceAll("\\s*\\(\\d+\\s+messages?\\)\\s*$", "");
        return t.trim();
    }

    /** Para el botón "Probar voz". */
    public void probar() {
        hablar("Cliente Juan Pérez te escribe. Así sonará el aviso.");
    }
}
