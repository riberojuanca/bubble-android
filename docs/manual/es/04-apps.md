# 4. Apps en la burbuja

Abrí **Bubble → Apps**. La lista muestra las apps de la burbuja con tres íconos cada una: **Wi-Fi**, **datos móviles** ([capítulo 5](05-internet.md)) y **quitar** (el tacho). Usá el buscador para encontrar una; deslizá hacia abajo para volver a leer la lista (por ejemplo, después de instalar algo con F-Droid).

## Agregar apps

Hay tres formas, y se pueden combinar.

### Copiar una app que ya tenés en el teléfono

**Agregar apps del teléfono** lista las apps que instalaste en tu teléfono normal. Marcá las que quieras y tocá **Agregar**.

- La copia de adentro **empieza sin datos**: volvés a iniciar sesión, y no se comparte nada con la copia de afuera.
- Es **la misma app instalada**: mientras siga instalada afuera, Play Store la mantiene actualizada, y la copia de adentro recibe cada actualización.
- Después podés **desinstalarla afuera**; la copia de adentro queda y sigue funcionando, pero deja de actualizarse. Para actualizarla, instalala de nuevo afuera (desde Play o un APK) y volvé a desinstalarla afuera.
- Bajar la app de Play le dice a Google que tenés esa app; Google no ve que está en la burbuja ni qué hacés con ella.

### Instalar un archivo APK

**Instalar un archivo APK** ofrece dos orígenes:

- **De las Descargas de la burbuja**: un APK que bajaste con un navegador dentro de la burbuja. Si la burbuja está bloqueada, Bubble te pide desbloquearla primero.
- **De los archivos del teléfono**: un APK de tu teléfono normal. Se instala solo dentro de la burbuja.

Los archivos y las fotos que descargan las apps de la burbuja pertenecen a su almacenamiento separado. No aparecen automáticamente en las Descargas ni en la galería del teléfono normal. Bubble conserva el selector de documentos de Android dentro del perfil para que las apps puedan elegir archivos allí; el selector de APK de arriba solo muestra APK, no fotos ni otras descargas. Para usar una foto de la burbuja en el teléfono normal, elegila desde **Fotos de la burbuja** en el selector de archivos del teléfono ([capítulo 3](03-inicio-y-burbuja.md#usar-fotos-de-la-burbuja-en-el-teléfono)).

### Usar F-Droid dentro de la burbuja

F-Droid es la tienda de apps de código abierto, ya instalada en la burbuja. Abrila desde la sección Privado e instalá apps como siempre. Aparecen en Bubble → Apps después de deslizar hacia abajo para actualizar. F-Droid es la única app con internet de entrada, para que pueda descargar.

## Las apps nuevas no tienen internet

Toda app que agregues empieza **sin internet**. Activalo con los íconos de Wi-Fi y datos móviles en Bubble → Apps ([capítulo 5](05-internet.md)). Las apps que agregues al espacio Meta son distintas: tienen internet desde el principio ([capítulo 8](08-espacio-meta.md)).

## Quitar apps

Tocá el **tacho** al lado de la app y confirmá. La app y sus datos se borran **solo de la burbuja**; la copia de tu teléfono normal, si existe, no se toca.

## Teclados

Los teclados son por espacio, así que la burbuja tiene el suyo. Bubble activa adentro el **Gboard** de tu teléfono, **sin internet** (no puede mandar lo que escribís a ningún lado). Si tu teléfono no tiene Gboard, Bubble instala **HeliBoard** desde F-Droid. Si activás otro teclado dentro de la burbuja, Bubble lo respeta.

## Apps de Google

Las *apps* de Google (YouTube, Maps…) se permiten en la burbuja como cualquier otra; lo que la burbuja deja afuera son los *servicios* de Google (Play Services, Play Store y su plomería). Bubble se niega a agregarlos, y el guardián los apaga si llegan a aparecer.

## Apps que necesitan Google

- **Sin notificaciones push por Google**, la mayoría de las apps funcionan normalmente.
- **Las apps que verifican Play Integrity** suelen abrir pero no dejan iniciar sesión. No pueden funcionar en la burbuja.
- **Facebook e Instagram** se cierran al arrancar en la burbuja. Usá el [espacio Meta](08-espacio-meta.md).

La [lista de compatibilidad de apps](../../app-compatibility.md) (en inglés) registra lo que se probó.
