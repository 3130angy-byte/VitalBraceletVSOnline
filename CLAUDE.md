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
  de un archivo cuyo checksum no cumple esa fórmula.
  **Reporte de batalla (descubierto 2026-09-28)**: según el PDF del mod
  (secc. 27 "VS DIM" y "Global Connect"), el resultado NO lo escribe tu VB
  ni tu programa: el Digimon Link del RIVAL lee tu VS DIM, pelea y escribe el
  resultado en la tarjeta; tu VB lo lee al reinsertarla y aplica él mismo
  recompensa/castigo. Par real antes/después (01/09, 12 min de diferencia,
  mismos flags `a234a404`): "VS DIM Monster Island - Hedorah.bin" →
  "Resultado del VS.bin". Cambiaron SOLO 5 palabras: [0] 0→2, [4] 0→3,
  [5] 0→1, [6] 100→0, y el checksum 53127→4800. **Los Vital Values NO
  cambian** (4684 en ambos). El checksum del reporte es OTRA fórmula: suma
  de 16 bits SOLO de las palabras del bloque de estado 0x40000
  (2+4684+100+10+3+1 = 4800), no del rango completo — por eso antes parecía
  "inválido". Origen confirmado por el usuario: un amigo le pasó la VS DIM
  de Hedorah, el usuario la metió en SU VB (flags `ba993504` = su aparato;
  los flags `a234a404` son del aparato del amigo), pelearon, **GANÓ HEDORAH**
  (el Digimon de la tarjeta) y perdió el del usuario; el backup posterior es
  "Resultado del VS". O sea: el VB que pelea CONTRA la tarjeta escribe el
  reporte. **Formato DESCIFRADO (2026-09-28)** con 3 reportes reales contra
  el mismo Hedorah (Perfect, etapa 4): "Resultado del VS" (sept.), "...VS 2
  - Ganó Hedorah" y "...VS 3 - Ganó MagnaKidmon" (MagnaKidmon = Ultimate,
  etapa 5; ambas peleas de 2 rondas):
    [0] = 2 siempre → "hay reporte de batalla" (0 = VS DIM sin pelear)
    [4] = dato del Digimon que peleó contra la tarjeta, SIN CONFIRMAR cuál.
          5 con MagnaKidmon (etapa 5 Y secondPoolBattleChance 5: ambos
          encajan); 3 en sept. contra otro Digimon que el usuario cree
          Perfect (etapa 4) de una DIM vieja que luego corrigió (quizá
          figuraba como 3). Prueba que lo decide: Hedorah contra Dynasmon
          (etapa 5, secondPool 8). NO son rondas (2 rondas en ambas peleas).
    [5] = 1 si GANÓ el Digimon de la tarjeta, 0 si perdió (confirmado)
    [6] = se pone en 0
    checksum 0x120000 = suma de 16 bits SOLO del bloque de estado
          (0x40000..0x40FFF, ya sin el NOT), guardada con NOT (3 de 3).
  VV, [2], [3] y todo lo demás quedan iguales. El VB del DUEÑO lee el
  reporte al reinsertar la tarjeta y calcula él la recompensa/castigo.
  Observado del lado del que pelea (MagnaKidmon contra Hedorah Perfect):
  ±400 VV por el resultado + 500 VV de bono por pelear (el usuario lo leyó en
  pantalla; MagnaKidmon ya estaba en 9999, tope). Récord del VB: 024/024 →
  025/026. Empate: formato desconocido. Consecuencia: escribir VV nuevos
  (lo que hace hoy RETIRAR) no sirve; hay que escribir un reporte así.
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
cierra el programa sin retirar, el saldo se pierde. **CONFIRMADO 2026-09-28
(3ª prueba, VS DIM recién extraída 9999 → devolución 9499): el VB IGNORA los
VV escritos en la tarjeta.** El resultado debe viajar como "reporte de
batalla" (ver sección 2, VS DIM). **RETIRAR ya escribe el reporte**
(decisión del usuario: opción 1, UN reporte con el SALDO TOTAL):
`DigimonProgress.battleReport()` → saldo > 0 = victoria contra el rival de
mayor etapa que venció; < 0 = derrota contra el de mayor etapa que lo
venció; sin batallas o saldo 0 = `copyUnchanged` (sin reporte). Empates no
se reportan. `VsDimWriter.writeBattleReport` reproduce BYTE A BYTE los
reportes reales "VS 2" y "VS 3" desde el Hedorah original, y se niega a
partir de un archivo que ya tenga reporte ([0]≠0). [4] = etapa del rival es
SUPUESTO (ver sección 2). Nombre: "VS DIM <especie> <fecha> (victoria vs
Perfect).bin". Los VV del programa (`VitalRewards`) quedan solo como
ESTIMACIÓN (y la tabla no coincide con el mod: contra Perfect el VB dio
±400 + 500 de bono). **PROBADO EN EL VB REAL (2026-09-28, usuario):
FUNCIONA** — al reinsertar la VS DIM retirada, el VB entrega el resultado
de la batalla. Pruebas anteriores, NO CONCLUYENTES
(2026-09-26 y 2026-09-28 19:08): el VB aceptó las devoluciones
(checksum válido) y el Digimon volvió sin cambios, PERO ambas se hicieron
desde una VS DIM VIEJA: "VS DIM MagnaKidmon.bin" (25/09: 4997 VV, 35
trofeos), mientras el Digimon real ya tenía 9999 VV y 37 trofeos (lo
muestran los `dimcard-backup-20260926180918.bin` y
`...\devoluciones\dimcard-backup-20260928191049.bin` que deja el programa de
tarjetas al escribir). La devolución llevaba 4497 (4997 - 500). No se sabe
si el VB ignora siempre los VV de la tarjeta o solo ignoró una foto que no
coincidía con su Digimon. **Prueba pendiente**: extraer una VS DIM RECIÉN
sacada (9999), perder una batalla, retirar (9499) y devolverla. Dato nuevo
sin explicar: el backup del 28/09 tiene [2]=0 (en todas las demás VS DIM
era 100). Pista aparte: "Resultado del VS.bin" (flags `a234a404`, [0]=2
[4]=3 [5]=1, checksum inválido) — averiguar de dónde salió.

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
**Portal sin carreras (2026-10-01, bug desde 0.0.2)**: si el Digimon iba
caminando (paseo o clic-para-mover) al pedir el portal, ese movimiento y el
del portal movían la MISMA ventana a la vez (se deslizaba a la esquina y
volvía). Ahora toda entrada/salida pasa por `DigimonInstance.enterPortal` /
`exitPortal`: cortan el movimiento (`stopMovement`, `clickToMove.cancel`),
bloquean el clic (`teleporting`) y, si venía moviéndose, `playEnter(...,
settleFirst=true)` lo deja en IDLE_1/IDLE_2 x2 y RECIÉN entonces lee su
posición para ubicar el portal. El portal se dibuja DETRÁS del Digimon
(pedido del usuario): ambas ventanas son "siempre encima" y, tras mostrar el
portal, `keepPetInFront()` trae al Digimon al frente.

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

**ARENA 2 vs 2** (tercer modo de combate, paquete `org.example.arena`,
menú BATALLA > ARENA 2 VS 2, solo Child+): inspirado en la app Vital Bracelet
Arena (2022, 2 vs 2 tocando números; dada de baja el 30/09/2024). Su arte
es de Bandai y NO se usa (figuras propias que imitan disposición y colores);
sus fórmulas nunca se publicaron, así que las reglas son NUESTRAS y
configurables (`ArenaConfig`, `...nfigrena.properties`; un archivo viejo
recibe al final las claves nuevas, las viejas quedan sin uso). Decisiones del
usuario: equipo = puestos 1 y 2 (si no hay compañero Child+, uno PRESTADO de
una cápsula o de la carpeta de rivales); contra la máquina (2 DIM cards);
cambio y W-ATTACK; NO cuenta para el récord ni el reporte. **Stats convertidos
como la app (2026-10-02, dato DEFINITIVO del usuario, fijo en código)**:
DP x 120 = BP, HP x 400 = HP, AP x 150 = AP (`ArenaFighter.BP_PER_DP`,
`HP_PER_HP`, `AP_PER_AP`; HP 8 = 3200, como los ~3080 de los videos). Reemplaza
al viejo "HP x10" (`vida.multiplicador` ya no se usa). El BP entra en la misma
fórmula del VB por proporción, así que BP o DP dan igual.
**Reglas v2 (2026-10-01, a partir de 2 videos de la app que pasó el usuario +
sus decisiones)**: ATAQUE = COMBO POR TIEMPO (`combo.segundos`=10): siempre
hay 5 números a la vista, se tocan en orden y el acertado lo reemplaza el
siguiente (1-5, luego 6...); se mueven y rebotan; uno fuera de orden cuesta
`combo.penalizacionError` s. El combo da BONO DE AP = 40 % x (1 - e^(-combo/5.65))
(13 ≈ +36 %, 18 ≈ +38 %, como en el video). Contador de 10 círculos: LLENO
(combo ≥ `combo.paraBig`=10) = BIG ATTACK (decisión del usuario). NO HAY
FALLOS (decisión del usuario): el DP funciona como "BP" con la fórmula del VB
(`BattleEngine.hitRate`, DP + atributo): x(1 + peso·(acierto-50)/100), 50 % =
x1. Daño = AP (x150) x `dano.factor`(1.33) x (1+bono) x BP x defensa, contra HP
(x400); 1.33 = el mismo balance de antes de convertir (`dano.base` quedó
comentado en el archivo del usuario). **Al ser atacado se ELIGE (2026-10-02,
como la app)**: DEFENSE = minijuego de la barra (escudo x0.4, amarillo x0.7) o
PROTECT = el COMPAÑERO aparece un momento delante, RECIBE el golpe (sin
minijuego, con su propio BP) y vuelve atrás; el activo sigue siendo el mismo
(si el compañero cae protegiendo, no hay cambio). Sin compañero en pie, PROTECT
apagado. La app además daba +BP con habilidades del BE (aún no). La máquina usa
PROTECT a veces (`cpuProtects`: activo < 35 % y compañero mucho mejor, 50 %).
Pantalla: botones DEFENSE (celeste, escudo) y PROTECT (verde) + tarjeta del
compañero; la placa del defensor muestra un momento al compañero
(`forcedPlate`) con la vida de ANTES del golpe (`setPlateHp`) y cae al impacto.
W-ATTACK = (AP de los dos) x2 x factor, atacan los dos. **Entrada por portal del
compañero (2026-10-02, pedido del usuario)**: ARENA LOCAL = el puesto 1 entra al
portal con `keepPortalOpen` y el puesto 2 del escritorio camina detrás
(`startFollowing`) y cruza el MISMO portal (`TeleportAnimator.playFollowIntoOpenPortal`);
al terminar salen los dos por el mismo portal (`playExit` con el portal abierto +
`leaveArenaThrough` → `playExitThroughOpenPortal`, que lo cierra). ARENA ONLINE =
el puesto 1 ya está en la sala; al llegar `arenaStart`, el puesto 2 del escritorio
cruza SU portal (`goToArena`) y recién entonces se abre la pelea (los mensajes
esperan en la cola de `OnlineArenaSession`); al cerrar la ventana solo él vuelve
por su portal a donde estaba (`returnFromArena`). Ganchos:
`LobbyWindow.setArenaHooks` → `OfficialBattleUi` → `OnlineArenaSession`
(beforeStart / onClosed). Sin probar en pantalla. GUTS (`guts.probabilidad`=0.10, decisión del usuario): un golpe de KO
a veces deja 1 HP. Máquina: combo 6-16. Balance (2000 peleas simuladas, 4
juegos de stats, atributos iguales): el jugador gana 57-66 % (ataca primero),
5-10 turnos suyos por pelea, cada golpe quita 20-40 % de la vida, W-ATTACK
0.5-0.9 por pelea, GUTS ~0.3. `ArenaEngine.Hit` trae `guts`, `big` y `apBonus`;
`pass(side)` = turno perdido sin golpe (ARENA online, sin elegir a tiempo).
**Pantalla estilo VB Arena (por defecto; `pantalla.estilo=coliseo` vuelve al
diseño del coliseo)**: sin fondo de ciudad (degradado oscuro, estelas y una
línea de neón donde se para cada Digimon), ventana casi cuadrada, rival arriba
a la derecha y tú abajo a la izquierda, sprites a aumento ENTERO. Placas
blancas inclinadas con "cola" de globo, insignia de atributo (Va verde, Da
naranja, Vi morada, Fr turquesa), sprite NAME oscurecido (`darkName`) y barra
que CAE AL INSTANTE con un tramo rojo de "daño reciente" que se encoge. Reloj
circular (combo y defensa). Botones inclinados ATTACK (amarillo), W-ATTACK
(naranja, medidor de 5 segmentos), CHANGE (verde) + tarjeta del compañero.
Minijuegos en el panel de abajo: números con estrella, contador de 10
círculos y "COMBO n"; READY → START!; al final EXCELLENT!!/GREAT!/GOOD/BAD +
"AP +x%". Defensa: tramos en chevrón (verdes, amarillos), ESCUDO en el centro,
indicador y botón STOP (o ESPACIO / clic). Animaciones: impulso, el Digimon
EXPULSA su ataque (BIG con 10+ o W-ATTACK, si no SMALL; sprites del firmware,
girados en la diagonal), impacto con destello y temblor (de toda la pantalla
si es BIG), retroceso, número de daño amarillo-naranja que salta, escena
W-ATTACK (negro, líneas amarillas, rayo, los DOS juntos, luego disparan los
dos), pantalla GUTS!!! magenta, caída (la placa lo sigue mostrando:
`holdPanel`/`spriteIndex`), cambio deslizándose, WIN!!/LOSE... con saltos y
pose de VICTORY (local). La ARENA online manda "small"/"big" en `arenaStart`,
`guts`/`big`/`apBonus` en `arenaHit` y el tiempo del combo en `arenaAttack`
(protocolo v8); el servidor acepta combos hasta `combo.maximo` y espera
combo.segundos + 8 s. Protocolo v9 (2026-10-02): stats convertidos en el
servidor, `arenaDefend.canProtect`, `arenaDefense.protect` (cliente → servidor)
y `arenaHit.protect`. Balance con stats convertidos y PROTECT (2000 peleas x 4):
el jugador gana 58-69 %, 5-9 turnos, 21-39 % de vida por golpe. Probado fuera de pantalla con Kumamon/Louwemon vs
Duskmon (turno, números, defensa, impacto, BIG, W-ATTACK, GUTS). Visto pero
sin implementar: símbolos verdes sobre un Digimon en un video (¿estado
alterado?). Excluido: App Abilities (solo BE), objetos, stats entrenados del
BE, rangos y recompensas.

**LABORATORIO** (versión PC de la app Vital Bracelet Lab, paquete
`org.example.lab`, interfaz SIMPLE a propósito — decisión del usuario):
ventana aparte y ÚNICA (`LabWindow.open()`), los Digimon siguen en el
escritorio. Se abre desde la pantalla de inicio (botón LABORATORIO, sin
Digimon cargado) o menú V-PET > LAB. Pestañas: ALMACÉN (`LabStorage`,
cápsulas = COPIAS de VS DIM en `...\laboratorio\capsulas\<id>.bin` +
`.properties` con especie, edad, fecha, notas y origen; el usuario confirmó
que el VB conserva a su Digimon y que, al devolver una cápsula, el VB recibe
la versión de la cápsula → puede quedar vieja; rechaza archivos con reporte
de batalla (`VsDimWriter.hasBattleReport`); botones IMPORTAR VS DIM, AL
ESCRITORIO (`Main.spawnDigimon`, máx. 2, edad = la guardada + días
transcurridos), EXPORTAR PARA EL VB (copia en devoluciones), ELIMINAR (a
`papelera`, nunca borra), ABRIR CARPETA), DIGIDEX (`Digidex`: especies
vistas por hash SHA-1 del sprite NAME, PNG + `digidex.properties`
veces|especie|primera vez; registra tu Digimon al aparecer, cápsulas y
rivales de Aleatoria/ARENA; el online todavía no) e HISTORIAL
(`BattleHistory`, `historial.tsv` en disco: fecha, Digimon, modo, rival,
resultado, VV estimados; Aleatoria, Oficial Libre/Original y ARENA). Los
sprites NAME se muestran sobre una franja oscura (son letras claras con
fondo transparente). ARENA: si no hay otro Digimon Child+ en el escritorio,
el compañero sale de una CÁPSULA al azar antes que de las DIM cards.
Fuera por ahora: NFC (versión celular), misiones, objetos, raids, rankings.

**Equipo = PUESTOS del escritorio** (decisión del usuario, como Vital
Bracelet Arena): el orden de `DigimonRegistry` es el puesto; índice 0 =
PUESTO 1 (principal: te sigue en la sala y pelea las batallas tipo VB),
índice 1 = PUESTO 2 (secundario: solo cuenta en el 2 vs 2). TODO Digimon del
escritorio es una cápsula (`DigimonInstance.capsuleId`): cargar desde la
pantalla de inicio la guarda en el Laboratorio (`LabStorage.findSame` por
SHA-1 reusa la misma VS DIM). Cambiar quién está en el escritorio YA NO
exige retirar: `DesktopTeam` (lo implementa `Main`) + `DesktopTeam.askReplace`
pregunta "¿a cuál reemplaza?" y el reemplazado vuelve a su cápsula por el
portal (`DigimonInstance.dismiss`, sin archivo ni Explorador). Si estaba
FUERA (sala online, pelea) el equipo cambia al instante y su ventana se
cierra al volver (`away`/`pendingDismiss`/`comeBack`). Laboratorio: recuadro
"EN EL ESCRITORIO — Puesto 1 / Puesto 2", botón INTERCAMBIAR PUESTOS 1 ⇄ 2 y
"★ Puesto N" en la lista. El récord pendiente de cada Digimon se GUARDA en
su cápsula (`DigimonProgress.toProperties/restore`, claves `progreso.*`)
tras cada batalla y se vacía al RETIRAR: sobrevive a reemplazos y a cerrar
el programa. `registry.putAt` INSERTA (no pisa): al reemplazar el puesto 1,
el otro no se pierde (bug evitado). ARENA local: equipo = puestos 1 y 2
(Child+), no "quien recibió el clic + el otro".
**Cambios de equipo con la sala online abierta (2026-10-01, pedido del
usuario)**: a la sala entra SIEMPRE el puesto 1 (VS ONLINE desde el menú del
puesto 2 manda al puesto 1); `DigimonInstance.inLobby` marca quién está allá y
al cerrar la sala vuelve QUIEN ESTÉ en ese momento (`returnFromLobby`).
Reemplazar el puesto 1 desde la PC: el nuevo NO aparece en el escritorio
(`setSpawnIntoLobby`, ventana vacía hasta cerrar la sala); en la sala el viejo
camina a un portal y se desvanece, el portal QUEDA ABIERTO y por él sale el
nuevo (`LobbyWindow.beginPortalSwap` + `LobbyPortalSwap`). INTERCAMBIAR 1 ⇄ 2:
el de la sala entra a su portal y el del escritorio al suyo; cuando este
termina de entrar, el de la sala sale por ESE MISMO portal del escritorio,
que quedó abierto (`TeleportAnimator.keepPortalOpen` +
`playExitThroughOpenPortal`, `leaveLobbyThrough`; antes se cerraba y se abría
otro en el centro, corregido a pedido del usuario) y el otro sale por el portal de la sala (se reenvía el
equipo recién entonces). Los demás jugadores ven el mismo cambio por portal
cuando cambia el aspecto del Digimon (`LobbyDigimon.look`); si solo cambia el
compañero, el Digimon no salta (`takePlaceOf`). No se puede cambiar el equipo
durante un reto/pelea ni con un cambio por portal en curso
(`LobbyWindow.teamChangeBlocker`); si el servidor rechaza el equipo con el
portal abierto, se reintenta una vez a los 5,5 s y si no, vuelve a salir el
mismo. Probado fuera de pantalla (sala): MagnaKidmon entra, portal abierto,
sale Dynasmon y sigue al avatar. Sin probar en pantalla: la parte del escritorio.

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

- **Lector de nombres SIN IA HECHO (2026-10-02, pedido del usuario; reemplaza la
  decisión de abajo)**: `org.example.dim.NameSpriteReader`. El usuario dio 3
  fuentes del VB (hojas con las letras separadas por columnas MAGENTA de ancho
  variable): DigiScript (68 celdas: A-Z, a-z, espacio - = _ ( ) 1-0; ÚNICA con
  minúsculas), Official Bandai (42: A-Z, espacio - = _ ( ) 1-0; = la de DIMNameGen)
  y Agero (42: con [ ] en vez de ( )). Las otras dos se transcriben en mayúsculas.
  Hojas en `resources/fonts/VB_Alphabet_ENG_*.png`; plantillas de texto en
  `resources/fonts/vb_name_fonts.txt` (las genera `tools/GenerarPlantillasNombre.java`;
  el lector no decodifica imágenes). Cómo lee: tinta = píxel claro (el verde
  0x07E0 es transparente); por fuente y desplazamiento vertical (-3..+3), una
  programación dinámica cubre las columnas con letras (costo = píxeles distintos;
  saltar tinta x2), hueco de 3+ columnas = espacio; gana la fuente con menos
  error, tope 12 %. Probado con TODAS las DIM/VS DIM de la PC: 76 nombres latinos
  leídos (casi todos con 0 % de error, 15-60 ms): "MagnaKidmon", "DYNASMON",
  "LORD KNIGHTMON", "LUCEMON- FALLDOWN MODE", "SIRIUSMON" (Official Bandai),
  "Hedorah"... Los 48 sin leer son TODOS katakana (Agumon, Angoramon, Impulse
  City). Las DIM reales usan sobre todo DigiScript (por eso DIMNameGen no
  coincidía). Uso: `DigimonInstance` lo prueba PRIMERO y solo si falla usa la
  visión (SpeciesNameReader); al importar (pantalla de inicio y Laboratorio) la
  especie viene PRELLENADA con lo leído y se puede corregir (0.0.3.1 incluida).
  Se guarda tal cual se lee (sin pasar a "Dynasmon").
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
  **Avatar nuevo (2026-10-01, referencias del usuario)**: de PERFIL con
  proporciones REALISTAS (no chibi: cabeza chica, cuerpo alto), cabello negro
  despeinado con mechones cortos, ojo pequeño, nariz, oreja; chaqueta azul
  ABIERTA sobre polera blanca, pantalón negro, zapatillas oscuras con suela
  blanca. SOLO izquierda/derecha (decisión del usuario, como los Digimon): mira
  a la izquierda y se espeja; al ir derecho arriba o abajo conserva hacia dónde
  miraba. Imagen 36x60 px = 18x30 en el mundo (1:1 con la cámara x2, como los
  Digimon). Se GENERA por capas (elipses, polígonos, segmentos gruesos) con
  contorno automático por capa y extremidades de atrás oscurecidas; caminata
  de 6 cuadros a partir de ángulos de cadera/rodilla/hombro (`LEG_CYCLE`), el
  cuerpo sube y baja solo (apoya el pie más bajo) y la cadera se redondea a
  píxel entero para que la cara no tiemble. Cabeza x1.15 (`HEAD_SCALE`) para
  que la cara se lea. Cuadro 0 quieto, 1-6 caminando (110 ms cada uno).
  Colores en `Palette` (pelo, chaqueta, polera, pantalón, zapatillas) para el
  editor futuro. Ya no hay vistas de frente ni de espaldas.
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
  **Cámara y movimiento de la sala (2026-10-01, pedido del usuario)**: ya
  no se ve toda la sala: lienzo 960×600 con CÁMARA x2 (`LobbyWindow.ZOOM`)
  centrada en tu avatar, que alcanza suavemente y no se sale del mapa;
  minimapa en la esquina superior derecha (jugadores como puntos y recuadro
  de la cámara); avisos de lugares en pantalla (arriba al centro). El mapa
  se pre-dibuja al doble de resolución (`LobbyMapRenderer.renderStatic(2)`)
  y los textos del mundo usan tamaño de fuente / 2 (el texto del lienzo es
  vectorial). El Digimon de la sala ya NO se reduce promediando 2x2: mide
  la mitad en el mundo y con la cámara x2 se ve con sus píxeles 1:1. **Efecto
  "látigo" corregido**: era un suavizado exponencial (25 %/cuadro el avatar,
  18 %/cuadro el Digimon) sobre fotos del servidor a 10 Hz → arrancaba y
  frenaba 10 veces por segundo. Ahora: INTERPOLACIÓN CON RETRASO (se dibuja
  120 ms en el pasado, lineal entre las dos fotos que rodean ese instante →
  velocidad constante = `Protocol.WALK_SPEED` 160, la misma que usa el
  servidor), margen de 150 ms antes de mostrar "quieto", paso del avatar
  cada 170 ms y del Digimon cada 220 ms. El Digimon sigue POR EL MISMO
  CAMINO del avatar (migas cada 3 u, como los compañeros de Pokémon), a 26 u
  de camino detrás, a velocidad constante (x1.35 si se queda atrás): no
  corta esquinas ni atraviesa muros. Medido fuera de pantalla (simulación
  de servidor a 10 Hz doblando una esquina): avatar 161 u/s de promedio,
  nunca 0 (antes había cuadros detenidos), Digimon 0 cuadros sobre muro.
  **Mapa v2 (2026-10-01, pedido del usuario; protocolo v7, mapa
  "torneo-1f-v2")**: `LobbyMapRenderer` ahora pinta PIXEL ART real (rectángulos
  enteros a 1 px por unidad del mundo, agrandado sin suavizado; solo las
  etiquetas son texto vectorial encima). Detalles de escena SIN funciones:
  alfombra roja de torneo, estandartes y apliques en los muros, marcador "VS
  ONLINE" (letras pixel 5x5 propias), vitrinas de trofeos, macetas, bancas,
  puertas de vidrio en la entrada, kiosco de la tabla y PC dibujados. Training
  Room ABIERTO, solo estético (piso de madera, colchoneta, espejo, sacos,
  pesas, mancuernas, muñecos, trotadoras, dispensador). Portal: solo
  decorativo, animado con la misma hoja del portal (`drawPortalAnimation`),
  sobre una plataforma; ya no dice BLOQUEADO ni tiene aviso. Los adornos son
  `LobbyMap.PROPS` (casillas 'D', no se pisan; el servidor las respeta).
  Comprobado: las 406 casillas pisables se alcanzan desde la entrada.
  **Protocolo v6 (equipo + PC + ARENA online)**: el mensaje "digimon" lleva
  el PUESTO 1 y, si hay, el PUESTO 2 como "partner" (mismo formato y misma
  validación; al resto solo se reparten sus cuadros). Se puede REENVIAR
  (cambio de equipo) salvo durante un reto o pelea (`teamRejected`), máx. 1
  cada 5 s; tope de línea 256 KB. La sala envía `DigimonInstance.teamPayload`
  (puestos del registro) y se reenvía con `LobbyWindow.notifyTeamChanged()`
  tras cada cambio en el Laboratorio. Las Batallas Oficiales se anotan al
  PUESTO 1 del momento (`recordOnlineBattle`). **PC** en la sala
  (`LobbyMap.PC`='K', muro norte, casillas 34-35 de la fila 12, frente a la
  terminal): al LLEGAR abre el Laboratorio. **ARENA 2 vs 2 online** = 3er
  modo del NPC (`Protocol.MODE_ARENA`="arena"): exige puesto 1 y 2 Child+ en
  ambos (`OnlineArenaMatch.teamProblem`). `OnlineArenaMatch` (servidor) corre
  `ArenaEngine` (mismas reglas que la local, SIN bono de trofeos, config del
  servidor) y manda arenaStart / arenaTurn / arenaAttack / arenaDefend /
  arenaWait / arenaSwitch / arenaHit / arenaEnd; el cliente responde
  arenaAction / arenaCombo / arenaDefense. Límites: acción 25 s, minijuego
  14 s (sin respuesta = golpe fallido o sin defensa); combo 0 = fallo sin
  pedir defensa; si uno se va, gana el otro. `OnlineArenaSession`
  (cliente) procesa los mensajes EN COLA, uno tras otro (espera cada
  animación), con `ArenaScreen` en modo online (`openOnline`, `promptAction`,
  `playAttack`, `playDefense`, `playHit`, `playSwitch`, `showEnd`) y un
  `ArenaEngine` espejo (`applyState`/`stateJson`; si eres "b", se invierten
  los lados para que siempre estés a la izquierda). Si cierras la ventana a
  media pelea, la sesión sigue escuchando y anota el final. NO cuenta para
  el récord (solo historial y comentario). OJO: combo y defensa los informa
  el cliente (el servidor solo los acota): sirve entre amigos, no es a
  prueba de trampas. Probado con 2 jugadores automáticos: reto sin compañero
  rechazado, reenvío de equipo, cambio de equipo en plena pelea rechazado y
  pelea completa (22 turnos). Sin probar: tiempos límite y desconexión a
  media pelea.
  Siguiente: cuentas (Fase 3). Usará BattleEngine + coliseo + HUD +
  PowerTrophyBonus en la Fase 4.
- **Protecciones del servidor y bug de nombres (2026-10-02)**: una conexión que
  no manda el "hello" en 10 s se corta (`ClientConnection.HELLO_TIMEOUT_SECONDS`;
  después rige la inactividad de 90 s); una IP con 5 nombres rechazados en 60 s
  queda bloqueada 10 min (`VsServer.noteRejected`); el servidor solo acepta JSON
  de texto y nunca guarda ni ejecuta lo que mandan los clientes. Probado: corte
  a los 10,0 s y bloqueo al 6.º intento. **Bug real de nombres corregido**: el
  nombre de jugador vivía solo en `assistant.properties`, que es UNO para todos
  los programas abiertos en la misma PC, y la sala lo leía del archivo al entrar
  → con dos programas abiertos se mezclaban los nombres (y entrar por el botón
  LABORATORIO no guardaba el nombre). Ahora `AssistantSettings` guarda en
  MEMORIA el nombre/servidor de la pantalla de inicio de ESE programa
  (`sessionName`/`sessionHost`); el archivo solo los precarga. Probado con 2
  programas a la vez. 0.0.3.1 viene con servidor "100.x" (decisión del usuario):
  cada probador escribe la IP; si no es válida, el juego arranca igual.
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
  Digimon lo avisa en su burbuja. Se revisan también SUBCARPETAS (hasta 3
  niveles) y se recuerdan los archivos que no sirven (VS DIM, BE Memory,
  ilegibles). En Ajustes, ELEGIR CARPETA de rivales GUARDA AL INSTANTE: antes
  el Popup del menú (autoHide) se cerraba al abrir el selector y la carpeta
  nunca llegaba a guardarse (bug real, visto el 2026-09-29). Al Popup del
  menú se le agrega la clase CSS "root" para quitar el aviso de JavaFX
  "Could not resolve '-fx-text-base-color'".
- **VS DIM en el programa** (`DimVPetData.fromVsDim`, DimCard sintético,
  forma fija). Pendiente: identificar el campo [2].
- **Arnés temporal restante**: `AttackSpriteResolver.runDiagnostics()` en
  `Main.start` (marcado TEMPORAL).
- **Persistencia**: guardar/cargar `DigimonInstance` en disco — nunca
  implementado.
- **Splash art de Big Attack** en las pantallas del coliseo: arquitectura
  prevista (mismos overlays que el HUD) pero sin implementar.
