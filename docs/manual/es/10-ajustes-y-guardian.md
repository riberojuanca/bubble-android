# 10. Ajustes, el guardián y el registro técnico

## Ajustes

| Opción | Qué hace |
|---|---|
| **Volver a vincular** | Repite la vinculación ([capítulo 2](02-instalar-y-configurar.md)). Solo hace falta si Bubble ya no puede conectarse, por ejemplo después de revocar las autorizaciones de depuración |
| **Registro técnico** | Muestra la última salida del motor. Sirve cuando algo falla; se puede seleccionar y copiar |
| **Forma de los íconos** | La forma de los íconos de apps que dibuja Bubble (sus pantallas, el widget y los íconos de la pantalla de inicio). Elegí la misma que en Fondo de pantalla y estilo → Íconos; Android no deja que Bubble la lea. Cuadrado de entrada |
| **Buscar actualizaciones** | Busca una versión nueva en GitHub y permite descargarla e instalarla |
| **Quitar todo** | Deja el teléfono como estaba antes de Bubble ([capítulo 11](11-quitar.md)) |
| **Acerca de** | La versión de Bubble |

Bubble también busca actualizaciones al abrirlo, como máximo una vez cada seis horas. Si hay una versión nueva, tocá **Actualizar** para descargarla y confirmá la instalación en Android. La primera vez, Android puede pedirte permitir instalaciones desde Bubble; después de habilitar ese permiso, volvé a Bubble para continuar. Se conservan tus datos y la vinculación. Antes de instalar, Bubble verifica el SHA-256 de la descarga, la versión del paquete y la firma. No hace falta desinstalar la versión anterior.

## El guardián

El guardián es un pequeño proceso que corre con la identidad del motor. Cada minuto:

- apaga cualquier servicio de Google que aparezca dentro de la burbuja o se prenda en el espacio Meta;
- vuelve a aplicar el firewall del teléfono, si lo usás;
- vuelve a mandar la lista del firewall a un perfil que estaba bloqueado cuando cambió.

Tocá el **ícono de espía** arriba en las pantallas de Bubble para ver cómo está corriendo:

- **Vigila todo el tiempo**: la depuración USB está prendida, así que el guardián puede seguir vivo.
- **Revisa cada vez que Bubble se abre, hace algo o el teléfono arranca**: el caso habitual. Con la depuración USB e inalámbrica apagadas el guardián no puede quedar vivo, pero entonces nadie más puede meter servicios de Google en la burbuja: para eso también hace falta la identidad `shell`.
- **No está corriendo**: abrí Bubble para que vuelva a arrancar.

## Qué hace Bubble en segundo plano

Cuando abrís Bubble (una vez por cada vez que Android arranca Bubble), y solo si hay Wi-Fi, Bubble hace un mantenimiento corto en segundo plano, mientras la pantalla ya muestra el estado:

- silencia los eventos del sistema que revelan el uso de la burbuja (este ajuste además sobrevive a los reinicios);
- arranca el guardián;
- vuelve a aplicar el firewall de la burbuja, y reinstala F-Droid o el teclado si los quitaron;
- lee el tamaño de íconos de tu lanzador para el widget.

Durante ese tiempo la depuración inalámbrica se prende unos segundos y Android puede mostrar su notificación *Depuración inalámbrica conectada*; Bubble la oculta mientras trabaja. Sin Wi-Fi, este mantenimiento espera a la próxima vez; abrir apps y cambiar su acceso a internet nunca lo necesitan.

## Depuración inalámbrica y Opciones para desarrolladores

Dejá prendidas las Opciones para desarrolladores. Bubble prende la depuración inalámbrica solo mientras trabaja y la deja como estaba. La depuración USB la decidís vos: prendida, el guardián queda vivo todo el tiempo; apagada, revisa en cada sesión de Bubble.
