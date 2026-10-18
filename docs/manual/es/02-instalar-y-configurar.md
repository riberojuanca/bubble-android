# 2. Instalar y configurar

## Instalar Bubble

1. Bajá el APK más reciente de la [página de versiones](https://github.com/riberojuanca/bubble-android/releases).
2. Abrilo y, cuando Android lo pida, permitile al navegador o al gestor de archivos instalar apps.
3. Abrí **Bubble**.

> Desde 2027 Android va a exigir que las apps instaladas fuera de las tiendas vengan de un desarrollador verificado. Ver [verificación de desarrolladores](../../developer-verification.md) (en inglés) para saber qué significa para Bubble.

## Paso 1 de 2: vincular Bubble

Bubble necesita la identidad `shell` de Android para armar la burbuja. La obtiene por la **depuración inalámbrica**, que autorizás una vez con un código de vinculación. Después de este paso, Bubble prende la depuración inalámbrica solo mientras trabaja y la vuelve a apagar.

1. **Activá las Opciones para desarrolladores**: Ajustes → Acerca del teléfono → tocá **Número de compilación** siete veces. Poné tu PIN si lo pide.
2. Asegurate de estar conectado a **Wi-Fi**. En Bubble, tocá **Vincular**. Se inicia la notificación de vinculación y se abren las Opciones de desarrollador.
3. En Opciones para desarrolladores, prendé **Depuración inalámbrica**. Si Android pregunta *«¿Permitir la depuración inalámbrica en esta red?»*, marcá **Permitir siempre en esta red** y aceptá.
4. Tocá **Depuración inalámbrica** (el nombre, no el interruptor) y abrí **Vincular dispositivo con código de vinculación**. Aparece un código de 6 dígitos. **Dejá ese diálogo abierto.**
5. Sin volver a Bubble ni cerrar el diálogo, abrí la notificación de Bubble y tocá **Vincular**.
6. Escribí ahí el **código de 6 dígitos** y enviá la respuesta.
7. Cuando la notificación muestre *Vinculado*, volvé a Bubble.

Si rechaza el código (*«El código no coincide o se cerró el diálogo de vinculación»*), abrí de nuevo el diálogo de vinculación (el código cambia cada vez) y repetí los pasos 4 a 6.

## Paso 2 de 2: crear la burbuja

Tocá **Crear la burbuja**. Tarda alrededor de un minuto. Bubble:

1. Crea un espacio privado solo con las piezas del sistema que Android necesita para hacerlo andar, dejando afuera todos los servicios de Google.
2. Vigila el sistema mientras arranca; si algo se cae, borra el perfil nuevo enseguida.
3. Instala adentro **F-Droid**, para que puedas instalar apps, y un **teclado sin internet** (el Gboard de tu teléfono, o HeliBoard si no tenés Gboard).
4. Configura el firewall de la burbuja (al principio solo F-Droid tiene internet), el bloqueo inteligente y el silenciado de eventos del sistema.

Si ya tenés un espacio privado, Bubble no lo toca y pregunta:

- **Tiene servicios de Google**: no puede ser la burbuja. **Eliminar y crear** lo borra, con sus apps y datos, y crea la burbuja.
- **No tiene servicios de Google**: **Usar este** lo conserva como está, con sus apps y datos, o **Eliminar y crear** lo reemplaza.

Al terminar, Bubble muestra *La burbuja está lista*. Tocá **Empezar**.

## Entrar a la burbuja por primera vez

Abrí el cajón de apps y bajá hasta el final: hay una sección **Privado**. Tocala y desbloqueala con tu bloqueo de pantalla. Vas a ver F-Droid. Eso es la burbuja.

## Después de reiniciar

No hace falta volver a vincular. Después de un reinicio, Bubble restaura su protección sola la próxima vez que tenga Wi-Fi. La burbuja arranca **bloqueada**; abrila desde la sección Privado como siempre.
