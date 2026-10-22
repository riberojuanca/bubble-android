# 8. El espacio Meta

## Por qué existe

Facebook e Instagram se cierran solas apenas arrancan dentro de la burbuja. La causa es una versión vieja de la biblioteca Firebase de Google que traen las dos apps: cuando Google está en el teléfono pero falta en el perfil, toma un camino roto y se cae. Es un error de esas apps, no algo que Bubble pueda arreglar desde afuera. (Detalles técnicos, en inglés, en [ingeniería inversa](../../reverse-engineering/instagram-firebase-crash.md).)

El espacio Meta es un **perfil de trabajo aparte** donde sí funcionan: ahí Google Play Services está **instalado pero apagado**. Las apps lo ven, toman un camino que falla limpio y siguen funcionando sin notificaciones de Google. Google nunca corre.

## Qué ganás y qué aceptás

**Ganás**
- Facebook e Instagram funcionan, aisladas de tus contactos, fotos, archivos y demás apps del teléfono normal.
- La burbuja sigue sin Google.

**Aceptás**
- Android deja que cualquiera vuelva a prender Google desde Ajustes (el botón **Habilitar** de Google Play Services en el perfil de trabajo). Bubble no puede bloquear ese botón. Si pasa, el guardián vuelve a apagar Google, pero mientras tanto Google podría ver las apps de este espacio, nunca la burbuja.
- El Google del teléfono sabe que el espacio Meta existe y qué apps tiene. Un perfil de trabajo no está oculto como la burbuja.
- Sin notificaciones por Google para estas apps (Instagram tiene un canal propio para algunas). Bubble borra apenas aparece el aviso «Habilitar Servicios de Google Play» que publican.

## Crearlo

**Bubble → Espacio Meta → Crear espacio Meta**. Bubble primero muestra una pantalla que explica todo lo anterior; tocá **Crear**. Tarda alrededor de un minuto y necesita Wi-Fi.

Después, **Agregar apps del teléfono** y elegí Facebook, Instagram u otras. Tienen internet por Wi-Fi y datos móviles desde el principio; el teclado no.

## Usarlo

Sus apps están en la pestaña **Trabajo** del cajón de apps, con un maletín. También están en [Bubble Apps y el widget](07-bubble-apps-y-widget.md).

La pestaña Trabajo tiene un interruptor **Pausar apps de trabajo**: detiene todo el espacio Meta (sin actividad de fondo ni notificaciones) hasta que lo reanudes. La burbuja y tu teléfono normal no se ven afectados.

**Archivos del espacio Meta** (Bubble → Espacio Meta) abre el explorador de archivos de Android en el almacenamiento propio del espacio Meta. Desde el espacio Meta, las fotos y archivos de la burbuja están en el selector de archivos de Android, en **Burbuja**, y al revés ([capítulo 3](03-inicio-y-burbuja.md#fotos-y-archivos-entre-espacios)).

## Para tener en cuenta

- **El nombre de la pestaña Trabajo y el maletín** los pone Android; no se pueden cambiar.
- **Facebook e Instagram siguen instaladas en tu teléfono normal**: las copias del espacio Meta se actualizan desde ahí. Podés no usar las de afuera. Si las desinstalás afuera, las copias de Meta siguen funcionando pero dejan de actualizarse.
- **Avisos de «Habilitar Google Play Services»**: Facebook o Instagram pueden pedirte que actives Google. Ignoralo o cancelalo; las apps funcionan sin eso. Bubble apaga los Ajustes propios del espacio Meta para que estas apps no puedan abrir esa pantalla solas.
- **Ubicación**: las apps no tienen permiso de ubicación en el espacio Meta. Igual pueden estimar dónde estás por tu conexión a internet y tu cuenta.

## Borrarlo

**Bubble → Espacio Meta → Borrar el espacio Meta**. Se pierden sus apps y datos; la burbuja y el teléfono normal no cambian.
