# 13. Preguntas frecuentes

**¿Bubble es seguro? Pide mucho poder.**
Bubble necesita permisos del sistema para armar y gobernar la burbuja, que ninguna app común puede obtener, así que toma prestada la identidad `shell` de Android a través de la depuración inalámbrica. Se la das una vez, con un código, a propósito: ninguna app puede obtener esa identidad sin vos. Bubble es de código abierto para que cualquiera pueda revisar qué hace con ella. [SECURITY.md](../../../SECURITY.md) (en inglés) lista cada permiso y por qué.

**¿Google igual sabe algo?**
Los servicios de Google del teléfono pueden saber que existe un perfil privado y qué apps tiene instaladas. No pueden ver cuándo las usás, tus datos, tus cuentas ni tu tráfico. Ver el [modelo de privacidad](../../privacy-model.md) (en inglés).

**¿Por qué no usar directamente el espacio privado normal de Android?**
Un espacio privado normal viene con Google Play Services y Play Store adentro, que te siguen entre apps. La burbuja los deja afuera desde el primer arranque, filtra internet de cada app, oculta su uso del registro del sistema y mantiene afuera a Google con un guardián.

**¿Necesito root?**
No. Bubble funciona en un teléfono de fábrica con el bootloader bloqueado.

**¿Necesito una computadora?**
No. La vinculación se hace en el propio teléfono.

**¿Cómo actualizo Bubble?**
Bubble consulta GitHub al abrirlo, como máximo una vez cada seis horas, y ofrece **Actualizar** si hay una versión nueva. También podés usar **Ajustes → Buscar actualizaciones**. La primera vez Android puede pedirte permitir instalaciones desde Bubble y después te pide confirmar la actualización. Se conservan tus datos y la vinculación; no desinstales la versión anterior.

**¿Gasta batería?**
Las apps de la burbuja usan lo mismo que usarían en cualquier lado. Bubble solo trabaja un momento cuando lo usás; el guardián revisa una vez por minuto mientras está vivo.

**¿Puedo usar mi propia VPN?**
Sí, en el teléfono normal. Dentro de la burbuja ese lugar lo ocupa el firewall de Bubble.

**¿Por qué tengo que vincular de nuevo después de restablecer el teléfono?**
El restablecimiento borra la autorización de la llave de Bubble.

**¿Por qué el espacio Meta tiene Google instalado? ¿No es lo contrario de Bubble?**
Facebook e Instagram se cierran sin él, por un error de esas apps. El espacio Meta mantiene Google apagado y separado de la burbuja, así que la burbuja sigue sin Google. No estás obligado a crearlo.

**¿Va a funcionar en mi teléfono?**
Está probado en un Pixel 9a con Android 17. Otros teléfonos pueden comportarse distinto: los fabricantes cambian partes de Android en las que Bubble se apoya, y por ahora la burbuja solo se crea en Android 17. Los reportes de otros teléfonos son bienvenidos.

**¿Es gratis?**
Sí, y de código abierto (MIT). Podés apoyarlo por GitHub Sponsors.
