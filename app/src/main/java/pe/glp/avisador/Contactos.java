package pe.glp.avisador;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.Normalizer;
import java.util.Locale;

/**
 * A qué grupo pertenece cada contacto: CLIENTE, FAMILIA o SILENCIAR.
 * Se identifica por el nombre tal como aparece en WhatsApp / la llamada
 * (el nombre guardado en la agenda) o por el número si no está guardado.
 */
public final class Contactos {
    public static final String CLIENTE = "CLIENTE";
    public static final String FAMILIA = "FAMILIA";
    public static final String SILENCIAR = "SILENCIAR";
    /** Sin clasificar y con nombre guardado en la agenda */
    public static final String CONTACTO = "CONTACTO";
    /** Sin clasificar y sin nombre: solo se ve el número */
    public static final String NUEVO = "NUEVO";

    private final SharedPreferences p;

    public Contactos(Context c) {
        p = c.getApplicationContext().getSharedPreferences("contactos", Context.MODE_PRIVATE);
    }

    public static String clave(String nombre) {
        if (nombre == null) return "";
        if (esNumero(nombre)) return soloDigitosLocales(nombre);
        String s = Normalizer.normalize(nombre.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return s.replaceAll("\\p{M}", "").replaceAll("\\s+", " ");
    }

    public String categoria(String nombre) {
        String guardada = p.getString(clave(nombre), null);
        if (guardada != null) return guardada;
        return esNumero(nombre) ? NUEVO : CONTACTO;
    }

    public void asignar(String nombre, String categoria) {
        if (categoria == null) p.edit().remove(clave(nombre)).apply();
        else p.edit().putString(clave(nombre), categoria).apply();
    }

    /** "+51 987 654 321", "987654321", "‪+51 987‑654‑321‬" → es número (no hay nombre guardado). */
    public static boolean esNumero(String s) {
        if (s == null) return false;
        String limpio = s.replaceAll("[\\s\\-()+.\\u202A-\\u202E\\u2011\\u00A0]", "");
        return limpio.length() >= 6 && limpio.matches("\\d+");
    }

    static String soloDigitosLocales(String s) {
        String d = s.replaceAll("\\D", "");
        if (d.length() == 11 && d.startsWith("51")) d = d.substring(2);
        return d;
    }

    /** Número dicho en grupos para que se entienda: "987 654 321". */
    public static String numeroParaVoz(String s) {
        String d = soloDigitosLocales(s);
        // Cifra por cifra, en grupos de tres con una pausa: "9 8 7, 6 5 4, 3 2 1"
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < d.length(); i++) {
            if (i > 0 && i % 3 == 0) b.append(", ");
            b.append(d.charAt(i)).append(' ');
        }
        return b.toString().trim();
    }

    public static String nombreCategoria(String cat) {
        switch (cat) {
            case CLIENTE: return "Cliente";
            case FAMILIA: return "Familia";
            case SILENCIAR: return "Silenciado";
            case NUEVO: return "Número nuevo";
            default: return "Sin clasificar";
        }
    }
}
