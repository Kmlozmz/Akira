<p align="center">
  <img src=".github/assets/akira-header.svg" alt="Akira — Música local y en línea" width="100%" />
</p>

<p align="center">
  Un reproductor para tu biblioteca de siempre y para lo que todavía no has descubierto.
</p>

<p align="center">
  <a href="https://github.com/Kmlozmz/Akira/releases"><img src="https://img.shields.io/badge/versi%C3%B3n-0.2.0-FA243C?style=flat-square" alt="Versión 0.2.0" /></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-242428?style=flat-square" alt="Android 8.0 o posterior" />
  <a href="LICENSE"><img src="https://img.shields.io/badge/licencia-GPL--3.0-242428?style=flat-square" alt="GPL versión 3" /></a>
  <img src="https://img.shields.io/badge/idiomas-ES%20%2F%20EN-242428?style=flat-square" alt="Español e inglés" />
</p>

<p align="center">
  <a href="https://github.com/Kmlozmz/Akira/releases"><strong>Descargar Akira</strong></a> ·
  <a href="#qué-ofrece">Funciones</a> ·
  <a href="#instalación-y-actualizaciones">Actualizaciones</a> ·
  <a href="https://github.com/Kmlozmz/Akira/issues">Informar de un problema</a>
</p>

---

## Qué es Akira

Akira es un reproductor de música de código abierto para Android, escrito en Kotlin. Reúne una biblioteca local completa, acceso a música en línea, letras sincronizadas y ajustes de sonido en una interfaz que puedes adaptar a tu gusto.

Puedes usarlo con los archivos que ya tienes en el teléfono, sin cuenta ni conexión. Cuando quieras descubrir música, activa el modo en línea para explorar recomendaciones, buscar canciones o escuchar una radio. La cuenta de YouTube Music es opcional y añade acceso a tu contenido personal.

El modo en línea sigue en desarrollo; el catálogo, la conexión de cuentas y algunas recomendaciones dependen de servicios externos.

## Qué ofrece

<table>
  <tr>
    <td width="50%" valign="top">
      <h3>Tu biblioteca</h3>
      <p>Canciones, álbumes, artistas, carpetas y listas. Búsqueda local, favoritos y filtros para dejar fuera audios que no quieras escuchar.</p>
    </td>
    <td width="50%" valign="top">
      <h3>Descubrir música</h3>
      <p>Recomendaciones, búsqueda en línea, radios y una ruleta para elegir tu próxima canción. Disponible también como invitado.</p>
    </td>
  </tr>
  <tr>
    <td valign="top">
      <h3>Ajustar el sonido</h3>
      <p>Crossfade con curvas editables, normalización de volumen, ecualizador y perfiles para distintas salidas de audio.</p>
    </td>
    <td valign="top">
      <h3>Seguir las letras</h3>
      <p>Búsqueda en varios proveedores, selección automática de letras sincronizadas y corrección manual del desfase.</p>
    </td>
  </tr>
  <tr>
    <td valign="top">
      <h3>Elegir cómo se ve</h3>
      <p>Temas claro y oscuro, colores de la portada, carátulas a pantalla completa y controles Liquid Glass configurables.</p>
    </td>
    <td valign="top">
      <h3>Controlar la reproducción</h3>
      <p>Cola editable, temporizador, widgets y controles desde la notificación o la pantalla de bloqueo.</p>
    </td>
  </tr>
</table>

## Local y en línea

El selector de modo cambia el contenido de Inicio, Biblioteca y Buscar. Cada modo conserva su contexto de reproducción.

| | Modo local | Modo en línea |
| :--- | :--- | :--- |
| **Música** | Archivos de audio del dispositivo. | Catálogo y contenido disponible a través del proveedor. |
| **Inicio** | Accesos y sugerencias de tu propia biblioteca. | Recomendaciones, radios y colecciones para explorar. |
| **Cuenta** | No es necesaria. | Opcional; vincular YouTube Music permite consultar tu contenido personal. |
| **Conexión** | La biblioteca y la reproducción funcionan sin internet. | Necesaria para cargar contenido y reproducir música. |

Los favoritos guardados como invitado permanecen en el teléfono. Al vincular una cuenta, el modo en línea utiliza el contenido de esa cuenta. La reproducción en línea no convierte las canciones en archivos locales.

### Biblioteca local

- Explora por canciones, álbumes, artistas, carpetas o listas.
- Decide qué carpetas se incluyen y establece una duración mínima para los audios.
- Revisa las canciones excluidas y recupera las que quieras conservar en la biblioteca.
- Crea listas, guarda favoritos y cambia el orden de la cola.

Los filtros cambian lo que muestra Akira; no borran ni mueven tus archivos.

### Sonido y transiciones

El crossfade permite ajustar la duración y la forma en que una canción sale y entra la siguiente. Puedes usar una curva predefinida o modificarla desde el editor.

El ecualizador incluye cinco bandas y preamplificación. Los perfiles por salida permiten guardar ajustes distintos para el altavoz y los auriculares. La normalización ofrece modos por canción o por álbum, además de conservar el volumen original.

La disponibilidad de los efectos y de las transiciones depende del dispositivo, la salida de audio y el formato de las pistas.

### Letras y portadas

Akira puede leer letras locales o consultar varios proveedores. La búsqueda automática compara las opciones disponibles y da prioridad a las letras sincronizadas. Si el tiempo no coincide, puedes corregirlo desde el reproductor.

El reproductor admite portadas enmarcadas o a pantalla completa, colores obtenidos de la carátula y material animado cuando está disponible y es compatible. Los controles y paneles Liquid Glass permiten ajustar su transparencia y difusión.

## Instalación y actualizaciones

### Primera instalación

1. Abre [GitHub Releases](https://github.com/Kmlozmz/Akira/releases) y elige la versión que quieras instalar.
2. Descarga el archivo **`Akira-<versión>.apk`** de esa publicación.
3. Abre el APK y confirma la instalación en Android. Si te lo pide, permite instalar aplicaciones desde el navegador o gestor de archivos que estés usando.
4. Al abrir Akira, sigue la configuración inicial para añadir tu música y elegir tus preferencias.

**Requisitos:** Android 8.0 o posterior. Las publicaciones públicas incluyen únicamente el APK de uso habitual.

### Desde la aplicación

Abre **Ajustes → Aplicación → Actualizaciones**.

| Paso | Qué sucede |
| :--- | :--- |
| **Consultar** | Akira comprueba si hay una versión compatible y muestra sus novedades. Puedes aplazar el aviso durante la sesión o pedir que vuelva al día siguiente. |
| **Descargar** | Tú inicias la descarga. Puedes ver los MB descargados y el progreso, o cancelarla. |
| **Verificar** | Se comprueban el tamaño, la integridad del archivo, el identificador, la versión y la firma de la aplicación. |
| **Instalar** | Si falta el permiso de Android, Akira abre sus ajustes. Al concederlo y volver, continúa con el instalador. |

Android siempre te pide confirmar la instalación. Una actualización compatible conserva la biblioteca y los ajustes; no necesitas desinstalar Akira.

## Permisos y datos

Akira solicita los permisos que necesita para las funciones que utilizas:

| Permiso o servicio | Uso |
| :--- | :--- |
| **Archivos de audio** | Encontrar y reproducir la música guardada en el teléfono. |
| **Notificaciones** | Mostrar controles de reproducción cuando corresponda en Android. |
| **Instalación de aplicaciones** | Abrir una actualización descargada, si eliges instalarla desde Akira. |
| **Servicios en línea** | Obtener música, letras, información de artistas o actualizaciones de sus respectivos proveedores. |

La cuenta de YouTube Music es opcional. Las consultas de actualizaciones se hacen a GitHub y no incluyen la sesión de tu cuenta de música.

## Preguntas frecuentes

<details>
<summary><strong>¿Necesito una cuenta para usar Akira?</strong></summary>

No. La biblioteca local funciona sin cuenta y el modo en línea puede utilizarse como invitado. Vincular YouTube Music permite acceder a contenido personal de esa cuenta.

</details>

<details>
<summary><strong>¿Funciona sin internet?</strong></summary>

Sí, para la biblioteca y las canciones guardadas en el dispositivo. La música en línea, las letras remotas, la información de artistas y las actualizaciones necesitan conexión.

</details>

<details>
<summary><strong>¿Por qué una canción no tiene letras o portada animada?</strong></summary>

Ese material no está disponible para todas las canciones. Depende de los archivos locales, de los proveedores y de que el formato sea compatible.

</details>

<details>
<summary><strong>¿Qué puede fallar en esta versión?</strong></summary>

El modo en línea continúa en desarrollo. Puede haber cambios en la disponibilidad del catálogo, las recomendaciones o el acceso a cuentas. Los efectos de sonido, el material animado y algunas funciones del sistema también pueden comportarse de forma distinta según el teléfono.

</details>

## Informar de un problema

Abre una [incidencia](https://github.com/Kmlozmz/Akira/issues) e incluye:

- Versión de Akira, modelo del teléfono y versión de Android.
- Modo local o en línea; en este último caso, si ocurre con cuenta o como invitado.
- Pasos para reproducirlo, resultado esperado y lo que ocurrió.
- Una captura o grabación, si ayuda a entenderlo.

Antes de adjuntar archivos, revisa que no contengan contraseñas, cookies ni datos personales. Las sugerencias y correcciones también pueden compartirse mediante una incidencia o una propuesta de cambios.

## Licencia y créditos

Akira se distribuye bajo [GNU GPL versión 3](LICENSE). Las licencias de las bibliotecas, tipografías y demás recursos de terceros están recogidas en [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) y en los avisos de la aplicación.

Akira es un proyecto independiente.
