# 1. Antes de empezar

## Qué es Bubble

Bubble te da un segundo espacio en el teléfono, la **burbuja**, para las apps que no querés cerca de Google ni del resto del teléfono. Dentro de la burbuja:

- **No hay servicios de Google**: ni Google Play Services, ni Play Store, ni cuenta de Google. Las apps que usan las bibliotecas de rastreo de Google a través de Play Services no obtienen nada.
- Las apps tienen **sus propios datos**, separados de la misma app en tu teléfono normal.
- Las apps **no tienen internet hasta que lo permitas**, por separado para Wi-Fi y datos móviles.
- La burbuja está **oculta**: las otras apps de tu teléfono no pueden listar sus apps.

La burbuja es la función *Espacio privado* del propio Android, armada de forma más estricta. Las apps corren de verdad, igual de rápido que en cualquier otro lado; no se emula nada.

## Qué necesitás

- Un teléfono con **Android 17** (probado en un Pixel 9a). El espacio privado existe desde Android 15, pero por ahora Bubble crea la burbuja solo en Android 17.
- **Wi-Fi** para la configuración y algunas tareas de mantenimiento. El uso diario funciona sin Wi-Fi.
- Activar una vez las **Opciones para desarrolladores** y la **Depuración inalámbrica**. Bubble explica cómo.
- Unos **dos minutos**.

**No** necesitás root, desbloquear el bootloader, una ROM modificada ni una computadora.

## Qué esperar

**Funciona bien**
- Apps de mensajería y de código abierto (Signal, NewPipe, apps de F-Droid, Bitwarden, DuckDuckGo, Immich, Spotify…).
- Música de fondo con la pantalla apagada (ver el [capítulo 6](06-bloqueo.md)).
- Facebook e Instagram, en el espacio Meta aparte.

**Funciona con límites**
- **Las notificaciones que llegan por Google** no llegan dentro de la burbuja. Las apps con conexión propia (Signal, por ejemplo) notifican normalmente mientras la burbuja está abierta.
- **Una burbuja bloqueada está detenida**: sus apps no corren ni notifican hasta que la vuelvas a abrir (salvo que algo esté sonando).
- Algunas apps no pueden **recordar tu sesión** porque la guardan con los servicios de Google.

**No funciona**
- Las apps que exigen la verificación **Play Integrity** de Google: muchas apps de bancos, Google Wallet, algunos juegos. Suelen abrir pero no dejan iniciar sesión.
- Las apps que son parte de los servicios de Google (Gmail con tu cuenta, Google Drive…). No tiene sentido aislarlas.

## Qué cambia Bubble en tu teléfono

- Crea la burbuja (un espacio privado) y, si lo pedís, el espacio Meta (un perfil de trabajo).
- Prende la depuración inalámbrica solo mientras trabaja y la deja como estaba.
- Silencia unos 66 eventos del sistema que revelarían el uso de la burbuja (también dejan de escribirse para el teléfono normal; son solo de diagnóstico).
- Les niega el «volcado de estadísticas de uso» a las apps de Google que podrían leerlo.
- Pone el bloqueo automático del espacio privado en «solo al reiniciar» y bloquea la burbuja ella misma (ver el [capítulo 6](06-bloqueo.md)).
- Solo si usás el firewall del teléfono: corta la red de las apps que elijas en el teléfono normal.

Todo se deshace con **Ajustes → Quitar todo** ([capítulo 11](11-quitar.md)).
