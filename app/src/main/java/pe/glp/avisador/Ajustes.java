package pe.glp.avisador;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

/** Preferencias del usuario, guardadas en el teléfono. */
public final class Ajustes {
    private final SharedPreferences p;

    public Ajustes(Context c) {
        p = c.getApplicationContext().getSharedPreferences("ajustes", Context.MODE_PRIVATE);
    }

    public boolean activo() { return p.getBoolean("activo", true); }
    public void setActivo(boolean v) { p.edit().putBoolean("activo", v).apply(); }

    /** Volumen de la voz, 0–100 (% del volumen de alarma). */
    public int volumen() { return p.getInt("volumen", 85); }
    public void setVolumen(int v) { p.edit().putInt("volumen", Math.max(10, Math.min(100, v))).apply(); }

    /** Leer también lo que dice el mensaje (solo clientes y números nuevos). */
    /** Nombre de la voz elegida (vacío = elegir automáticamente una de hombre). */
    public String voz() { return p.getString("voz", ""); }
    public void setVoz(String v) { p.edit().putString("voz", v).apply(); }

    /** Tono de la voz: 1.0 normal, menos = más grave. */
    public float tonoVoz() { return p.getFloat("tonoVoz", 0.85f); }
    public void setTonoVoz(float v) { p.edit().putFloat("tonoVoz", v).apply(); }

    public float velocidad() { return p.getFloat("velocidad", 0.95f); }
    public void setVelocidad(float v) { p.edit().putFloat("velocidad", v).apply(); }

    public boolean leerTexto() { return p.getBoolean("leerTexto", false); }
    public void setLeerTexto(boolean v) { p.edit().putBoolean("leerTexto", v).apply(); }

    public boolean anunciarFamilia() { return p.getBoolean("anunciarFamilia", true); }
    public void setAnunciarFamilia(boolean v) { p.edit().putBoolean("anunciarFamilia", v).apply(); }

    /** Un tono corto antes de la voz para llamar la atención. */
    public boolean tono() { return p.getBoolean("tono", true); }
    public void setTono(boolean v) { p.edit().putBoolean("tono", v).apply(); }

    /** Minutos sin respuesta antes de recordar (0 = no recordar). */
    public int recordatorioMin() { return p.getInt("recordatorioMin", 10); }
    public void setRecordatorioMin(int v) { p.edit().putInt("recordatorioMin", v).apply(); }

    public boolean silencioNocturno() { return p.getBoolean("silencioNocturno", true); }
    public void setSilencioNocturno(boolean v) { p.edit().putBoolean("silencioNocturno", v).apply(); }
    public int silencioDesde() { return p.getInt("silencioDesde", 22); }
    public int silencioHasta() { return p.getInt("silencioHasta", 6); }

    public long pausaHasta() { return p.getLong("pausaHasta", 0); }
    public void setPausaHasta(long t) { p.edit().putLong("pausaHasta", t).apply(); }

    /** ¿Ahora mismo debe quedarse callado? (pausa o noche) */
    public boolean callado() {
        if (System.currentTimeMillis() < pausaHasta()) return true;
        if (!silencioNocturno()) return false;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int desde = silencioDesde(), hasta = silencioHasta();
        return desde > hasta ? (h >= desde || h < hasta) : (h >= desde && h < hasta);
    }
}
