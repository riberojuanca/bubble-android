# 3. La pantalla de inicio y la burbuja

## La pantalla de inicio de Bubble

Arriba hay una línea de estado, por ejemplo **Lista · sin servicios de Google**, y un pequeño **ícono de espía** en la esquina (el guardián, ver el [capítulo 10](10-ajustes-y-guardian.md)). Debajo, una fila por área:

| Fila | Abre |
|---|---|
| **Burbuja** | El estado de la burbuja, revisarla, su bloqueo con huella, borrarla |
| **Gestionar apps** | Las apps dentro de la burbuja, con su acceso a internet ([capítulo 4](04-apps.md), [capítulo 5](05-internet.md)) |
| **Bubble Apps** | Abre la cuadrícula para poner apps y el widget en la pantalla de inicio ([capítulo 7](07-bubble-apps-y-widget.md)) |
| **Espacio Meta** | El espacio opcional para Facebook e Instagram ([capítulo 8](08-espacio-meta.md)) |
| **Firewall** | El firewall de tu teléfono normal ([capítulo 9](09-firewall-del-telefono.md)) |
| **Ajustes** | Volver a vincular, el registro técnico, quitar todo, la versión |

Deslizá la pantalla hacia abajo para revisar de nuevo. Bubble revisa solo al abrirlo (como mucho cada un par de minutos) y cada diez minutos mientras sigue abierto.

### Mensajes de estado

| Mensaje | Significa |
|---|---|
| Lista · sin servicios de Google | Todo está como debe |
| Revisando la burbuja… | Bubble le está pidiendo su estado a la burbuja |
| Todavía no hay burbuja | Bubble muestra el asistente inicial |
| No se pudo revisar la burbuja ahora | La burbuja no respondió; probá de nuevo o mirá [solución de problemas](12-problemas.md) |
| Atención: metieron servicios de Google en la burbuja (N) | Algo instaló servicios de Google adentro; el guardián los desactivó. Para sacarlos del todo, reemplazá la burbuja |
| Atención: servicios de Google activos en la burbuja: N | Abrí Bubble de nuevo para que el guardián los desactive |
| Hay un espacio privado sin servicios de Google que todavía no es la burbuja | Existe un espacio privado que Bubble no administra; abrí Bubble → Burbuja para decidir |

Mientras la burbuja está bloqueada, Bubble muestra el último estado que leyó; dentro de una burbuja bloqueada no se puede preguntar nada. Mientras nada necesita atención, el inicio dice **Protegida 🛡 y cerrada 🔒** (o **y abierta 🔓**). Tocá el escudo para ver el detalle de la protección; tocá el candado para desbloquear la burbuja con el pedido de huella o PIN de Android, o para bloquearla al instante. Todo lo que necesita la burbuja abierta (sus apps, archivos, fotos o Descargas) también pide la huella solo.

## La pantalla Burbuja

- **Estado y detalles**: el número de usuario del espacio privado y cuántos servicios de Google hay instalados y activos adentro (los dos deberían ser 0).
- **Cómo entrar**: un recordatorio de que la burbuja es la sección *Privado* al final del cajón de apps.
- **Revisar ahora**: le pide su estado a la burbuja.
- **Huella**: una nota sobre darle a la burbuja su propio bloqueo para que sus apps puedan usar tu huella, con **Abrir ajustes de seguridad** ([capítulo 6](06-bloqueo.md)).
- **Eliminar y crear una burbuja nueva**: reemplaza la burbuja. Se pierden sus apps y datos.
- **Borrar la burbuja**: la elimina sin crear otra ([capítulo 11](11-quitar.md)).

## Entrar y salir de la burbuja

- **Entrar**: cajón de apps → **Privado** (al final) → desbloquear. O abrí cualquier app de la burbuja desde **Bubble Apps** o el widget ([capítulo 7](07-bubble-apps-y-widget.md)); Android te pide desbloquear primero.
- **Salir**: usá el botón de candado de la sección Privado, o simplemente apagá la pantalla: Bubble bloquea la burbuja sola al apagarse la pantalla, salvo que algo adentro esté sonando ([capítulo 6](06-bloqueo.md)).

Con la burbuja bloqueada, Android detiene todas sus apps y mantiene su almacenamiento cifrado. Sus apps no corren, no usan la red ni notifican hasta que la vuelvas a abrir.

La sección Privado solo aparece en el cajón de apps si la burbuja tiene al menos una app. Por eso Bubble siempre deja F-Droid ahí.

## Usar fotos y archivos de la burbuja en el teléfono

Las fotos y descargas guardadas dentro de la burbuja (con Immich o con un navegador de ahí) se quedan en la burbuja. Bubble te deja llegar a ellas desde el teléfono normal sin mover lo demás:

- **Fondo desde Fondo de pantalla y estilo**: activá Bubble → Ajustes → **Fondos con fotos de la burbuja**. Después, Fondo de pantalla y estilo → *Elegir una foto* sigue abriendo Google Fotos, con un botón **Fotos de la burbuja** encima: tocalo, abrí una carpeta y tocá una foto. Se abre la pantalla de fondo de pantalla de Android para recortarla y ponerla en la pantalla de inicio, la de bloqueo o las dos. La app de fondos del Pixel siempre le pide la foto a Google Fotos por nombre, y Google Fotos solo muestra las fotos del teléfono normal; por eso las de la burbuja llegan con ese botón. Usa un servicio de accesibilidad que solo ve qué pantalla de esas dos apps está abierta, nunca lo que muestra.
- **Fondo desde Bubble**: Bubble → Burbuja → **Fotos de la burbuja**, y los mismos pasos.
- **Cualquier app que pida una imagen** puede elegir **Fotos de la burbuja** entre las apps que ofrece, o en el selector de archivos de Android (menú → **Explorar** → **Fotos de la burbuja**).
- **Archivos de la burbuja**: Bubble → Burbuja → **Archivos de la burbuja** abre el explorador de archivos de Android dentro de la burbuja, en su propio almacenamiento (sus Descargas y carpetas, aparte de las del teléfono).

Si la burbuja está bloqueada, Android te pide la huella o el PIN ahí mismo y después se abren las fotos o los archivos. Solo sale de la burbuja la foto que elegís, y Bubble no guarda ninguna copia; la app a la que se la das puede guardar la suya (Android guarda la imagen del fondo, por ejemplo).
