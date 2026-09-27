# DIGIMON PROJECT — Contexto para Claude Code

Asistente virtual de escritorio estilo V-Pet (Java 17 + JavaFX 21.0.4, Gradle),
que lee tarjetas DIM reales (`.bin`) con la librería `VB-DIM-Reader` de
cfogrady. Chat con IA local (Ollama, ministral-3:3b). Todo el contenido de este
archivo está confirmado contra código fuente real (propio o de las
herramientas de referencia DIM-Modifier / VitalWear) — no hay suposiciones
sin marcar como tales.

Ruta del proyecto: `D:\FERNANDO\PROYECTO PERSONAL\DIGIMON PROJECT\DIGIMON-ASSISTANT 0.0.3`
Ejecución: `.\gradlew.bat run`

**0.0.3 = asistente, SIN crianza** (depuración ejecutada el 2026-09-26, rama
`feat/purga-crianza`; la versión con crianza vive en la carpeta `0.0.2`).
Tres conceptos: (1) IA local para chat y asistente, (2) lectura de DIM solo
para darle "rostro" (sprites, stats, atributo, Activity Type, especie), (3)
VS online (por construir). Se quitaron: huevo/eclosión, hambre, suciedad,
malestar/corazones/curar, entrenamiento y minijuegos, evolución, Jogress,
muerte/vida útil/renacimiento, Adventure, selección de huevos, OCR por
plantillas. Decisiones del usuario: los Digimon llegan SOLO por VS DIM, en
forma fija; se conserva Batalla aleatoria.

## 1. Arquitectura de paquetes

```
org.example
├── Main.java (pantalla de inicio -> "Cargar mi Digimon (VS DIM)", edad, máx. 2)
├── animation/ (DimAnimationPlayer, DimSpriteSet, TeleportAnimator,
│               PortalSpriteSheet, VPetStageTier, VPetAnimations, SpriteRole)
├── battle/ (BattleEngine, AttackSpriteResolver, BattlePresentationScreen,
│            BattleScreenHud, PortalTransitionScene, PowerTrophyBonus,
│            RivalDimPool -- rivales de Batalla aleatoria)
├── behavior/ (DigimonInstance -- archivo principal, DigimonRegistry,
│              CompanionController -- pasear/reacciones, DigimonProgress --
│              VP/trofeos/récord, DigimonAge, VPetMovementController)
├── chat/ (AiConversationController, ChatMemory, DigimonNameIdentity,
│          SpeechTraits, StageVoice, EmotionalSupportPolicy/Signals,
│          AssistantCommands/Settings, NewsService, MusicControl,
│          SpeciesNameReader, OllamaClient, ChatPanel, SpeechBubble...)
├── dim/ (DimVPetData, VsDimReader/VsDimData, DimSpriteImageFactory)
├── interaction/ (VPetClickToMoveController)
└── ui/ (VPetMenu, DigimonInfoPanel, StartScreen, NameSpriteBanner)
```

## 2. Formato DIM — hechos confirmados con código fuente real

- Etapas crudas: 0=Baby I, 1=Baby II, 2=Child, 3=Adult, 4=Perfect, 5=Ultimate.
  `ULTIMATE_DIM_STAGE = 5`.
- Sprites por personaje según etapa (confirmado en `DimCardDataReader`):
  Baby I = 6 sprites, Baby II = 7 sprites, Child+ = 14 sprites.
  Orden Child+: NAME, IDLE_1, IDLE_2, WALK_1, WALK_2, RUN_1, RUN_2, TRAIN_1,
  TRAIN_2, VICTORY, SLEEP, ATTACK, DODGE, SPLASH(opcional).
  Orden Baby: NAME, IDLE_1, IDLE_2, WALK_1, VICTORY, SLEEP, SPLASH(opcional).
- `CharacterStatsEntry` (confirmado en `DimStatsReader`): stage,
  unlockRequired, attribute, **type** (= Activity Type), smallAttackId,
  bigAttackId, dpStars, dp, hp, ap, firstPoolBattleChance,
  secondPoolBattleChance.
- Atributo: 0=Null, 1=Virus, 2=Data, 3=Vaccine, 4=Free.
- **Activity Type** (`entry.getType()`, confirmado en `StatsGridController`
  de DIM-Modifier): 0=Stoic, 1=Active, 2=Normal, 3=Indoor, 4=Lazy.
- **Super Hit**: determinístico, sin azar — ocurre cuando
  `roundNumber == activityType + 1`, y SOLO si ese Digimon es quien gana
  esa ronda. Bonus de daño: **+2 AP** sobre su AP normal.
- **Small/Big Attack**: `smallAttackId`/`bigAttackId` son índices directos
  (sin offset) a los sprites genéricos del firmware Vital Bracelet — NO a
  la tabla de sprites propia del personaje. Confirmado con
  `SpriteArrayFirmware`/`FimwareData20a` de DIM-Modifier: Big Attack rango
  0-21 (22 sprites), Small Attack rango 0-38 (39 sprites). Los recursos
  extraídos viven en `resources/attacks/atk_s_00..38.png` y
  `atk_l_00..21.png`. Un ID fuera de ese rango es un **ataque personalizado
  de tarjeta (BEM)** — fuera de alcance a propósito, ver sección 7.
- Pool de rival en Battle Random: `firstPoolBattleChance` para etapa
  Child/Adult (2-3), `secondPoolBattleChance` para Perfect/Ultimate (4-5).
  Baby I/II SIEMPRE excluidos del sorteo. `NONE_VALUE = 0xFFFF = 65535`
  significa "no aplica", nunca un peso real — filtrarlo explícitamente
  (bug real que existió y se corrigió esta sesión).
- **VS DIM** (Digimon extraído del VB): **NO es una DimCard normal** y
  VB-DIM-Reader **no puede abrirla** (truena en `UnorderedSpriteReader`: la
  zona de sprites no trae el paquete `GP_SPIFI`). Un dato anterior de este
  archivo decía lo contrario y era falso: aquella captura de DIM-Modifier
  era una DIM card normal. Se lee con `VsDimReader` propio. Formato
  confirmado comparando byte a byte 3 archivos reales (todo con NOT, u16
  LE): `0x10000` dword de flags VS (≠0 = hay Digimon); `0x30000` tabla de
  stats COMPLETA de la DIM de origen; `0x40000` estado del Digimon: [1]
  Vital Values (confirmado), [2] = 100 en los 3 archivos (sin identificar),
  [3] slot (confirmado), [6] Power Trophies del mod Digimon Link
  (confirmado); `0x60000` tabla de dimensiones completa; `0x100000` SOLO
  los sprites del Digimon transferido, RGB565 crudo sin índice. No guarda
  edad, ID de la DIM de origen ni sprites de otros slots; los trofeos
  normales de un VB SIN mod quizá sí (ver Power Trophies, sin confirmar).
  **Tampoco guarda batallas ni victorias**: confirmado con "VS DIM
  MagnaKidmon, Battle step.bin" (4 batallas, 4 victorias) — frente al
  archivo anterior solo cambiaron 4 bytes: Vital Values (4997 → 5985, +988
  solo por esas 4 victorias según el usuario, sin caminar ni entrenar:
  promedio 247 por victoria; sin confirmar si cada victoria da lo mismo ni
  cuánto quita una derrota) y el checksum. **Vital Values por victoria en el
  VB real** (observado por el usuario): rival Ultimate +500, Perfect +200,
  Adult +80; Child +30 PROVISIONAL (sin observar). Derrota: el castigo real
  no se identificó; decisión de diseño: restar la misma cantidad,
  PROVISIONAL. Todo lo provisional debe ser configurable (como los
  umbrales de rango), nunca fijo en código. Ojo: 988 no se arma con 4
  victorias de 500/200/80 (500+200+200+80 = 980) — sin explicar todavía. **Checksum VS DIM**: u16 LE en `0x120000` = suma de 16 bits de
  todos los u16 (ya con NOT) del bloque `[0x10000, 0x120000)`; confirmado
  en 3 archivos. El checksum de DIM normal en `0x3FFFFE` NO cambia. Para
  escribir una VS DIM hay que recalcular el de `0x120000`. **Escritura HECHA**
  (`VsDimWriter`): el checksum se guarda con NOT como todo lo demás
  (confirmado en 5 archivos); escribir MagnaKidmon con 5985 VV da un archivo
  IDÉNTICO byte a byte al "Battle step" que produjo el VB. Se niega a partir
  de un archivo con checksum inválido: "Resultado del VS.bin" lo tiene
  inválido (editado, no salió tal cual del VB; sus [0]=2 [4]=3 [5]=1 no son
  prueba de nada).
- **Power Trophies** (mod "Digimon Link Pendulum", PDF de investigación
  del mod): cada 10 = +50% DP, +25% HP, +1 AP (`PowerTrophyBonus`,
  verificado con MagnaKidmon: 35 → DP+75, HP+9, AP+3). **Rango por puntos
  = campo [6]** (decisión del usuario, 2026-09-26): C desde 10, B desde 30,
  A desde 70, S desde 120 (tope); menos de 10 = sin rango ("-"). Configurable
  en `vs-server.properties`. Mismos puntos para el bono, con **TOPE de 120**
  (`PowerTrophyBonus.MAX_TROPHIES`, decisión del usuario): más puntos ya no
  suben el bono (máx. 12 bloques). La idea: VB con mod
  = Power Trophies; VB original = trofeos de evolución. **SIN CONFIRMAR que
  un VB original guarde sus trofeos en [6]**: los archivos del VB con mod
  (flags `ba993504`: MagnaKidmon 35, Dynasmon 67, Gulusgammamon 0) no lo
  prueban. Hay VS DIM de otros equipos en `D:\FERNANDO\DIGIMON\HERRAMIENTAS\DIMCARDTOOL`
  (flags `a234a404`: Hedorah [6]=100, "Resultado del VS" [0]=2 [4]=3 [5]=1;
  flags `52195804`: Arcturus y ghill, [6]=0, ghill [2]=25) — falta saber de
  qué VB salió cada uno y cuántos trofeos mostraba. Decisión del usuario:
  el bono SOLO aplica en el VS online.
- Color verde puro = transparencia, confirmado en el propio
  `SpriteData.Sprite.getBGRA()` de la librería (no es un supuesto nuestro).

## 3. Sistemas funcionando

**Digimon en el escritorio** (sin crianza): llega por VS DIM
(`DigimonInstance.fromVsDim`, edad preguntada al importar), forma fija,
**aparece saliendo de un portal** (`TeleportAnimator.playExit`, pedido del
usuario; recién después pasea), pasea según el modo (quieto/barra/libre,
`CompanionController`), click izquierdo para moverlo, click derecho para el
menú. Datos: `DigimonProgress` (Vital Values y Power Trophies de la VS DIM +
récord de batallas con SALDO de Vital Values) y `DigimonAge` (edad del VB +
días en el programa; sin muerte).

**Récord y retiro** (pedido del usuario): TODA batalla (aleatoria y Batalla
Oficial, Libre u Original) suma/resta Vital Values según la ETAPA DEL RIVAL
(`VitalRewards`, `recompensas.properties`: Ultimate 500, Perfect 200, Adult
80, Child 30 provisional; derrota resta lo mismo, provisional; empate 0). La
Batalla Oficial se registra al RECIBIR el resultado, no al terminar la
animación (cerrar la sala a media pelea no borra una derrota). Menú V-PET >
**RETIRAR**: muestra batallas, VV antes → después y el veredicto (saldo
positivo = vuelve como victoria, negativo = derrota), escribe una VS DIM NUEVA
desde la original (`VsDimWriter`, VV entre 0 y `vitalValues.maximo`=9999,
supuesto) en `AppPaths.returns()` (`...\DigimonProjectData\devoluciones`,
nombre "VS DIM <especie> <fecha> (+N VV).bin"), el Digimon entra al portal,
se abre el Explorador con el archivo seleccionado y vuelve la pantalla de
inicio (se muestra ANTES de cerrar la ventana del Digimon: sin ventanas,
JavaFX cerraría el programa). La VS DIM no tiene un campo conocido de
"ganó/perdió": el resultado viaja como Vital Values. Sin persistencia: si se
cierra el programa sin retirar, el saldo se pierde. **PROBADO EN EL VB REAL
(2026-09-26, usuario): NO FUNCIONA como transporte de resultados.** Una
devolución con -500 VV fue aceptada por el VB (checksum válido), pero el
Digimon volvió como si no hubiera peleado: sin recompensa ni castigo. El VB
IGNORA los Vital Values escritos en la VS DIM al recibirla (probablemente usa
su propia copia interna). Falta descubrir cómo el VB recibe un resultado de
batalla por VS DIM: pista sin confirmar = "Resultado del VS.bin" (flags
`a234a404`, [0]=2 [4]=3 [5]=1, checksum inválido) — averiguar de dónde salió.
Hasta entonces, RETIRAR devuelve al Digimon sin cambios reales.

**Identidad de nombre** (sistema narrativo): `DigimonNameIdentity` guarda
`slotNameHistory` (mapa slot→nombre), `uniqueName` opcional, y un flag
`justReachedUnnamedSlot`. Si el usuario le dice un nombre terminado en
"mon", se acepta directo como nombre de esa forma; si no, la IA pregunta si
es para toda la vida (`uniqueName`) o solo esa forma. La ESPECIE se lee del
sprite NAME por visión (ver sección 6), aparte del nombre propio.

**Chat IA** (Ollama, ministral-3:3b, `http://localhost:11434`; modelos en `D:\DigimonProjectData\models`):
- **Modelo elegido: ministral-3:3b** tras comparar en el hardware real
  (i7-2670QM sin AVX2, 16 GB, solo CPU, ~2.2 tok/s) contra qwen2.5:3b,
  qwen3:4b y qwen3.5:4b (resultados en `D:\DigimonProjectData\bench\`):
  el de más personalidad y diferencia por etapa, 25-80s por respuesta.
  qwen3:4b descartado (escribe su razonamiento en inglés aun con
  `think:false`). Sus defectos (emojis, `**negritas**`, `*acciones*`,
  divagar) los limpia `ReplyCleaner` + tope de frases por etapa
  (`StageVoice.maxSentences`: Baby 2, Child/Adult 3, Perfect/Ultimate 4).
- **Asistente = comandos detectados por el PROGRAMA** (`AssistantCommands`),
  no tool calling del modelo: en la comparación NINGÚN modelo lo hizo
  confiable (inventaron la hora; qwen3.5 prometió un recordatorio sin
  crearlo). El programa reconoce "¿qué hora/día es?", "recuérdame X en N
  minutos/horas" y "abre X" (solo lista blanca fija de apps, nunca texto
  del usuario como comando), ejecuta, y pasa el resultado al modelo solo
  para que lo cuente con su voz; la hora/fecha exacta se agrega si el
  modelo no la dice. El aviso del recordatorio es texto fijo (el modelo
  tardaría 30-80s en redactarlo). Comandos asíncronos
  (`tryHandleAsync`) para no bloquear la interfaz con internet.
- **Noticias** (`NewsService`): Google Noticias por RSS (gratis, sin clave),
  país configurable; la IA solo cuenta titulares REALES. Por iniciativa
  propia (`maybeShareNews`, decisión del usuario) cada N minutos, nunca si
  hay señales de malestar/crisis. `SystemTrust`: Java confía en su almacén
  O en el de Windows — en esta laptop Avast intercepta el HTTPS de Java
  ("PKIX path building failed"); nunca desactivar la verificación.
- **Música** (`MusicControl`, decisión del usuario: su reproductor, pero
  que el programa pueda elegir): elige un archivo de su carpeta y lo abre
  en el reproductor predeterminado; pausa/siguiente/anterior con teclas
  multimedia de Windows; búsqueda en YouTube/Spotify (ahí el usuario da
  play: Spotify no permite reproducir una canción concreta sin su API de
  pago).
- **Ajustes** en `D:\DigimonProjectData\config\assistant.properties`
  (`AssistantSettings`, se relee en cada uso): país de noticias,
  iniciativa propia on/off e intervalo, carpeta de música. Irán a la
  pantalla "Funciones" en el rediseño de la interfaz.
- `num_ctx` subido a 2048: con 1024 el prompt (~700) + titulares +
  respuesta no cabían.
- Prompt modular en 6 capas: CONTEXTO BASE (identidad de AMIGO + regla
  primera persona/especie≠nombre + `EmotionalSupportPolicy.SAFETY_TEXT`,
  cambia poco), PERFIL (`SpeechTraits` por Activity Type/Attribute + nombre
  actual, recalculado en cada cambio de slot), MEMORIA (resumen + mensajes
  recientes), CONTEXTO DINÁMICO (instrucciones de nombre — solo si hay
  confirmación pendiente, slot nuevo sin nombre, o palabras clave), APOYO
  (solo si hay señales, ver abajo), y VOZ DE LA ETAPA (`StageVoice`, al
  FINAL a propósito: un modelo de 3B obedece más lo último que lee).
- **Apoyo emocional condicional** (decisión del usuario, reemplaza la capa
  "siempre activa" anterior, que hacía que un Baby I sonara preocupado y
  maduro sin motivo): `EmotionalSignals` revisa por palabras clave los
  últimos 3 mensajes del usuario → `DISTRESS` agrega
  `EmotionalSupportPolicy.supportText(etapa)` DESPUÉS de la voz (un Baby
  consuela como pequeño, sin consejos; con la voz última un Baby I
  respondió a "tuve un día horrible" con "¿te gusta el azul?"). **Crisis:
  si el mensaje ACTUAL trae señales de riesgo, la respuesta es fija
  (`CRISIS_REPLY`) y NO pasa por el modelo** — probado: qwen2.5:3b, aun con
  la instrucción al final, preguntó "¿has pensado en qué podría cambiar si
  no siguieras?". Los mensajes siguientes dentro de la ventana sí van al
  modelo con `CRISIS_TEXT` al final y `ensureCrisisGuidance` como garantía
  en código. Máx. 1 pregunta por mensaje también se aplica en código. `SAFETY_TEXT` sigue siendo
  permanente y mínimo (riesgo real + "no asumas que está mal") como red de
  seguridad si la detección por palabras falla. Nunca depende de
  `personality_base.txt`.
- **Voz por etapa** (`StageVoice`): reglas concretas por etapa (largo de
  frase, qué NO hace, qué tipo de preguntas hace para conocer al usuario) +
  pauta de amistad común ("estás conociéndolo", máx. 1 pregunta por
  mensaje, retoma lo que te contó). Baby I/II: tope duro de 2 frases en
  código (`StageVoice.limitLength`), excepto en crisis.
- `build.gradle` fuerza `options.encoding = 'UTF-8'`: sin eso javac en
  Windows leía los .java como windows-1252 (errores intermitentes y textos
  en español corruptos, incluido el prompt).
- `PersonalityBaseLoader` lee `D:\DigimonProjectData\config\personality_base.txt`,
  se recarga al aparecer el Digimon y cada vez que se abre el chat (hot reload).
- Rendimiento: `keep_alive=30m`, `num_ctx=1024`, `num_thread=4`,
  `GenerationProfile` (CHAT vs SPONTANEOUS, límites de tokens distintos),
  `warmUp()` al arrancar el programa, un solo hilo de ejecución (nunca
  peticiones paralelas), logging `[CHAT START]/[OLLAMA RESPONSE]/[CHAT END]`
  con métricas reales de Ollama.
- Etiquetas de nombre (`##NAME_CANDIDATE:...##` etc.) se parsean con
  regex tolerante (0-2 símbolos `#`, `<>` opcionales) + una red de
  seguridad que limpia cualquier `##...##` residual — un modelo de 3B no
  es 100% consistente con el formato exacto.

**Teletransportación** (`TeleportAnimator`): coreografía completa —
camina/corre al centro según distancia → Idle x2 → portal aparece con
espacio real (`PORTAL_CLEARANCE`) → Idle x2 más → camina hasta el borde
del portal → fase combinada de caminar+desvanecer en 8 pasos de opacidad
(100/90/80/70/50/30/10/0%, congela el frame de WALK al llegar a 30%) →
desaparece → portal se cierra. `playExit` es el camino simétrico, con
`targetX/targetY` reales (nunca asume dirección). El portal se espeja
(`scaleX`) según hacia dónde mira, con una regla **no intuitiva y ya
verificada**: en la entrada, el portal debe mirar en dirección **opuesta**
a hacia dónde camina el Digimon (`-dirX`); en la salida, en la **misma**
dirección (`dirX` directo) — son geometrías distintas, no cambiar esto sin
volver a probar ambos casos.

**Convención de orientación** (confirmada en `VPetMovementController`,
reutilizada en todo lo demás): moverse/mirar a la **derecha** → `scaleX =
-1`. Moverse/mirar a la **izquierda** → `scaleX = 1`. Es así en TODO el
proyecto, no inventar una segunda convención.

**Battle Random** (`BattlePresentationScreen`): fondo real del coliseo
(`resources/Battle coliseum.png`, ver sección 4), ambos Digimon
en tamaño nativo (nunca `fitWidth`/`fitHeight`) dentro del óvalo de la
arena, posicionados por fracciones del fondo (no píxeles fijos, sobrevive
a cambios de tamaño de ventana). `BattleEngine.RoundOutcome` es la única
fuente de verdad — la presentación NUNCA recalcula daño/atacante/tipo de
ataque, solo los reproduce. Cada ronda: PLAYER actúa primero, luego ENEMY
(salvo que el impacto de PLAYER ya deje algún HP en 0, ahí se corta la
ronda). El que gana la ronda usa su ataque real (`attackType`/`attackId`
de `RoundOutcome`, resuelto vía `AttackSpriteResolver`); el que pierde usa
SIEMPRE su `smallAttackId` (el motor solo evalúa Big Attack para quien
realmente gana, nunca inventar Big Attack fallido) y hace `DODGE` con un
pequeño salto sincronizado al recorrido del proyectil (arranca al 75% del
trayecto, el proyectil sigue de largo con `MISS_OVERSHOOT_DISTANCE=80` y
un fundido progresivo en el último 25% del tramo de sobrepaso). HIT no
tiene fundido, `impact.png` mantiene el protagonismo. Ventana calculada
como `alto = pantalla*0.62`, `ancho = alto*BG_ASPECT` (nunca al revés,
para no deformar el fondo).

**Pantallas del coliseo** (`BattleScreenHud`, dentro de
`getLeftScreenOverlay()`/`getRightScreenOverlay()`): izquierda = jugador,
derecha = rival (diseño espejado, rostros mirando al centro). Cada una
muestra rostro (recorte heurístico de IDLE_1: cuadrado del 70% del lado
menor arriba, anclado al frente si el sprite es ancho — la DIM no trae
retrato), sprite NAME nativo con scroll (`NameSpriteBanner`), y barra de
vida estilo VB Arena **sin número de HP** (decisión del usuario) con
insignia circular de atributo (Va/Da/Vi/Fr/--) + nombre del atributo.
Colores de atributo y de HP (verde >50%, amarillo >25%, rojo) son
elección nuestra, no extraídos de VB Arena. El HUD se diseña en píxeles
nativos del PNG (246×150) y se escala con el fondo. Se usa igual en
Batalla aleatoria (y servirá para el VS online). El marco es
parte del fondo, no se redibuja; el splash art futuro va en los mismos
overlays.

**Menú** (`VPetMenu`, diseño del usuario): clic derecho → panel oscuro al
COSTADO del Digimon con flechita que lo señala (a la izquierda si no cabe a
la derecha). Página V-PET (Batalla aleatoria directa [solo Child+],
Movimiento, Digimon, "ASISTENTE ›") y página ASISTENTE
(Hablar, Abrir programa, Música, Funciones, "‹ V-PET"); cada categoría abre
una subpágina con "‹ VOLVER". Los botones del asistente actúan AL
INSTANTE sin pasar por la IA y el Digimon confirma con
`AiConversationController.announce()` (texto fijo); noticias y
recordatorio sí pasan por el chat. Funciones → Ajustes guarda
`assistant.properties`. Íconos: símbolos de texto con color, PROVISIONALES
(los del diseño son pixel art, pendientes). Ojo: en el HBox de la flecha
usar `viewOrder`, no `toFront()` (reordena los hijos y cambia de lado la
flecha).

**Panel "Digimon"** (`DigimonInfoPanel`): sprite NAME real como imagen
(`DimSpriteImageFactory.toNativeImage`, nunca OCR), atributo, Activity
Type, días desde el nacimiento (`VPetLifespan.getBirthMillis()`), portal
"Vital Bracelet"-style (fondo cian, franja de nombre con scroll). Debajo del
retrato (pedido del usuario): rango (o "SIN RANGO"), puntos (con "TOPE 120"
si pasan), cuántos faltan para el siguiente rango, y tabla DP/HP/AP con la
BASE de la DIM y el BONO de los puntos (`PowerTrophyBonus`), aclarando que
el bono solo cuenta en la Batalla Libre del VS Online. Los umbrales salen de
`DigimonRank` (clase pública compartida con `ServerConfig`, lee
`vs-server.properties` de ESTA PC). El fondo cuadriculado se mide ya dentro
de la escena del popup (fuera de escena medía corto y el texto quedaba
blanco sobre blanco).

## 4. Recursos de arte generados (pixel art, sin blur, cuadrícula
baja-res + escalado con vecino más cercano)

- `PortalSpriteV3.png`: portal ovalado 3/4, 104×110×14 frames
  (`PortalSpriteSheet.FRAME_WIDTH=104`, `FRAME_HEIGHT=110`,
  `MATERIALIZE_FRAMES=6`, `LOOP_FRAMES=8`). Mira hacia la izquierda por
  defecto (`DEFAULT_EFFECT_FACES_RIGHT` en batalla usa la misma
  convención invertida para los efectos de ataque).
- `attacks/impact.png`: destello de impacto genérico, 48×48, estático.
- `Battle coliseum.png`: fondo de batalla. **El PNG real mide 1671×941**
  (el código aún usa `BG_NATIVE_W/H = 1920×1080` para `BG_ASPECT`; la
  diferencia de proporción es ~0.1%, inofensiva). Gradas como arcos
  concéntricos reales alrededor de un pivote compartido con la arena
  (no una pared plana), arena con grosor visible real (doble elipse),
  monumento central anidado en la curva (sin rampa), 2 pantallas.
  Coordenadas clave si hace falta recalcular posiciones: pivote en
  fracción (0.5, 246/270), radio Y de arena 66/270. Área útil de las
  pantallas (medida sobre el PNG de 1671×941): centro izq. (182, 209),
  centro der. (1489, 209), 246×150 cada una.

## 5. Bugs conocidos, sin resolver

Ninguno registrado en 0.0.3 (los de evolución y Jogress desaparecieron con
la depuración; siguen existiendo en 0.0.2).

## 6. Decisiones deliberadas — no re-litigar sin nueva evidencia

- **OCR por plantillas: descartado** (fuentes DIMNameGen, comparación de
  píxeles — la fuente real no coincide con ninguna plantilla).
  `DigimonNameRecognizer` y sus fuentes se borraron en la depuración de 0.0.3.
  **Reabierto con evidencia nueva (2026-09-26): lectura por VISIÓN** con el
  mismo modelo local (ministral-3:3b), `SpeciesNameReader`. Probado con 9
  sprites NAME reales: leyó perfecto los de alfabeto latino (MagnaKidmon,
  Dynasmon) y falló TODOS los de katakana (DIM de Agumon). El usuario
  confirmó que sus Digimon usan letras latinas. Lo leído es la ESPECIE, no
  el nombre propio (regla especie≠nombre): se muestra solo si no hay nombre
  propio, y la IA lo recibe como "tu especie". Se valida (latino, termina
  en "mon"), se corrige contra `KnownDigimonNames` (sin adivinar en
  empates), y se guarda una sola vez por hash de píxeles en
  `D:\DigimonProjectData\config\especies_leidas.properties` (tarda
  1.5-4.5 min en esta CPU y comparte la cola con el chat). Riesgo conocido:
  un katakana mal leído como texto latino válido puede pasar (visto:
  アグモン -> "Jjimon" -> "Jijimon"). Si no se puede leer: se muestra
  "Digimon" — **nunca el nombre del archivo** — y el flujo de nombre
  existente hace que el Digimon lo pregunte.
- **Sin ataques personalizados de tarjeta (BEM).** Confirmado que existen
  como concepto (y que hay un bug de offset conocido en DIM-Modifier para
  esa parte específica), pero fuera de alcance por ahora. Un ID de
  ataque fuera del rango del firmware genera un log `[ATTACK RESOLVE]` y
  no muestra efecto — es comportamiento esperado, no un bug.
- **Sin segunda tirada de evasión.** El modelo de combate actual es una
  sola tirada por ronda que decide quién golpea — el "MISS" visual del
  perdedor es una interpretación de presentación, no una regla nueva de
  combate. No agregar una tirada de esquive independiente sin decisión
  explícita, porque cambiaría el balance matemático real.

## 7. Pendiente / próximos pasos conocidos

- **VS online**: documento de diseño aparte (claude.ai, "Digimon VS Online —
  Documento de diseño"). **Fase 1 (esqueleto de red) HECHA** en
  `org.example.online`: `Protocol` (TCP, un JSON por línea, `org.json`,
  puerto 7777, sala 800×500, máx. 20 jugadores, tope de 4 KB por línea),
  `server/VsServer` + `ClientConnection` (servidor autoritativo: el cliente
  solo manda su DESTINO y el servidor mueve a 160 u/s, tick 20 Hz, foto a
  10 Hz; valida todo: hello obligatorio, versión, nombres y chat limpiados
  y recortados), `client/VsClient` (sin JavaFX) y `LobbyTestWindow` (prueba
  con cuadrados, clic para caminar, chat). Ejecutar:
  `.\gradlew.bat runServer` y `.\gradlew.bat runClient --args="--ventanas=2"`
  (también `--host`, `--port`, `--name`). Probado con 2 jugadores
  automáticos + 2 ventanas reales. **Mapa del 1er piso HECHO**
  (`LobbyMap`, protocolo v2): FORMA del Colosseum 1F - Lobby de Digimon
  World Re:Digitize (referencia del usuario), ESTILO del boceto "Edificio
  Torneo - 1er piso" del usuario. Cuadrícula 50×31 de 16 u (800×496):
  pasillo oeste-este, embudo norte con mostrador de muro a muro y 2 NPC
  (Batalla oficial / Recepción de torneos, en los extremos), terminal de
  tabla de posiciones (donde estaba Digistorage), Training Room en cruz al
  oeste BLOQUEADA, portal este a pisos superiores BLOQUEADO, entrada al
  sur (spawn). El servidor calcula el camino (BFS 8 direcciones, sin cortar
  esquinas) a la casilla ALCANZABLE más cercana al clic; candado propio
  `moveLock` para camino+índice (separado del de envío). Cliente:
  `LobbyMapRenderer` = dibujo PROVISIONAL con figuras simples (el arte
  final será una imagen pixel art sobre la misma cuadrícula) + globo de
  aviso al acercarse a un NPC/lugar. Probado: 171 posiciones sin tocar
  muros. **Avatar humano HECHO** (`AvatarSprites`, cliente): UN solo avatar
  por ahora (masculino del boceto del usuario: cabello castaño, polo gris,
  jean azul, zapatillas blancas); el editor de personajes (cabello, torso,
  piernas, pies; boceto del usuario) vendrá después. Pixel art 16×24 px
  definido con PLANTILLAS de texto + PALETA separada (el editor solo
  cambiará la paleta). Direcciones DOWN/UP/SIDE; SIDE mira a la izquierda
  y a la derecha se ESPEJA en código (scaleX -1, decisión del usuario, misma
  convención del proyecto). Cuadros: 0 quieto, 1-2 caminar; la dirección y
  el cuadro los deduce el cliente del movimiento (`AvatarAnim`); se dibuja
  por orden de altura y con marcador cian sobre el propio jugador.
  **Digimon que sigue al avatar + rango HECHO** (protocolo v3): el cliente
  manda su Digimon una vez (`LobbyDigimon.payloadFrom`): especie, atributo,
  etapa, Power Trophies y 4 cuadros (IDLE_1, IDLE_2, WALK_1, WALK_2) como
  píxeles RGB565 CRUDOS de la VS DIM — NUNCA archivos de imagen (ningún
  jugador puede mandar un PNG malicioso al decodificador de los demás). El
  servidor valida cada campo y tamaño, calcula el RANGO él mismo
  (`ServerConfig`, umbrales en `vs-server.properties`: C≥10, B≥30, A≥70,
  S≥120, menos = sin rango "-", sin insignia) y reparte solo especie/atributo/etapa/rango/
  cuadros (nunca stats ni trofeos). Cliente: dibuja el Digimon a la MITAD
  (promedio 2x2), lo mueve con una "correa" de 24 u detrás del avatar
  (solo visual, sin tráfico extra), IDLE/WALK alternados, espejado a la
  derecha; insignia de rango (C gris, B azul, A naranja, S dorado,
  colores provisionales) junto al nombre. Prueba: `--vsdim="a.bin;b.bin"
  --especie="MagnaKidmon;Dynasmon"`.
  **Seguridad del servidor**: tope de línea 64 KB; límites por jugador
  (`RateLimiter`: chat 1/s ráfaga 5, movimientos 20/s) y 4 conexiones por
  IP (configurables); desconexión por inactividad a los 90 s (el cliente
  hace ping cada 30 s); cola de salida con tope y hilo propio por conexión
  (un cliente que no lee no frena a nadie); aviso del motivo antes de
  cerrar. Ojo: NO usar try-with-resources sobre el InputStream del socket —
  cerrarlo cierra todo el socket y se perdía el aviso final (bug real,
  corregido). **Botón de emergencia**: consola del servidor → `apagar`
  (avisa a todos, cierra y termina), `expulsar <n>`, `jugadores`, `ayuda`.
  Probado: rangos B/A correctos, el que llega tarde recibe los Digimon
  existentes, Digimon manipulado rechazado con motivo, 12 mensajes → 5
  pasan, 5ª conexión por IP rechazada, `apagar` cierra avisando.
  **Entrada desde el programa principal HECHA**: menú V-PET > BATALLA ahora
  abre subpágina (ALEATORIA = Batalla aleatoria, solo Child+; VS ONLINE =
  cualquier etapa). VS ONLINE (`DigimonInstance.vsOnlineAction`): animación
  de entrada al portal (TeleportAnimator, igual que Batalla aleatoria) ->
  abre `LobbyWindow` (la sala, clase pública reutilizable; LobbyTestWindow
  ahora es solo un lanzador de prueba) con tu avatar y tu Digimon (especie
  = la leída por visión o "Digimon") -> al cerrar la sala, animación de
  salida y vuelta al escritorio. Servidor/puerto/nombre de jugador en
  `assistant.properties` (`online.servidor`=127.0.0.1 por defecto o la IP
  de Tailscale del anfitrión, `online.puerto`=7777, `online.nombreJugador`
  vacío = usuario de Windows); `AssistantSettings.save` los conserva al
  guardar desde la pantalla de Ajustes. Tailscale del usuario: IP
  anotada en ~/.gradle/gradle.properties (servidorProbadores), fuera del
  repo porque el repo es público (probado: conexión por esa IP funciona).
  **Pantalla de inicio con nombre de usuario** (`StartScreen`): primero el
  nombre (tope 16, sin correo/contraseña por ahora, decisión del usuario),
  luego "Cargar mi Digimon (VS DIM)"; se guarda en `online.nombreJugador`
  (`AssistantSettings.savePlayerName`) y se precarga la próxima vez.
  **Servidor local automático**: si la sala no encuentra servidor y el
  host es 127.0.0.1/localhost, `VsServer.startEmbedded(puerto)` lo arranca
  DENTRO del programa (sin consola, "apagar" no cierra el V-Pet) y la sala
  reintenta con un `VsClient` nuevo (un socket fallido no se reutiliza).
  Con otro host (Tailscale) no se arranca nada: se avisa. `vs-server.properties`
  vive en `D:\DigimonProjectData\config` (antes, en la carpeta de trabajo).
  **Batallas Oficiales HECHAS** (protocolo v4, flujo del usuario): al llegar
  al NPC "Batalla oficial" se abre su diálogo (`OfficialBattleUi`): ponerse
  disponible o no + lista de disponibles (se refresca cada 3 s); clic en un
  nombre = retar: el retador ve "Esperando respuesta..." y el retado "[nombre]
  te ha retado a una batalla, ¿aceptar?", ambos con contador de 15 s; sin
  respuesta = rechazo (lo decide el SERVIDOR, `OfficialBattles`). Si acepta,
  el servidor calcula la pelea UNA vez (BattleEngine + PowerTrophyBonus,
  solo online) con la regla de empate del documento de diseño (más HP; con
  el mismo HP, más daño total; si no, EMPATE real, `BattleResult.draw`) y
  manda el MISMO mensaje a ambos; cada cliente lo reproduce desde su lado
  (él a la izquierda) en el coliseo con el HUD, y al terminar vuelve la sala
  y avisa `battleDone`. Tras pelear, ambos quedan NO disponibles. Solo pelean
  Child o superiores. El Digimon del escritorio comenta el resultado
  (`LobbyWindow.setOnBattleResult` -> VPetEvent), sin tocar DigimonProgress.
  El mensaje "digimon" ahora trae 6 cuadros (+ATTACK, DODGE), el sprite NAME
  y los STATS (dp, hp, ap, ataques, Activity Type) que SOLO ve el servidor;
  al rival solo le llega lo que la animación necesita (HP máximo con bono,
  atributo, ataque pequeño). Límite: un reto cada 3 s. Tope de línea 128 KB.
  Probado: aceptar (misma pelea en ambos), rechazar, 15 s sin respuesta,
  retar a un no disponible, stats nunca repartidos, y la reproducción en el
  coliseo con vuelta a la sala.
  **Modos de Batalla Oficial** (protocolo v5, decisión del usuario): en el
  diálogo del NPC se elige BATALLA LIBRE (stats + bono de puntos, tope 120)
  o BATALLA ORIGINAL (stats de la DIM, sin bono); el reto lleva `mode`
  ("libre"/"original", otro valor = rechazado), el retado ve el modo antes
  de aceptar y el mensaje "battle" lo repite. Probado: MagnaKidmon (35) vs
  Dynasmon (67): Libre HP 21 vs 30, Original 12 vs 12.
  Siguiente: cuentas (Fase 3). Usará BattleEngine + coliseo + HUD +
  PowerTrophyBonus en la Fase 4.
- **0.0.3.1 (probadores, sin asistente ni chat IA)**: MISMO código con
  `-Ddigimon.edicion=tester` (`Edition.ASSISTANT=false`): sin Ollama, sin
  página ASISTENTE, sin chat (el globo muestra textos fijos, p. ej. "¡Gané!
  +500 Vital Values."), la especie la escribe el usuario al importar (no hay
  visión), la carpeta de rivales se elige al pedir Batalla aleatoria (no hay
  pantalla de Ajustes). La pantalla de inicio ahora pide también el SERVIDOR;
  `-Ddigimon.servidor` fija el de por defecto (IP de Tailscale del
  anfitrión). `AppPaths`: si no hay disco D:, usa `%USERPROFILE%\DigimonProjectData`.
  `.\gradlew.bat runTester` = probarla desde el código; `.\gradlew.bat
  packageTester` = jpackage app-image con Java incluido (lanzador
  `org.example.Launcher`, porque con JavaFX en el classpath no se puede
  arrancar directo una clase que extiende Application) + ejecutable
  "Servidor VS Online.exe" con consola, en `..\DIGIMON-ASSISTANT 0.0.3.1\`
  (carpeta + .zip de ~61 MB + `tester\LEEME.txt`). Gradle 9: sin
  `project.exec` (se usa ProcessBuilder). Probado: servidor empaquetado con 2
  jugadores automáticos, y el .exe principal arranca. El runtime empaquetado
  NO trae java.exe suelto.
- **Batalla aleatoria**: los rivales salen de `RivalDimPool` = DIM cards
  NORMALES de la carpeta de Ajustes (`batalla.carpetaRivales`), porque la
  VS DIM solo trae los sprites de su propio Digimon. Sin carpeta, el
  Digimon lo avisa en su burbuja.
- **VS DIM en el programa** (`DimVPetData.fromVsDim`, DimCard sintético,
  forma fija). Pendiente: identificar el campo [2].
- **Arnés temporal restante**: `AttackSpriteResolver.runDiagnostics()` en
  `Main.start` (marcado TEMPORAL).
- **Persistencia**: guardar/cargar `DigimonInstance` en disco — nunca
  implementado.
- **Splash art de Big Attack** en las pantallas del coliseo: arquitectura
  prevista (mismos overlays que el HUD) pero sin implementar.
