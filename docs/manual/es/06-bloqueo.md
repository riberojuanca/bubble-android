# 6. Bloqueo, huella y música

## Cuándo se bloquea la burbuja

Una burbuja bloqueada está detenida: Android cierra todas sus apps y mantiene su almacenamiento cifrado. Bubble la bloquea:

- **Al apagarse la pantalla**, si no hay nada sonando adentro.
- **En cuanto deja de sonar** (pausa, fin, error), si la pantalla sigue apagada, tras unos segundos para que la pausa entre canciones no la bloquee.
- **Cuando la bloqueás vos** con el botón de candado de la sección Privado.

Volver a abrirla siempre pide tu bloqueo de pantalla (o el bloqueo propio de la burbuja, ver abajo). Bubble puede bloquear la burbuja pero nunca desbloquearla por su cuenta.

## Notificaciones: mantener la burbuja abierta

Una burbuja bloqueada no puede recibir nada: Signal y otras apps de mensajería reciben sus mensajes recién al desbloquearla. Para que avisen como siempre, elegí **Bubble → Ajustes → Bloquear la burbuja → A mano**. La burbuja queda abierta al apagar la pantalla, y los servicios de Google del teléfono igual no pueden leer sus notificaciones. Bloqueala vos con el candado del inicio de Bubble; reiniciar también la bloquea.

## Música con la pantalla apagada: el bloqueo inteligente

El bloqueo automático del espacio privado de Android detiene la burbuja entera al bloquearse la pantalla, sin excepción para la música. Bubble lo reemplaza: pone el bloqueo automático de Android en *Solo después de reiniciar el dispositivo* y bloquea la burbuja ella misma, salvo mientras una de sus apps está sonando. Así podés escuchar NewPipe o Spotify en la burbuja con el teléfono bloqueado; cuando parás la música, la burbuja se bloquea.

Mientras algo suena, la burbuja entera queda abierta detrás de tu pantalla de bloqueo, no solo esa app. Sus apps pueden seguir publicando notificaciones y usando las redes permitidas. Esto no ofrece la misma protección que detener el perfil.

No cambies el bloqueo automático en los Ajustes de Android: lo maneja Bubble. Si lo volvés a poner en *Cada vez que se bloquea el dispositivo*, la música se va a cortar otra vez con la pantalla; Bubble lo restablece en su próximo mantenimiento.

## Huella dentro de la burbuja

De entrada, la burbuja se abre con el bloqueo de pantalla del teléfono. En ese modo **las apps de la burbuja no pueden usar tu huella**: Android no le da a la burbuja huellas propias. Una app de banco que ofrece «ingresar con huella» te manda a Ajustes, y registrar la huella desde ahí falla.

La solución es darle a la burbuja **su propio bloqueo**. Android te hace elegir el PIN en pantalla, así que Bubble no puede hacerlo por vos, pero se hace una sola vez:

1. **Abrí la burbuja** (desbloqueá la sección Privado). El cambio no se puede hacer con la burbuja bloqueada.
2. Andá a **Ajustes → Seguridad y privacidad → Espacio privado** (Bubble → Burbuja → **Abrir ajustes de seguridad** te lleva a Seguridad y privacidad).
3. Tocá **Bloqueo del espacio privado** y apagá **Usar bloqueo de pantalla del dispositivo**. Elegí **Elegir bloqueo nuevo** y confirmá el PIN del teléfono.
4. Elegí **PIN** y escribí un PIN para la burbuja (puede ser distinto del del teléfono).
5. De vuelta en Bloqueo del espacio privado, tocá **Desbloqueo con huella dactilar → Agregar huella** y registrá un dedo. Podés usar el mismo dedo que en el teléfono.

Desde ahí abrís la burbuja con ese PIN o huella, y las apps de adentro pueden usar la huella. El bloqueo inteligente funciona igual.

Los nombres de los menús pueden variar un poco según el teléfono y la versión de Android.

## Notificaciones

- **Burbuja abierta**: las apps con conexión propia (Signal, por ejemplo) notifican normalmente. Las que dependen del servicio push de Google, no.
- **Burbuja bloqueada**: ninguna app corre, así que nada notifica hasta que la abras.
- **Sonando**: la burbuja queda abierta, así que sus apps pueden notificar mientras tanto.
