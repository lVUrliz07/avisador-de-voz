package pe.glp.avisador;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Pantalla única: estado y permisos, ajustes de voz, y la lista de quién
 * escribió o llamó, con botones para marcar a cada uno como Cliente o Familia.
 */
public class MainActivity extends Activity implements Registro.Oyente {
    private static final int AZUL = 0xFF102A4C, NARANJA = 0xFFE8750A, VERDE = 0xFF155A39,
            ROJO = 0xFF931F17, GRIS = 0xFF3B4A61, FONDO = 0xFFEEF2F7, BLANCO = 0xFFFFFFFF;
    private static final int[] RECORDATORIOS = { 0, 5, 10, 15, 30 };

    private Ajustes ajustes;
    private Contactos contactos;
    private Registro registro;
    private Anunciador anunciador;

    private TextView estado;
    private LinearLayout pasos;
    private LinearLayout lista;
    private Button botonPausa;
    private Button botonRecordatorio;
    private Button botonSilencio;
    private LinearLayout voces;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ajustes = new Ajustes(this);
        contactos = new Contactos(this);
        registro = Registro.get(this);
        anunciador = Anunciador.get(this);
        construir();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registro.agregarOyente(this);
        pintarEstado();
        pintarLista();
        pintarVoces();
    }

    private void pintarVoces() {
        voces.removeAllViews();
        List<android.speech.tts.Voice> lista = anunciador.vocesEspanol();
        if (lista.isEmpty()) {
            voces.addView(texto("Cargando voces… si no aparecen, toca de nuevo.", 14, GRIS, false));
            Button r = boton("Ver voces", BLANCO, AZUL);
            r.setOnClickListener(v -> pintarVoces());
            voces.addView(r);
            return;
        }
        String elegida = ajustes.voz();
        int n = 0;
        for (android.speech.tts.Voice v : lista) {
            if (n++ >= 12) break;
            String pais = v.getLocale().getCountry().isEmpty() ? "" : " " + v.getLocale().getCountry();
            String etiqueta = "Voz " + n + pais + (Anunciador.pareceHombre(v) ? " · hombre" : "")
                    + (v.isNetworkConnectionRequired() ? " · usa internet" : "");
            boolean es = v.getName().equals(elegida);
            Button b = boton((es ? "✓ " : "") + etiqueta, es ? AZUL : BLANCO, es ? BLANCO : AZUL);
            b.setOnClickListener(x -> {
                ajustes.setVoz(v.getName());
                anunciador.aplicarVoz();
                anunciador.probarVoz(v.getName());
                pintarVoces();
            });
            voces.addView(b);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        registro.quitarOyente(this);
    }

    @Override
    public void cambio() {
        runOnUiThread(this::pintarLista);
    }

    /* ======================= Construcción de la pantalla ======================= */

    private void construir() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(FONDO);
        scroll.setFitsSystemWindows(true);
        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setPadding(dp(16), dp(20), dp(16), dp(32));
        scroll.addView(raiz);

        TextView titulo = texto("Avisador de voz", 26, AZUL, true);
        raiz.addView(titulo);
        raiz.addView(texto("Dice en voz alta quién te escribe o te llama, para saber cuándo contestar.", 15, GRIS, false));

        // ---- Botón grande para silenciar / activar ----
        botonSilencio = boton("", ROJO, BLANCO);
        botonSilencio.setTextSize(20);
        botonSilencio.setMinHeight(dp(68));
        botonSilencio.setOnClickListener(v -> {
            boolean activar = !ajustes.activo();
            ajustes.setActivo(activar);
            if (activar) ajustes.setPausaHasta(0);
            anunciador.alCambiarSilencio(activar);
            pintarEstado();
        });
        raiz.addView(botonSilencio);
        raiz.addView(texto("También puedes silenciarlo desde el panel rápido (desliza la barra de arriba con dos dedos → lápiz ✏️ → arrastra «Avisador de voz»).", 13, GRIS, false));

        // ---- Estado y permisos ----
        LinearLayout tarjetaEstado = tarjeta(raiz);
        estado = texto("", 16, AZUL, true);
        tarjetaEstado.addView(estado);
        pasos = new LinearLayout(this);
        pasos.setOrientation(LinearLayout.VERTICAL);
        tarjetaEstado.addView(pasos);

        Button probar = boton("🔊  Probar voz", NARANJA, Color.BLACK);
        probar.setOnClickListener(v -> anunciador.probar());
        tarjetaEstado.addView(probar);

        // ---- Ajustes ----
        LinearLayout tarjetaAjustes = tarjeta(raiz);
        tarjetaAjustes.addView(texto("Ajustes", 18, AZUL, true));


        TextView etiquetaVol = texto("Volumen de la voz: " + ajustes.volumen() + "%", 15, GRIS, false);
        tarjetaAjustes.addView(etiquetaVol);
        SeekBar volumen = new SeekBar(this);
        volumen.setMax(100);
        volumen.setProgress(ajustes.volumen());
        volumen.setPadding(dp(8), dp(12), dp(8), dp(12));
        volumen.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                etiquetaVol.setText("Volumen de la voz: " + Math.max(10, p) + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) {
                ajustes.setVolumen(s.getProgress());
                anunciador.hablar("Así de fuerte.");
            }
        });
        tarjetaAjustes.addView(volumen);

        tarjetaAjustes.addView(interruptor("Leer también lo que escriben los clientes", ajustes.leerTexto(), v -> ajustes.setLeerTexto(v)));
        tarjetaAjustes.addView(interruptor("Avisar mensajes de la familia", ajustes.anunciarFamilia(), v -> ajustes.setAnunciarFamilia(v)));
        tarjetaAjustes.addView(interruptor("Sonido corto antes de la voz", ajustes.tono(), v -> ajustes.setTono(v)));
        tarjetaAjustes.addView(interruptor("No hablar de noche (10 pm a 6 am)", ajustes.silencioNocturno(), v -> ajustes.setSilencioNocturno(v)));

        botonRecordatorio = boton("", BLANCO, AZUL);
        botonRecordatorio.setOnClickListener(v -> {
            int actual = ajustes.recordatorioMin(), sig = RECORDATORIOS[0];
            for (int i = 0; i < RECORDATORIOS.length; i++) {
                if (RECORDATORIOS[i] == actual) { sig = RECORDATORIOS[(i + 1) % RECORDATORIOS.length]; break; }
            }
            ajustes.setRecordatorioMin(sig);
            pintarRecordatorio();
        });
        tarjetaAjustes.addView(botonRecordatorio);
        pintarRecordatorio();

        botonPausa = boton("", BLANCO, AZUL);
        botonPausa.setOnClickListener(v -> {
            if (System.currentTimeMillis() < ajustes.pausaHasta()) ajustes.setPausaHasta(0);
            else ajustes.setPausaHasta(System.currentTimeMillis() + 60 * 60 * 1000L);
            pintarEstado();
        });
        tarjetaAjustes.addView(botonPausa);

        // ---- Voz ----
        LinearLayout tarjetaVoz = tarjeta(raiz);
        tarjetaVoz.addView(texto("Voz", 18, AZUL, true));
        tarjetaVoz.addView(texto("Toca una voz para escucharla y elegirla. Las marcadas «hombre» suelen ser voces masculinas.", 14, GRIS, false));
        voces = new LinearLayout(this);
        voces.setOrientation(LinearLayout.VERTICAL);
        tarjetaVoz.addView(voces);
        tarjetaVoz.addView(interruptor("Voz más grave", ajustes.tonoVoz() < 0.95f, v -> {
            ajustes.setTonoVoz(v ? 0.85f : 1.0f);
            anunciador.probar();
        }));
        tarjetaVoz.addView(interruptor("Hablar más despacio", ajustes.velocidad() < 0.9f, v -> {
            ajustes.setVelocidad(v ? 0.8f : 0.95f);
            anunciador.probar();
        }));

        // ---- Lista ----
        LinearLayout tarjetaLista = tarjeta(raiz);
        tarjetaLista.addView(texto("Quién escribió o llamó", 18, AZUL, true));
        tarjetaLista.addView(texto("Marca a cada uno como Cliente o Familia una sola vez. Desde entonces la voz lo dice así: «Cliente Juan Pérez te escribe».", 14, GRIS, false));
        lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        tarjetaLista.addView(lista);
        Button borrar = boton("Borrar lista", BLANCO, ROJO);
        borrar.setOnClickListener(v -> registro.borrarTodo());
        tarjetaLista.addView(borrar);

        raiz.addView(texto("Software libre (MIT). Sin internet: nada sale de este teléfono.", 12, GRIS, false));
        setContentView(scroll);
    }

    /* ======================= Estado ======================= */

    private void pintarEstado() {
        pasos.removeAllViews();
        boolean permiso = tienePermisoNotificaciones();
        boolean bateria = sinAhorroBateria();
        String voz = anunciador.problemaVoz();

        if (!permiso) {
            estado.setText("⚠ Falta un permiso para empezar");
            estado.setTextColor(ROJO);
        } else if (System.currentTimeMillis() < ajustes.pausaHasta()) {
            estado.setText("⏸ En pausa hasta las " + hora(ajustes.pausaHasta()));
            estado.setTextColor(NARANJA);
        } else if (!ajustes.activo()) {
            estado.setText("🔇 Avisos desactivados");
            estado.setTextColor(NARANJA);
        } else {
            estado.setText("✅ Escuchando WhatsApp, Messenger, SMS y llamadas");
            estado.setTextColor(VERDE);
        }

        if (!permiso) {
            pasos.addView(texto("1. Toca el botón y activa «Avisador de voz» en la lista.", 15, AZUL, false));
            Button b = boton("Dar permiso de notificaciones", AZUL, BLANCO);
            b.setOnClickListener(v -> abrirPermisoNotificaciones());
            pasos.addView(b);
            if (Build.VERSION.SDK_INT >= 33) {
                pasos.addView(texto("Si sale «Configuración restringida»: entra a Información de la app → toca los tres puntos ⋮ arriba a la derecha → «Permitir configuración restringida», y vuelve a intentar.", 14, GRIS, false));
                Button info = boton("Abrir información de la app", BLANCO, AZUL);
                info.setOnClickListener(v -> abrir(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
                pasos.addView(info);
            }
        }
        if (!bateria) {
            pasos.addView(texto("2. Para que Android no la apague por ahorrar batería:", 15, AZUL, false));
            Button b = boton("Permitir que funcione siempre", BLANCO, AZUL);
            b.setOnClickListener(v -> abrir(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))));
            pasos.addView(b);
        }
        if (voz != null) pasos.addView(texto("⚠ " + voz, 14, ROJO, false));
        if (permiso && !anunciador.conectado()) {
            pasos.addView(texto("Conectando con las notificaciones… si en un minuto no dice «Escuchando», apaga y vuelve a encender el permiso.", 14, GRIS, false));
        }
        botonSilencio.setText(ajustes.activo() ? "🔇  Silenciar la voz" : "🔊  Activar la voz");
        GradientDrawable fs = new GradientDrawable();
        fs.setColor(ajustes.activo() ? ROJO : VERDE);
        fs.setCornerRadius(dp(14));
        botonSilencio.setBackground(fs);
        botonPausa.setText(System.currentTimeMillis() < ajustes.pausaHasta() ? "▶ Reanudar ahora" : "⏸ Pausar 1 hora (reunión, misa…)");
    }

    private void pintarRecordatorio() {
        int m = ajustes.recordatorioMin();
        String cuando = m <= 0 ? "NO" : m >= 30 ? "a los " + m + " y 60 min" : "a los " + m + ", 30 y 60 min";
        botonRecordatorio.setText("Recordar mensajes sin contestar: " + cuando);
    }

    private boolean tienePermisoNotificaciones() {
        String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        ComponentName cn = new ComponentName(this, AvisoService.class);
        return s != null && (s.contains(cn.flattenToString()) || s.contains(cn.flattenToShortString()));
    }

    private boolean sinAhorroBateria() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void abrirPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= 30) {
            Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS);
            i.putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    new ComponentName(this, AvisoService.class).flattenToString());
            if (abrir(i)) return;
        }
        abrir(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    private boolean abrir(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    /* ======================= Lista ======================= */

    private void pintarLista() {
        if (lista == null) return;
        lista.removeAllViews();
        List<Registro.Evento> eventos = registro.lista();
        if (eventos.isEmpty()) {
            lista.addView(texto("Todavía no hay avisos. Cuando alguien escriba o llame, aparecerá aquí.", 14, GRIS, false));
            return;
        }
        int n = 0;
        for (Registro.Evento e : eventos) {
            if (n++ >= 40) break;
            lista.addView(fila(e));
        }
    }

    private View fila(Registro.Evento e) {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(0, dp(10), 0, dp(10));

        String cat = contactos.categoria(e.quien);
        String que;
        switch (e.tipo) {
            case "llamada": que = "📞 Llamada"; break;
            case "perdida": que = "📵 Llamada perdida"; break;
            default: que = e.cantidad > 1 ? "💬 " + e.cantidad + " mensajes" : "💬 Mensaje";
        }
        TextView linea1 = texto(e.quien, 17, AZUL, true);
        f.addView(linea1);
        String est = "llamada".equals(e.tipo) ? "" : (e.atendido ? "  ·  ✓ visto" : "  ·  ⏳ sin responder");
        TextView linea2 = texto(hora(e.hora) + "  ·  " + que + " por " + e.app + "  ·  " + Contactos.nombreCategoria(cat) + est, 14,
                (!e.atendido && !"llamada".equals(e.tipo)) ? ROJO : GRIS, false);
        f.addView(linea2);

        LinearLayout botones = new LinearLayout(this);
        botones.setOrientation(LinearLayout.HORIZONTAL);
        botones.addView(chip("Cliente", Contactos.CLIENTE.equals(cat), v -> marcar(e.quien, Contactos.CLIENTE, cat)));
        botones.addView(chip("Familia", Contactos.FAMILIA.equals(cat), v -> marcar(e.quien, Contactos.FAMILIA, cat)));
        botones.addView(chip("Silenciar", Contactos.SILENCIAR.equals(cat), v -> marcar(e.quien, Contactos.SILENCIAR, cat)));
        f.addView(botones);

        View raya = new View(this);
        raya.setBackgroundColor(0xFFD5DCE6);
        LinearLayout cont = new LinearLayout(this);
        cont.setOrientation(LinearLayout.VERTICAL);
        cont.addView(f);
        cont.addView(raya, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        return cont;
    }

    /** Tocar la categoría que ya tiene la quita (vuelve a "sin clasificar"). */
    private void marcar(String quien, String nueva, String actual) {
        contactos.asignar(quien, nueva.equals(actual) ? null : nueva);
        pintarLista();
    }

    /* ======================= Ayudantes de vista ======================= */

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView texto(String s, int sp, int color, boolean negrita) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setPadding(0, dp(4), 0, dp(4));
        if (negrita) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout tarjeta(LinearLayout padre) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable fondo = new GradientDrawable();
        fondo.setColor(BLANCO);
        fondo.setCornerRadius(dp(14));
        fondo.setStroke(dp(1), 0xFFC9D3E0);
        t.setBackground(fondo);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        padre.addView(t, lp);
        return t;
    }

    private Button boton(String s, int fondoColor, int textoColor) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(textoColor);
        b.setMinHeight(dp(52));
        GradientDrawable fondo = new GradientDrawable();
        fondo.setColor(fondoColor);
        fondo.setCornerRadius(dp(10));
        fondo.setStroke(dp(2), fondoColor == BLANCO ? textoColor : fondoColor);
        b.setBackground(fondo);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private Button chip(String s, boolean activo, View.OnClickListener al) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setMinHeight(dp(40));
        b.setMinimumHeight(dp(40));
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setTextColor(activo ? BLANCO : AZUL);
        GradientDrawable fondo = new GradientDrawable();
        fondo.setColor(activo ? AZUL : BLANCO);
        fondo.setCornerRadius(dp(20));
        fondo.setStroke(dp(1), AZUL);
        b.setBackground(fondo);
        b.setOnClickListener(al);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40));
        lp.rightMargin = dp(8);
        lp.topMargin = dp(6);
        b.setLayoutParams(lp);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private interface AlCambiar { void cambio(boolean valor); }

    private Switch interruptor(String s, boolean valor, AlCambiar al) {
        Switch w = new Switch(this);
        w.setText(s);
        w.setTextSize(15);
        w.setTextColor(AZUL);
        w.setChecked(valor);
        w.setPadding(0, dp(12), 0, dp(12));
        w.setOnCheckedChangeListener((v, c) -> {
            al.cambio(c);
            pintarEstado();
        });
        return w;
    }

    private static String hora(long t) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(t));
    }
}
