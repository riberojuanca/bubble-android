# 5. Acceso a internet

## El firewall de la burbuja

Dentro de la burbuja **ninguna app tiene internet salvo que lo permitas**. Bubble lo hace con una pequeña VPN que corre dentro de la burbuja: las apps que no permitiste van a un túnel donde su tráfico se descarta; las que permitiste quedan fuera de la VPN y usan la red normalmente. No se inspecciona ni se reenvía nada, y nada de tu tráfico sale del teléfono a través de Bubble.

Esta VPN es solo de la burbuja: tu teléfono normal y sus apps no se ven afectados, y afuera podés seguir usando tu propia VPN.

## Wi-Fi y datos móviles, por app

En **Bubble → Apps**, cada app tiene dos íconos:

| Ícono | Lila | Gris y tachado |
|---|---|---|
| **Wi-Fi** | La app tiene internet cuando el teléfono está en Wi-Fi | Sin internet por Wi-Fi |
| **Datos móviles** | La app tiene internet cuando el teléfono usa datos móviles | Sin internet por datos móviles |

Tocá un ícono para cambiarlo. El cambio se guarda al instante y se aplica en segundo plano; podés seguir tocando otras apps.

Ejemplos:

- **NewPipe o Spotify solo por Wi-Fi**, para que no gasten tu plan de datos.
- **Signal por los dos**, para que te lleguen los mensajes en la calle.
- **Una app de banco solo por datos móviles**, nunca en un Wi-Fi público.

El firewall sigue a la red que está usando el teléfono: cuando pasás de Wi-Fi a datos móviles, cambia a la lista de datos móviles en un momento. Ethernet cuenta como Wi-Fi.

## Para tener en cuenta

- **Cambiar permisos funciona sin Wi-Fi.** Bubble habla directamente con su copia dentro de la burbuja.
- **Si la burbuja está bloqueada** cuando cambiás un ícono, la elección se guarda y se aplica al desbloquearla.
- **Cuando la burbuja se abre**, su firewall arranca bloqueando todo y después le pide tu lista a Bubble. Si no puede obtenerla, todas las apps de la burbuja quedan sin internet y Bubble muestra una notificación: *Apps de este espacio sin internet*. Abrí Bubble para que la vuelva a mandar.
- **Justo después de desbloquear** hay un instante, antes de que arranque el firewall, en el que una app podría llegar a la red. Los teclados y la mayoría de las apps no corren en ese instante.
- **El firewall elige por red, no por paquete**: aplica la lista de la red que usa el teléfono. Una app permitida solo por Wi-Fi no recibe datos móviles, pero si el teléfono tuviera las dos redes a la vez y la app eligiera la otra por su cuenta, el firewall no lo impediría. En el uso normal, el teléfono usa una red a la vez.

## El espacio Meta

El espacio Meta tiene su propio firewall, con los mismos dos íconos por app, en **Bubble → Espacio Meta**. Las apps que agregues ahí tienen internet por las dos redes desde el principio; su teclado no tiene.

## Tu teléfono normal

Para cortarle internet a apps de tu teléfono normal, usá el [firewall del teléfono](09-firewall-del-telefono.md).
