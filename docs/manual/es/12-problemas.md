# 12. Solución de problemas

Empezá por **Ajustes → Registro técnico**: muestra lo último que hizo el motor y por qué falló.

## Configuración y conexión

**«No se pudo conectar a la depuración inalámbrica. Revisá que el Wi-Fi esté prendido.»**
La depuración inalámbrica solo funciona con Wi-Fi. Conectate a un Wi-Fi y probá de nuevo. En una red nueva, Android puede preguntar *«¿Permitir la depuración inalámbrica en esta red?»*: marcá **Permitir siempre** y aceptá.

**«Esta operación de configuración necesita Wi-Fi.»**
Crear espacios, instalar y el mantenimiento usan la depuración inalámbrica. Abrir apps y cambiar su acceso a internet, no.

**Rechaza el código de vinculación.**
Abrí de nuevo *Vincular dispositivo con código de vinculación* (el código cambia cada vez), dejá el diálogo abierto, tocá **Vincular** en Bubble y escribí el código nuevo en la notificación de Bubble.

**Bubble se vinculó una vez pero ahora no puede conectarse.**
Si revocaste las autorizaciones de depuración o restableciste el teléfono, usá **Ajustes → Volver a vincular**.

## La burbuja

**«La burbuja sigue bloqueada.»**
Cancelaste el desbloqueo, o Android no lo mostró. Probá de nuevo; Bubble le vuelve a pedir a Android si la pantalla de desbloqueo no aparece en unos segundos.

**«No se pudo comunicar con el perfil. Desbloquealo y volvé a intentar.»**
La burbuja (o el espacio Meta) está bloqueada o pausada, o falta su copia de Bubble. Desbloqueala y reintentá. Si sigue, abrí Bubble con Wi-Fi para que repare el perfil.

**«Abrí Bubble una vez con su conexión de configuración para terminar de preparar este perfil.»**
El firewall del perfil nunca se configuró. Abrí Bubble con Wi-Fi.

**Falta la sección Privado en el cajón de apps.**
Solo aparece si la burbuja tiene al menos una app. Bubble reinstala F-Droid en su próximo mantenimiento.

**Las apps de la burbuja desaparecen de las apps recientes.**
Está arreglado en las versiones actuales. Si creaste la burbuja con una versión vieja, las apps abiertas antes de actualizar pueden seguir ocultas hasta que las cierres y las vuelvas a abrir.

## Apps

**Una app se cierra al arrancar dentro de la burbuja.**
- Facebook, Instagram (y otras apps con un Firebase viejo): usá el [espacio Meta](08-espacio-meta.md).
- Una app que comparte o «abre con»: las versiones actuales incluyen el selector para compartir de Android en la burbuja; abrí Bubble con Wi-Fi una vez para que se agregue en burbujas viejas.

**Una app no tiene internet.**
Revisá sus íconos de Wi-Fi y datos móviles en Bubble → Apps, y qué red está usando el teléfono. Si todas las apps de la burbuja se quedaron sin internet y ves *«Apps de este espacio sin internet»*, abrí Bubble para que el firewall reciba su lista otra vez.

**Una app pide mi huella pero no puede usarla.**
Dale a la burbuja su propio bloqueo ([capítulo 6](06-bloqueo.md)).

**Una app de banco abre pero no deja iniciar sesión.**
Lo más probable es que exija Play Integrity, que no puede pasar sin los servicios de Google. No puede funcionar en la burbuja.

**Una app no recuerda mi sesión.**
Algunas apps guardan las sesiones con los servicios de Google, que la burbuja no tiene.

## Espacio Meta

**Facebook o Instagram muestran «Habilitar Google Play Services».**
Cancelalo. Las apps funcionan sin eso.

**Se habilitó Google en el espacio Meta.**
Bubble muestra un aviso. Abrí Bubble: el guardián lo apaga en menos de un minuto.

## Música y bloqueo

**La música se corta al apagar la pantalla.**
Revisá que la app avise que está reproduciendo (que se vea su notificación de música). Si cambiaste el bloqueo automático del espacio privado en los Ajustes de Android, Bubble lo restablece en su próximo mantenimiento.

**La burbuja no se bloquea al apagar la pantalla.**
Algo adentro está sonando. Paralo y la burbuja se bloquea unos segundos después.

## Reportar un problema

Abrí un *issue* en GitHub (en inglés si podés) con: tu teléfono y versión de Android, qué hiciste, qué esperabas, qué pasó, y el **registro técnico** (sacale antes cualquier dato personal). Para problemas de seguridad, ver [SECURITY.md](../../../SECURITY.md).
