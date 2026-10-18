# 9. El firewall del teléfono

**Bubble → Firewall** corta internet (Wi-Fi y datos móviles) a apps de tu **teléfono normal**. Está pensado para apps del sistema que se comunican con su fabricante y que no usás (por ejemplo Device Setup, DeviceLock Controller o SIM Manager), pero sirve para cualquier app.

## Usarlo

1. Prendé **Firewall del teléfono**.
2. Marcá las apps que deben quedarse sin internet. Usá el buscador; *Tus apps* y *Apps del sistema* aparecen por separado.
3. Tocá **Guardar**.

Apagar el interruptor levanta todas las reglas pero recuerda tu lista.

## Cómo funciona y sus límites

El firewall del teléfono no usa una VPN (Play Store puede tirar una VPN; estas reglas no las puede tocar). Combina tres capas, que aplica el motor:

- Una cadena del firewall del kernel que corta la app por completo. Se pierde en cada reinicio; el guardián la vuelve a poner.
- Datos en segundo plano bloqueados en redes medidas, con todas las redes Wi-Fi guardadas marcadas como medidas, para que valga también por Wi-Fi.
- El guardián, que vuelve a aplicar todo cada minuto mientras corre.

Límites:

- **Después de reiniciar**, hasta que Bubble tenga Wi-Fi y restaure las reglas, una app bloqueada puede usar la red. Los datos en segundo plano quedan bloqueados desde el arranque solo si además apagás una vez a mano **Ajustes → Apps → [app] → Datos móviles y Wi-Fi → Datos en segundo plano**.
- **La regla es por app, en todos los espacios**: bloquear una app en el teléfono normal también bloquea la misma app dentro de la burbuja.
- **Bloquear SIM Manager** impide bajar o administrar eSIM mientras esté bloqueado.
- Por ahora, elegir Wi-Fi y datos móviles por separado existe solo dentro de la burbuja y del espacio Meta, no en el teléfono normal.
