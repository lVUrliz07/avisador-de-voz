package pe.glp.avisador;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Lista de los últimos avisos (quién escribió o llamó, cuándo y si se atendió). */
public final class Registro {
    public static final class Evento {
        public long hora;
        public String tipo;      // "mensaje" | "llamada"
        public String app;       // WhatsApp, Messenger, Teléfono...
        public String quien;     // nombre o número como aparece en el teléfono
        public int cantidad;     // mensajes juntos
        public boolean atendido;
        public String clave;     // clave de la notificación (para marcar atendido)
    }

    public interface Oyente { void cambio(); }

    private static final int MAX = 150;
    private static Registro instancia;

    private final SharedPreferences p;
    private final List<Evento> eventos = new ArrayList<>();
    private final List<Oyente> oyentes = new CopyOnWriteArrayList<>();

    public static synchronized Registro get(Context c) {
        if (instancia == null) instancia = new Registro(c.getApplicationContext());
        return instancia;
    }

    private Registro(Context c) {
        p = c.getSharedPreferences("registro", Context.MODE_PRIVATE);
        try {
            JSONArray a = new JSONArray(p.getString("eventos", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Evento e = new Evento();
                e.hora = o.optLong("hora");
                e.tipo = o.optString("tipo");
                e.app = o.optString("app");
                e.quien = o.optString("quien");
                e.cantidad = o.optInt("cantidad", 1);
                e.atendido = o.optBoolean("atendido", true);
                e.clave = o.optString("clave");
                eventos.add(e);
            }
        } catch (Exception ignorado) {
            eventos.clear();
        }
    }

    public synchronized List<Evento> lista() { return new ArrayList<>(eventos); }

    public void agregarOyente(Oyente o) { oyentes.add(o); }
    public void quitarOyente(Oyente o) { oyentes.remove(o); }

    /** Agrega un evento o suma mensajes al último de la misma persona si está pendiente. */
    public synchronized Evento registrar(String tipo, String app, String quien, int cantidad, String clave) {
        if (!eventos.isEmpty()) {
            Evento u = eventos.get(0);
            if ("mensaje".equals(tipo) && tipo.equals(u.tipo) && quien.equals(u.quien) && app.equals(u.app)
                    && !u.atendido && System.currentTimeMillis() - u.hora < 30 * 60 * 1000L) {
                u.cantidad += cantidad;
                u.hora = System.currentTimeMillis();
                u.clave = clave;
                guardar();
                return u;
            }
        }
        Evento e = new Evento();
        e.hora = System.currentTimeMillis();
        e.tipo = tipo;
        e.app = app;
        e.quien = quien;
        e.cantidad = cantidad;
        e.atendido = "llamada".equals(tipo);
        e.clave = clave;
        eventos.add(0, e);
        while (eventos.size() > MAX) eventos.remove(eventos.size() - 1);
        guardar();
        return e;
    }

    public synchronized void marcarAtendido(String clave) {
        boolean cambio = false;
        for (Evento e : eventos) {
            if (!e.atendido && clave.equals(e.clave)) {
                e.atendido = true;
                cambio = true;
            }
        }
        if (cambio) guardar();
    }

    public synchronized void borrarTodo() {
        eventos.clear();
        guardar();
    }

    private void guardar() {
        JSONArray a = new JSONArray();
        try {
            for (Evento e : eventos) {
                JSONObject o = new JSONObject();
                o.put("hora", e.hora);
                o.put("tipo", e.tipo);
                o.put("app", e.app);
                o.put("quien", e.quien);
                o.put("cantidad", e.cantidad);
                o.put("atendido", e.atendido);
                o.put("clave", e.clave == null ? "" : e.clave);
                a.put(o);
            }
        } catch (Exception ignorado) {
            return;
        }
        p.edit().putString("eventos", a.toString()).apply();
        for (Oyente o : oyentes) o.cambio();
    }
}
