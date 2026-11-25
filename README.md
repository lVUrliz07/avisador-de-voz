# Avisador de voz 🔊

App de Android, **software libre (MIT)**, que dice en voz alta **quién te escribe o te llama**, usando el nombre guardado en el teléfono:

- «**Cliente Juan Pérez te escribe.**»
- «**Cliente Juan Pérez te escribió 3 mensajes por Messenger.**»
- «**Familia: Mamá te escribe.**»
- «**Número nuevo 9 8 7, 6 5 4, 3 2 1 te escribe.**» (si no tiene nombre en la agenda, dice el número cifra por cifra)
- «**Llamada de cliente Juan Pérez.**» (se repite mientras suena)
- «**Llamada perdida de cliente Juan Pérez.**»
- «**Hay un mensaje sin contestar de cliente Juan Pérez, de hace 10 minutos.**» (y otra vez a los 30 y a los 60)

Así se sabe, sin mirar el celular, **cuándo contestar y cuándo no**.

**No cambia nada en la forma de trabajar:** WhatsApp, Messenger y el teléfono se siguen usando igual. La app solo **escucha las notificaciones** que ya llegan. No abre WhatsApp, no responde nada y **no tiene permiso de internet**: ningún mensaje, ni de clientes ni de la familia, sale del celular.

Escucha: WhatsApp, WhatsApp Business, Messenger (y Messenger Lite / Meta Business Suite), SMS y las llamadas normales y de WhatsApp.

---

## 1. Instalar en el celular (Android 8 o más nuevo)

1. Desde el celular, entra a la página del proyecto en GitHub → **Releases** → descarga **AvisadorDeVoz.apk**.
2. Ábrelo. Si Android pregunta, permite **«Instalar apps desconocidas»** para el navegador o «Archivos».
3. Abre **Avisador de voz** y sigue los pasos de la pantalla:
   1. **Dar permiso de notificaciones** → activa «Avisador de voz».
      - En Android 13 o más nuevo puede salir **«Configuración restringida»**. Toca **«Abrir información de la app»** → los **tres puntos ⋮** arriba a la derecha → **«Permitir configuración restringida»**, y vuelve a dar el permiso.
   2. **Permitir que funcione siempre** (que Android no la apague para ahorrar batería).
4. Toca **«Probar voz»**. Si no suena en español: Ajustes del teléfono → Sistema → Idioma → **Salida de texto a voz** → elige el motor de Google y descarga **Español**.

**Xiaomi / Redmi / POCO:** además, en Información de la app activa **«Inicio automático»** y en Ahorro de batería elige **«Sin restricciones»**. **Huawei:** Inicio de apps → Gestionar manualmente → activar todo. **Samsung:** Batería → «Sin restricciones».

## Silenciar rápido

- En la app: el botón grande **«Silenciar la voz»** / **«Activar la voz»**.
- Sin abrir la app: en el **panel rápido** (donde están Wi-Fi y Linterna) agrega el botón **«Avisador de voz»**: desliza la barra de arriba con dos dedos → lápiz ✏️ → arrástralo.

## Voz y lectura

- Se puede elegir la voz (las marcadas «hombre» son masculinas), hacerla más grave o más despacio.
- El nombre se dice **una sola vez**; si el mismo chat sigue escribiendo, dice **«El mismo contacto dice: …»**.
- Antes de leer, el mensaje se limpia: sin emojis ni enlaces, abreviaturas corregidas («q», «xq», «mñn», «pls»), «holaaaa» → «hola», mayúsculas sin deletrear, «S/ 60» → «60 soles», «9am» → «9 de la mañana». Fotos y audios se anuncian como «Mandó una foto» / «Mandó un audio».

## 2. Marcar clientes y familia (una sola vez por persona)

En la lista **«Quién escribió o llamó»** aparece cada persona que escribe o llama. Toca:

- **Cliente** → la voz dice «Cliente …» y, si no se le responde, lo recuerda.
- **Familia** → la voz dice «Familia: …» (o nada, si apagas «Avisar mensajes de la familia»).
- **Silenciar** → no se anuncia nunca (grupos, publicidad, etc.).

Tocar otra vez el botón marcado lo quita. Los números que no están guardados en la agenda se anuncian como **«número nuevo»**, porque suelen ser clientes nuevos.

## 3. Ajustes

| Ajuste | Para qué sirve |
| --- | --- |
| Volumen de la voz | Habla por el canal de **alarma**: suena aunque el timbre esté en silencio o vibración. Solo sube el volumen mientras habla y luego lo devuelve como estaba. |
| Leer también lo que escriben los clientes | Además del nombre, lee el mensaje (solo de clientes y números nuevos, nunca de la familia). |
| Sonido corto antes de la voz | Un «bip» para llamar la atención antes del nombre. |
| No hablar de noche | De 10 pm a 6 am se queda callado (la lista igual se llena). |
| Recordar mensajes sin contestar | Si el chat no se abrió ni se respondió: «Hay un mensaje sin contestar de …, de hace 10 minutos», y otra vez a los 30 y a los 60. También para llamadas perdidas. La familia no se recuerda. El primer aviso se puede poner a los 5, 10, 15 o 30 minutos, o apagar. |
| Pausar 1 hora | Para reuniones, misa, etc. |

## 4. Cuándo se calla

- **Llamada:** apenas el jefe **contesta** (o cuelga), la voz se corta al instante y no se repite.
- **Mensaje:** apenas **abre el chat o responde**, se borra lo que faltaba decir de esa persona y se cancelan sus recordatorios.
- **Durante una llamada** no habla encima de la conversación.

## 5. Cómo sabe si ya se respondió

- Si el jefe **abre el chat** o **responde** (en el celular o en WhatsApp Web), WhatsApp quita la notificación y la app lo marca **«✓ visto»**.
- Si la notificación sigue ahí, queda **«⏳ sin responder»** en rojo y se recuerda.

## 6. Límites (son de Android y WhatsApp, no de la app)

- Si el chat **ya está abierto en pantalla** cuando llega el mensaje, WhatsApp no crea notificación y no se anuncia (porque ya se está viendo).
- Los chats **silenciados** en WhatsApp no generan notificación: no se anuncian.
- Las notificaciones de WhatsApp deben estar **activadas** (pueden estar sin sonido si se quiere solo la voz).
- Si el celular la cierra por batería, deja de hablar: por eso el paso 2 de la instalación es importante.

---

## Para programadores: compilar

El APK se compila solo en GitHub con el flujo `.github/workflows/compilar-apk.yml` cada vez que se sube un cambio a `main`, y se publica en **Releases**.

En una PC con Android Studio o con el SDK de Android y Gradle 8.9+:

```bash
gradle assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

**Firma propia (recomendado)** — sin ella, cada versión nueva se firma con una llave distinta y hay que **desinstalar** antes de actualizar. Una sola vez:

```bash
keytool -genkeypair -v -keystore avisador.jks -alias avisador -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 avisador.jks   # copia el resultado
```

En GitHub → Settings → Secrets and variables → Actions, crea:

- `AVISADOR_KEYSTORE_B64` = el texto en base64
- `AVISADOR_KEY_PASS` = la contraseña que pusiste

Guarda `avisador.jks` en un lugar seguro: sin ella no se pueden publicar actualizaciones que se instalen encima.

### Cómo está hecho

| Archivo | Qué hace |
| --- | --- |
| `AvisoService.java` | Servicio de Android que recibe cada notificación (permiso «acceso a notificaciones»). |
| `Anunciador.java` | Decide si es mensaje, llamada o llamada perdida; quién es; junta ráfagas de mensajes (2,5 s); arma la frase y la dice con la voz del teléfono; recordatorios. |
| `Contactos.java` | Cliente / Familia / Silenciar por persona; detecta números no guardados. |
| `Registro.java` | Últimos 150 avisos y si fueron atendidos. |
| `MainActivity.java` | La pantalla: permisos, ajustes y lista. |

Sin librerías externas: solo Android. Sin permiso de internet.

Licencia MIT.
