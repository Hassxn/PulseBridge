# Scoreboards de la network y timers — actualización coordinada

## Versiones del conjunto

| Proyecto | Versión | Dónde se instala |
|---|---|---|
| PulseProxy | 1.3.0 | Velocity |
| PulseProxyBridge (repositorio PulseBridge) | 1.1.0 | Cada backend |
| PulseHCF | 1.0.2-SNAPSHOT | Backend HCF / KitMap |
| PulseHub | 1.4.6 | Lobby / hub |
| PulseSurvival | 1.12.0 | Backend Survival |
| PulseSkyblock | 1.6.0 | Backend Skyblock |

PulseGenerator genera mundos y no administra una scoreboard. Se mantiene sin cambios para evitar crear una segunda barra en los servidores que lo usan.

## Qué cambia

- Estilo predeterminado dorado/blanco similar a PulseHCF, con título animado y datos propios de cada modalidad.
- Máximo de 15 líneas visibles y puntajes estables. Hub mantiene su scoreboard al cambiar entre lobby, parkour y PvP; los bloques dinámicos no recrean la barra.
- Bloque opcional {network-events}: se expande solo cuando existen timers de red visibles. Si no existe en una configuración personalizada, se inserta en memoria de forma predeterminada, sin reescribir el archivo del usuario.
- Survival y Skyblock aceptan títulos antiguos de texto fijo y títulos nuevos con frames; tienen ocultación de placeholders nativos vacíos y staff-lines opcionales mientras el jugador está en vanish.
- PulseBridge publica los timers SOTW/EOTW de HCF y recibe snapshots de los eventos del proxy. No crea una scoreboard propia. No hay un nuevo plugin que instalar.
- La protección SOTW local de HCF no se duplica en su propia scoreboard; los timers nativos siguen siendo responsabilidad de PulseHCF. Los eventos de otros servidores pueden mostrarse según el alcance elegido.
- Snapshots cada 2 segundos, TTL predeterminado de 15 segundos y contador calculado localmente. No queda un timer congelado indefinidamente si el proxy o el transporte deja de responder.

## Instalación, en este orden

1. Respaldá JAR, configuraciones y datos. Probá en una copia antes de actualizar la network pública.
2. Apagá proxy y backends. Reemplazá el JAR de PulseProxy y el core correspondiente a cada modalidad. No dejes dos versiones del mismo plugin en plugins.
3. Reemplazá tu PulseProxyBridge por 1.1.0 en todos los backends donde quieras mostrar eventos.
4. Quitá PulseHCFEvents.jar si lo habías instalado. Su función ahora está dentro de PulseBridge. Si queda instalado por error, el bridge evita publicar por duplicado, pero conviene retirarlo.
5. Conservá tus configuraciones. Al arrancar, Bridge crea su config.yml si no existe. Los nuevos estilos son los valores predeterminados para instalaciones nuevas; no se sobrescriben tus diseños personalizados.
6. En el proxy habilitá modules.operations y modules.lobby. Para la publicación de timers nativos, editá la sección bridge existente de modules/operations/config.yml:

~~~yaml
bridge:
  allowed-servers: [hcf]
~~~

El nombre debe coincidir con velocity.toml. No autorices servidores innecesarios como publicadores de eventos. Recibir snapshots no necesita estar en esta lista.

7. Arrancá los backends y el proxy. Entrá con una cuenta: el handshake y los mensajes necesitan al menos un jugador conectado en cada backend. Ejecutá /pulseproxy doctor y verificá que el bridge sea detectado.

## Configuración de la scoreboard

| Core | Archivo | Lista de líneas | Formato de evento |
|---|---|---|---|
| HCF | ui/scoreboard.yml | scoreboard.lines / scoreboard.staff-lines | scoreboard.formats.network-event |
| Hub | display/scoreboard.yml | profiles.default.lines / parkour.lines / pvp.lines | network-event-format |
| Survival | display.yml | scoreboard.es.lines / scoreboard.en.lines | scoreboard.network-event-format |
| Skyblock | display.yml | scoreboard.es.lines / scoreboard.en.lines | scoreboard.network-event-format |

Agregá una sola entrada en la ubicación deseada:

~~~yaml
- "{network-events}"
~~~

Es un BLOQUE de líneas, no un placeholder de una sola línea. Si no hay eventos activos, no consume ninguna fila. Se respeta la preferencia del jugador y los mundos donde la scoreboard está desactivada. El límite total continúa siendo 15: colocá el bloque cerca del comienzo para priorizar los timers.

Para controlar la inserción automática, usá network-events.enabled y network-events.auto-insert en la raíz de la configuración de Hub, o bajo scoreboard en HCF/Survival/Skyblock. enabled: false oculta el bloque. auto-insert: false deja la ubicación completamente en tus manos.

En Survival y Skyblock cada idioma admite:

~~~yaml
title:
  interval-ticks: 10
  frames:
    - "&6&lPulse&f&lSurvival"
    - "&e&lPulse&f&lSurvival"
~~~

El título antiguo title: "Texto" sigue funcionando. La velocidad efectiva depende también de update-ticks. HCF/Hub conservan sus formatos de animación existentes. En clientes/servidores antiguos siguen vigentes los límites del título y de prefix/suffix; no se promete texto ilimitado ni ocultar números donde la API no lo soporta.

## Configuración del bridge

~~~yaml
network-events:
  enabled: true
  scope: ALL
  ttl-seconds: 15
  max-lines: 3
  show-waiting: true
  hide-local-hcf-timers: true
  format: "&6{event} &7({server}) &f{time}"
  waiting-text: "Reiniciando"
  labels:
    SOTW: SOTW
    EOTW: EOTW
    RESTART: Reinicio
hcf-timers:
  enabled: true
  interval-ticks: 100
~~~

ALL muestra eventos de la network; LOCAL solo los del backend actual. El formato de la scoreboard del core tiene prioridad si no está vacío; si querés usar el formato global del bridge, dejá su formato específico como una cadena vacía. Variables de formato: {event}, {server}, {time}, {state}. Las etiquetas del bridge se configuran a nivel servidor.

/pulsebridge reload requiere pulsebridge.admin. El cambio de intervalo del publicador HCF requiere reiniciar el backend; los otros valores se leen desde la configuración recargada. El canal moderno pulseproxy:bridge se conserva y se agrega pulse:bridge como alternativa corta para backends legacy.

## Prueba rápida con un evento informativo

1. Entrá al lobby y asegurate de que su scoreboard esté visible.
2. Desde consola del proxy: networkevent eotw survival 2m.
3. Esperá unos segundos. Debe aparecer el contador en las scoreboards de los backends con jugadores, si scope es ALL.
4. Cambiá entre hub, Survival y Skyblock. El contador debe mantener la misma fecha de vencimiento.
5. Desde consola: networkevent cancel survival. El bloque debe desaparecer sin duplicar o desordenar líneas.
6. Repetí varias veces. En Hub, cambiá además a parkour/PvP mientras exista el evento y comprobá el regreso al perfil normal.

Este evento es informativo y no inicia el EOTW real del mapa de HCF. Es preferible para probar el dibujo sin afectar reglas de juego.

## Reinicio y retorno

1. Con jugadores dentro de Survival: networkevent restart survival 30s.
2. Debe mostrar el contador; al terminar, los jugadores son trasladados a un lobby configurado y disponible. No se espera que se desconecten de la network.
3. El backend NO se apaga automáticamente. Reinicialo desde el hosting y dejalo offline el tiempo suficiente para que el proxy lo detecte.
4. Al volver, el evento pasa a READY y el timer desaparece. Si no detectó la caída, usá networkevent ready survival.
5. Si el lobby no está disponible o se desconecta al cliente, guardá los logs completos de proxy y backend para investigar el traslado/fallback por separado.

## Timers nativos HCF

En un mapa de pruebas y con alguien conectado a HCF:

~~~text
sotw start 2m
~~~

Debe aparecer en la scoreboard nativa HCF y publicarse al proxy como PROTECTION. En los demás backends con alcance ALL se muestra su cuenta regresiva. En HCF no debe verse duplicado. La protección no bloquea nuevas entradas.

Probá sotw end antes del vencimiento: se elimina el contador publicado. Probá EOTW real solo en un mapa descartable, porque cambia reglas del core. El puente usa la API getTimerEngine/getGlobal/getExpiry y esas firmas se preservan para el perfil opcional de ofuscación de HCF.

## Prueba de caída de transporte

Con un timer largo activo, deshabilitá temporalmente operations desde consola del proxy. El proxy intenta enviar una lista vacía. Si se corta el transporte sin ese mensaje, las scoreboards deben ocultar los timers después del TTL (15 segundos por defecto). Los datos normales de la modalidad deben seguir visibles. Reactivá operations y verificá la recuperación.

## Validación y límites

Compilaciones locales de los seis proyectos completas. Pruebas: HCF 132, Proxy 32, Bridge 6, Hub 5, Survival 5, Skyblock 5: total 185, sin fallos ni errores. Se verifican bloques dinámicos, límite de líneas, títulos, compatibilidad de diseños, vencimiento de snapshots y rechazo de mensajes de clientes en ambos canales del proxy.

No se realizó una sesión con clientes Minecraft reales, Redis de producción, licencias de tu hosting ni una network real de varios proxies. Las capturas de scoreboard y las transiciones de traslado necesitan validarse en staging. No se ejecutó el perfil opcional de ofuscación. El bridge no ofrece una expansión PlaceholderAPI en esta versión: los cuatro cores leen directamente su API opcional para el bloque {network-events}.
