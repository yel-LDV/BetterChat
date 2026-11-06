# BetterChats

Proveedor de API y motor de chat para Spigot 1.8.8 (AzuriteSpigot / Java 21) y
superiores. Implementa el *Plan de Proyecto v2.0*: equipos de chat en JSON,
mensajes privados, formato seguro con MiniMessage, anti-spam, persistencia,
eventos públicos y un **modo spy unificado** que también ve los `/msg` de
VelezCore y el chat de clan y de aliados de vClans.

## Decisiones (fase 0)

| Decisión pendiente del plan | Elegido | Motivo |
|---|---|---|
| Rango de versiones | `betterchats-legacy` para 1.8.8+ | El servidor es AzuriteSpigot 1.8.8. Adventure va incluido y reubicado; en Paper moderno debería funcionar vía `AsyncPlayerChatEvent`, pero no está probado. |
| Estrategia de entrega | `HYBRID` por defecto (configurable) | 1.8 no tiene ChatRenderer ni chat firmado. El chat general sigue el evento (logs/DiscordSRV lo ven); los chats privados/staff y los mensajes con hover (`[item]`) se cancelan y se envían como componentes. |
| Persistencia | JSON por jugador (`playerdata/<uuid>.json`) | Simple y sin dependencias; `StorageProvider` permite añadir SQLite/MySQL. |
| Lenguaje | Java 21 (API en Java 8) | La API compila a bytecode 8 para que vClans (target 1.8) pueda usarla. |
| Folia | Más adelante | El core no usa `Bukkit.getScheduler()`: añadir `betterchats-folia` no toca el core. |
| Vanish | Metadata `"vanished"` + `canSee` | Convención de SuperVanish, PremiumVanish, Essentials… VelezCore ahora también la pone. |

**No incluido en esta versión:** módulos `betterchats-modern` (Paper nativo
con `AsyncChatEvent`) y `betterchats-folia`, backend SQL, matriz de CI y
pruebas de integración con MockBukkit (MockBukkit no soporta 1.8.8). Las
pruebas unitarias del core sí están: inyección MiniMessage, filtros y
validación de configuración (`gradle :betterchats-core:test`).

## Módulos

```
betterchats-api/      Contratos públicos (Java 8, sin dependencias)
betterchats-core/     Lógica sin Bukkit: equipos, JSON, formato, PMs, spy, anti-spam
betterchats-legacy/   Adaptador Spigot 1.8.8+  -> build/libs/BetterChats-1.0.0.jar
betterchats-example/  Plugin de ejemplo que consume la API
libs/                 PlaceholderAPI y PacketEvents del servidor (solo para compilar)
```

## Compilar

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
.\gradlew.bat build
```

Plugin: `betterchats-legacy/build/libs/BetterChats-1.0.0.jar`.
API para otros plugins: `betterchats-api/build/libs/BetterChats-API-1.0.0.jar`
(o `gradlew :betterchats-api:publishToMavenLocal` →
`dev.betterchats:betterchats-api:1.0.0`).

## Archivos (`plugins/BetterChats/`)

| Archivo | Para qué |
|---|---|
| `config.json` | Estrategia, prioridad del listener, seguridad, anti-spam, snapshots, alias de comandos |
| `chats/*.json` | Un archivo por chat. Exactamente uno con `"isDefault": true` |
| `private_messages.json` | `/msg`, `/reply`, formatos, sonido, vanish, rate limit y spy de PMs |
| `spy.json` | Comando `/spy`, y formato/permiso/activado de cada canal (propios y de otros plugins) |
| `chat_colors.json` | Menú `/chatcolor`: colores, efectos, formatos, iconos y permisos |
| `lang/es.json`, `lang/en.json` | Mensajes (MiniMessage) |
| `playerdata/`, `data/` | Generado: chat activo, ignorados, spy, membresías, caché de nombres |

`/betterchats reload` recarga todo de forma transaccional: si algún archivo
tiene un error se conserva la configuración anterior y se muestra el problema
con su ruta, p. ej. `chats/staff.json -> sound.pitch: debe estar entre 0.5 y 2.0 (valor: 3.0)`.

Placeholders de chat: `{playerName}`, `{displayName}`, `{team}`,
`{player_location}`, `{world}`, `{x}`, `{y}`, `{z}`, `{message}` y cualquier
`%placeholder%` de PlaceholderAPI.

## Colores legacy (`&7`, `&4`…)

Con `chat.legacyColorsInFormats: true` (por defecto) se aceptan códigos legacy
en **todo** lo configurable: formatos de `chats/*.json`, `private_messages.json`,
`spy.json`, `displayName` de los chats, `permissionLostMessage` y los mensajes
de `lang/`. Se pueden mezclar con MiniMessage en el mismo texto.

| Código | Resultado |
|---|---|
| `&0`–`&9`, `&a`–`&f` (o `§`) | Colores |
| `&l` `&o` `&n` `&m` `&k` | Negrita, cursiva, subrayado, tachado, ofuscado |
| `&r` | Reset |
| `&#RRGGBB` | Color hex (en clientes 1.8 se aproxima al color más cercano) |

Se respeta la regla legacy: un color nuevo apaga las decoraciones
(`&l&cHola` = rojo sin negrita; usa `&c&lHola` para rojo en negrita).

```json
"format": "&8[&7{world}&8] &7%vault_prefix%{playerName} &8» &f{message}"
```

En los **mensajes de los jugadores** (escribir `&4Hola` en el chat) los `&`
solo funcionan con el permiso correspondiente; sin permiso se ven tal cual:

| Permiso | Por defecto | Permite escribir |
|---|---|---|
| `betterchats.tags.color` | OP | `&0-&f`, `&#RRGGBB`, `&r` |
| `betterchats.tags.format` | OP | `&l &o &n &m` |
| `betterchats.tags.obfuscated` | nadie | `&k` |

Para que **todos** los jugadores puedan escribir con colores sin dar permisos,
pon `"defaultAllowedPlayerTags": ["color"]` en `security` de `config.json`
(o `["color", "format"]` para incluir negrita, cursiva…). El carácter `§`
escrito por un jugador se elimina siempre. Si tienes EzColors u otro plugin que
toque el mensaje del chat, quítalo: puede interferir con estos códigos.

## Colores de chat: `/chatcolor` (sustituye a EzColors)

Menú donde cada jugador elige el color o efecto con el que escribe y los
formatos que quiere (negrita, cursiva, subrayado, tachado, ofuscado). La
elección se guarda por jugador y se aplica a todo lo que escriba en el chat (y
en `/msg`, si `applyToPrivateMessages` es `true`). Ya no hace falta
`%ezcolors_color%` en el formato.

- **Menú hecho con PacketEvents.** El servidor no abre ningún inventario real
  (no se pueden sacar items y no choca con otros plugins de menús). Necesita el
  plugin **packetevents** instalado; sin él, `/chatcolor` funciona en modo
  texto.
- **Incluye:** 16 colores, 3 efectos (arcoíris, fuego, océano), 5 formatos y un
  botón para quitarlo todo. Se elige un solo color o efecto a la vez; los
  formatos se combinan con él.
- **Todo se configura** en `chat_colors.json`: título, filas, slot, material
  (nombres de 1.8: `WOOL` + `data`), nombre, lore, permiso y textos de estado.
  Un color es `"color": "red"` (`&c` o `#RRGGBB` también valen); un efecto es
  `"miniMessage": "<gradient:#ff5555:#ffff55>"`; un formato es
  `"decoration": "bold"`.
- Si el jugador escribe sus propios `&` (con permiso), esos colores mandan
  sobre el elegido. Los efectos letra a letra no se aplican a mensajes con
  `[item]`.

| Comando | Qué hace |
|---|---|
| `/chatcolor` (`/colores`) | Abre el menú |
| `/chatcolor <id>` | Activa o quita un color/formato (`/chatcolor red`, `/chatcolor bold`) |
| `/chatcolor list` | Lista con el estado de cada opción |
| `/chatcolor reset` | Quita color y formatos |

| Permiso | Para |
|---|---|
| `betterchats.chatcolor` | Abrir el menú (todos por defecto) |
| `betterchats.chatcolor.<id>` | Cada color o efecto (`betterchats.chatcolor.red`, `.rainbow`…) |
| `betterchats.chatcolor.format.<id>` | Cada formato (`betterchats.chatcolor.format.bold`…) |

Las opciones sin permiso aparecen como **Bloqueado**. Los OP tienen todas;
con LuckPerms, `betterchats.chatcolor.*` da todas a un rango.

## PlaceholderAPI

Opcional (`softdepend`). Si está instalado, cualquier `%placeholder%` funciona
en los formatos de chat, de mensajes privados (el entrante con los datos del
emisor, el saliente con los del receptor) y de spy (datos del emisor).

- Los valores se insertan **en línea**, como en los plugins legacy: si
  `%vault_prefix%` es `&c[Admin] &f`, el nombre que va detrás sale en blanco.
- Los colores `&`/`§` del valor se aplican; las etiquetas MiniMessage del valor
  se escapan (un nick con `<click:...>` sale como texto).
- Nunca se resuelven placeholders dentro del mensaje del jugador.
- Los placeholders que solo funcionan en el hilo principal se listan en
  `snapshot.placeholders` de `config.json`: se precalculan cada
  `refreshTicks` y el chat usa ese valor.

### Placeholders de BetterChats

| Placeholder | Valor |
|---|---|
| `%betterchats_chat%` | Nombre visible del chat activo (con colores) |
| `%betterchats_chat_id%` | Id del chat activo (`default`, `staff`…) |
| `%betterchats_spy%` | `true` si tiene el spy activo en algún canal |
| `%betterchats_spy_channels%` | Canales espiados, separados por coma |
| `%betterchats_ignored_count%` | Jugadores que ignora |
| `%betterchats_last_partner%` | Último interlocutor de `/msg` |
| `%betterchats_access_<chat>%` | `true` si puede usar ese chat |
| `%betterchats_member_<chat>%` | `true` si es miembro de ese chat |

### Compilar contra tu PlaceholderAPI y PacketEvents

Deja los jars del servidor en `libs/` (ahora: `PlaceholderAPI-2.11.7.jar` y
`packetevents-spigot-2.12.1.jar`). Gradle compila contra ellos; si falta
alguno, lo descarga (extendedclip / CodeMC). Ninguno se incluye dentro de
BetterChats: en el servidor se usan los plugins instalados.

## Seguridad del texto del jugador

El mensaje del jugador **nunca** se deserializa junto con la plantilla. Sin
etiquetas permitidas es texto literal; con etiquetas se usa un MiniMessage
restringido. `click`, `hover`, `insertion`, `font`, `keybind`… solo con su
permiso explícito (`betterchats.tags.click`…) o `betterchats.tags.all`, aunque
el chat las liste en `allowedPlayerTags`.

| Permiso | Concede |
|---|---|
| `betterchats.tags.color` | Colores, degradados, rainbow (y `&a`, `&#RRGGBB`, `&r`) |
| `betterchats.tags.format` | Negrita, cursiva, subrayado, tachado (y `&l &o &n &m`) |
| `betterchats.tags.<etiqueta>` | Una etiqueta concreta |
| `betterchats.tags.all` | Todas |

### Protecciones contra exploits de chat

| Ataque | Protección |
|---|---|
| `<click:run_command:/op ...>` u hover falsos en el chat | El mensaje del jugador nunca se lee como MiniMessage sin permiso; click/hover/insertion/font solo con permiso explícito |
| `%placeholders%` en el mensaje (filtrar datos, expansiones peligrosas) | PlaceholderAPI solo se aplica a las plantillas, nunca al texto del jugador; las etiquetas de los valores se escapan |
| "Chat kick": `[item]` con un libro o NBT enorme desconecta a todos | NBT de hover limitado y cada línea se mide antes de enviarse: si pasa de 30 000 bytes se quitan hover/clic o se manda como texto |
| Salto de línea / caracteres invisibles para fingir mensajes del staff o invertir texto | Se convierten en espacio o se eliminan (U+200B…U+200F, U+202A…U+202E, U+2066…U+2069, U+FEFF…) |
| Texto "zalgo" (marcas apiladas) | Máximo 2 marcas combinables seguidas |
| Cientos de etiquetas de color anidadas | Más de 32 etiquetas → el mensaje va como texto |
| `${jndi:...}` (Log4Shell) | Bloqueado en chat y PMs (`security.blockLookupPatterns`). Tu RecoilSpigot usa Log4j 2.25.3, que ya no es vulnerable |
| Spam / flood | Rate limit por chat y PMs, duplicados y longitud máxima |
| Flood de clics en el menú `/chatcolor` (lag) | Una tarea por jugador y un clic cada 150 ms; el menú es solo de paquetes (no hay items reales que duplicar) |
| Delatar staff en vanish | `/msg`, autocompletado e `/ignore` lo tratan como desconectado |
| `%` en el formato (romper `String.format`) | Se escapa al fijar el formato del evento |

## Comandos

| Comando | Permiso |
|---|---|
| `/chat` · `/chat <id>` · `/chat <id> <mensaje>` | acceso al chat |
| `/msg <jugador> <mensaje>` · `/r <mensaje>` | `betterchats.msg` |
| `/ignore [jugador]` · `/unignore <jugador>` | `betterchats.ignore` |
| `/spy` · `/spy list` · `/spy <canal>` · `/spy on/off` | `betterchats.spy.*` |
| `/chatcolor` · `/chatcolor <id>` · `/chatcolor list/reset` | `betterchats.chatcolor` |
| `/betterchats reload/version/teams/info/join/leave` | `betterchats.admin` |

Los alias se registran en tiempo de ejecución desde los JSON. Si otro plugin
ya tiene un alias (por ejemplo `/msg` de VelezCore) **se respeta** y se avisa
en consola; BetterChats queda en `/betterchats:msg`. Para quitárselo, pon
`takeOverExistingCommands: true`.

## Modo spy

Un solo `/spy` para todo. Canales:

| Canal | Origen | Permiso |
|---|---|---|
| `pm` | `/msg` de BetterChats | `betterchats.spy` |
| `team` | chats privados/staff en los que no estás | `betterchats.spy.team` |
| `velezcore.msg` | `/msg` y `/r` de VelezCore | `betterchats.spy.velezcore` |
| `vclans.clan` | chat de clan de vClans | `betterchats.spy.vclans` |
| `vclans.ally` | chat de aliados de vClans | `betterchats.spy.vclans` |

`betterchats.spy.all` da todos. `/spy` sin argumentos activa todos los
canales que puedas ver (también los que se registren después). El estado se
guarda entre sesiones (`restoreOnJoin`). Quien ya recibió el mensaje original
no lo recibe duplicado.

## Integraciones

**VelezCore** (sección `betterchats:` de `modules/essentials/config.yml`):
`/msg` y `/r` van al canal `velezcore.msg`, respetan los `/ignore` de
BetterChats, pueden delegarse enteros a BetterChats
(`delegar-mensajes: true`), el token `[item]` se registra en BetterChats y el
vanish pone la metadata `vanished`. Si usabas EzChat, quítalo: BetterChats lo
reemplaza.

**vClans** (sección `betterchats:` de `config.yml`): el chat de clan y de
aliados va a `vclans.clan` / `vclans.ally` (con `{clan}` en el formato) y no
llega a quien ignora al emisor. vClans sigue desviando el chat en `LOWEST`;
BetterChats ignora los mensajes ya cancelados.

## API

```java
BetterChatsAPI api = Bukkit.getServicesManager().getRegistration(BetterChatsAPI.class).getProvider();

api.getSpy().registerChannel(new SpyChannel("miplugin.canal", "Mi canal", null,
        "<dark_gray>[Spy] <gray>{sender} → {target}: <white>{message}"));
api.getSpy().dispatch(SpyMessage.builder("miplugin.canal")
        .sender(p.getUniqueId(), p.getName()).target("destino").message(texto)
        .alreadyReceived(receptores).build());

api.registerFilter(ctx -> FilterResult.allow(), 500);
api.registerToken(miToken);            // p. ej. [item]
api.getEventBus().subscribe(this, ChatMessageEvent.class, e -> { /* ... */ });
```

Eventos: `ChatTeamCreateEvent`, `ChatTeamDeleteEvent`, `ChatTeamJoinEvent`,
`ChatTeamLeaveEvent`, `ChatActiveTeamChangeEvent`, `ChatMessageEvent`,
`PrivateMessageEvent`, `SpyMessageEvent`, `BetterChatsReloadEvent`. Se publican
en el bus de la API (no en el de Bukkit); las suscripciones de un plugin se
quitan solas al deshabilitarse. Ver `betterchats-example`.
