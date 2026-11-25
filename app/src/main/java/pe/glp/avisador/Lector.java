package pe.glp.avisador;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prepara el texto de un mensaje para que la voz lo lea bien:
 * quita emojis y enlaces, corrige abreviaturas de chat ("q", "xq", "pls"),
 * letras repetidas ("holaaaa"), palabras en MAYÚSCULAS (que la voz deletrea)
 * y unidades del negocio ("S/ 60", "10kg").
 */
public final class Lector {
    private Lector() { }

    private static final Map<String, String> ABREVIATURAS = new LinkedHashMap<>();
    static {
        String[][] a = {
            {"q", "que"}, {"k", "que"}, {"ke", "que"}, {"xq", "porque"}, {"pq", "porque"}, {"porq", "porque"},
            {"x", "por"}, {"xfa", "por favor"}, {"xfavor", "por favor"}, {"porfa", "por favor"}, {"pls", "por favor"}, {"plis", "por favor"},
            {"tb", "también"}, {"tmb", "también"}, {"tbn", "también"}, {"bn", "bien"}, {"d", "de"}, {"dnd", "dónde"},
            {"cdo", "cuando"}, {"cnd", "cuando"}, {"msj", "mensaje"}, {"mñn", "mañana"}, {"mñna", "mañana"}, {"mnn", "mañana"},
            {"hrs", "horas"}, {"hr", "hora"}, {"min", "minutos"}, {"ok", "okey"}, {"oka", "okey"}, {"okis", "okey"},
            {"grax", "gracias"}, {"grs", "gracias"}, {"gcs", "gracias"}, {"slds", "saludos"}, {"bs", "besos"},
            {"tqm", "te quiero mucho"}, {"tkm", "te quiero mucho"}, {"ntp", "no te preocupes"}, {"nd", "nada"},
            {"aki", "aquí"}, {"aka", "acá"}, {"sra", "señora"}, {"sr", "señor"}, {"srta", "señorita"}, {"dr", "doctor"},
            {"av", "avenida"}, {"jr", "jirón"}, {"mz", "manzana"}, {"lt", "lote"}, {"urb", "urbanización"}, {"dpto", "departamento"},
            {"nro", "número"}, {"n°", "número"}, {"cel", "celular"}, {"jaja", "ja ja"}, {"jajaja", "ja ja"}, {"jsjs", "ja ja"},
        };
        for (String[] p : a) ABREVIATURAS.put(p[0], p[1]);
    }

    private static final Pattern ENLACE = Pattern.compile("(https?://|www\\.)\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPETIDAS = Pattern.compile("([a-zA-ZáéíóúñÁÉÍÓÚÑ])\\1{2,}");
    private static final Pattern PALABRA = Pattern.compile("[\\p{L}°]+");
    private static final Pattern SOLES = Pattern.compile("(?i)s/\\.?\\s*(\\d+(?:[.,]\\d+)?)");
    private static final Pattern KILOS = Pattern.compile("(?i)(\\d+)\\s*(kg|kilos?|k)\\b");
    private static final Pattern HORA = Pattern.compile("(?i)\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)");

    /** Junta varios mensajes seguidos en una sola lectura, hasta un largo máximo. */
    public static String juntar(List<String> textos, int max) {
        StringBuilder b = new StringBuilder();
        for (String t : textos) {
            String l = limpiar(t);
            if (l.isEmpty()) continue;
            if (b.length() > 0) b.append(".?!".indexOf(b.charAt(b.length() - 1)) >= 0 ? " " : ". ");
            b.append(l);
            if (b.length() >= max) break;
        }
        String r = b.toString().trim();
        if (r.length() > max) {
            int corte = r.lastIndexOf(' ', max);
            r = r.substring(0, corte > 0 ? corte : max) + "… y sigue";
        }
        return r;
    }

    public static String limpiar(String t) {
        if (t == null) return "";
        String s = t;
        // Mensajes que no son texto
        String bajo = s.toLowerCase(Locale.ROOT).trim();
        if (bajo.startsWith("📷") || bajo.equals("foto") || bajo.equals("imagen")) return "Mandó una foto.";
        if (bajo.startsWith("🎤") || bajo.contains("mensaje de voz") || bajo.contains("audio")) {
            if (bajo.length() < 30) return "Mandó un audio.";
        }
        if (bajo.startsWith("🎥") || bajo.equals("video")) return "Mandó un video.";
        if (bajo.startsWith("📍") || bajo.contains("ubicación")) { if (bajo.length() < 40) return "Mandó su ubicación."; }
        if (bajo.startsWith("📄") || bajo.startsWith("📎")) return "Mandó un documento.";
        if (bajo.equals("sticker") || bajo.startsWith("💟")) return "Mandó un sticker.";

        s = ENLACE.matcher(s).replaceAll(" un enlace ");
        // Mensaje casi todo en MAYÚSCULAS: se pasa a minúsculas para que no lo deletree
        int may = 0, letras = 0;
        for (char c : s.toCharArray()) if (Character.isLetter(c)) { letras++; if (Character.isUpperCase(c)) may++; }
        if (letras > 6 && may > letras * 0.7) s = s.toLowerCase(new Locale("es"));
        // "Av." "Jr." "Dpto." → sin punto (si no, la voz hace una pausa larga)
        s = s.replaceAll("(?i)\\b(av|jr|mz|lt|urb|dpto|sr|sra|srta|dr|nro)\\.", "$1");
        s = quitarEmojis(s);
        s = SOLES.matcher(s).replaceAll("$1 soles");
        s = KILOS.matcher(s).replaceAll("$1 kilos");
        s = horas(s);
        s = REPETIDAS.matcher(s).replaceAll("$1");          // "holaaaa" → "hola"
        s = s.replaceAll("([!?¡¿.,])\\1+", "$1");           // "!!!!" → "!"
        s = palabras(s);
        s = s.replaceAll("[\\*_~`#|<>\\[\\]{}]", " ");       // marcas de formato de WhatsApp
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    /** Para nombres de contacto: sin emojis ni adornos ("Juan 🔥 Gas" → "Juan Gas"). */
    public static String limpiarNombre(String n) {
        if (n == null) return "";
        if (Contactos.esNumero(n)) return n;
        String s = quitarEmojis(n).replaceAll("[\\*_~`#|<>\\[\\]{}()]", " ").replaceAll("\\s+", " ").trim();
        s = palabras(s);
        return s.isEmpty() ? n : s;
    }

    private static String quitarEmojis(String s) {
        StringBuilder b = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            int tipo = Character.getType(cp);
            boolean emoji = tipo == Character.SURROGATE || tipo == Character.OTHER_SYMBOL
                    || (cp >= 0x1F000 && cp <= 0x1FAFF) || (cp >= 0x2600 && cp <= 0x27BF)
                    || cp == 0xFE0F || cp == 0x200D || (cp >= 0x1F3FB && cp <= 0x1F3FF);
            b.append(emoji ? " " : new String(Character.toChars(cp)));
            i += Character.charCount(cp);
        }
        return b.toString();
    }

    /** Abreviaturas y palabras en MAYÚSCULAS (la voz las deletrea letra por letra). */
    private static String palabras(String s) {
        Matcher m = PALABRA.matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String w = m.group();
            String low = w.toLowerCase(new Locale("es"));
            String r = ABREVIATURAS.get(low);
            if (r == null) {
                // Mayúsculas de más de 3 letras → minúsculas (siglas cortas como "GLP" se dejan)
                r = (w.length() > 3 && w.equals(w.toUpperCase(new Locale("es")))) ? low : w;
            }
            m.appendReplacement(out, Matcher.quoteReplacement(r));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** "9am" → "9 de la mañana", "3:30pm" → "3 y 30 de la tarde". */
    private static String horas(String s) {
        Matcher m = HORA.matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            int h = Integer.parseInt(m.group(1));
            String min = m.group(2);
            boolean pm = m.group(3).toLowerCase(Locale.ROOT).startsWith("p");
            String parte = !pm ? "de la mañana" : (h == 12 || h < 7 ? "de la tarde" : "de la noche");
            String r = h + (min != null && !min.equals("00") ? " y " + min : "") + " " + parte;
            m.appendReplacement(out, Matcher.quoteReplacement(r));
        }
        m.appendTail(out);
        return out.toString();
    }
}
