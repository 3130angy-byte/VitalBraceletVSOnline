# Vital Bracelet VS Online

Tu Digimon del **Vital Bracelet** vive en el escritorio de tu PC, pelea y
entra a una sala en línea para enfrentarse a los Digimon de otros jugadores.

> Proyecto de fans sin fines de lucro, **no afiliado ni respaldado por Bandai**.
> **Versión de prueba 0.0.3.1.**

## Descargar y jugar (Windows)

1. Descarga el `.zip` desde **[Releases](../../releases)**.
   Trae Java incluido: no hay que instalar nada más.
2. Descomprime y abre `DigimonVS-0.0.3.1\DigimonVS-0.0.3.1.exe`.
   Si Windows avisa *"Windows protegió su PC"*: **Más información → Ejecutar de todas formas**
   (el programa no tiene firma digital).
3. Escribe tu nombre de usuario.
4. **Cargar mi Digimon (VS DIM)**: elige el `.bin` de tu Digimon, sacado del
   Vital Bracelet con tu lector de tarjetas. **Guarda antes una copia de ese archivo.**

## Qué se puede hacer

- **Digimon en el escritorio**: llega por un portal, pasea, se mueve con clic.
  Clic derecho = menú.
- **Ficha del Digimon**: stats, atributo, rango C/B/A/S según sus trofeos
  (10 / 30 / 70 / 120) y el bono que le suman.
- **Batalla aleatoria**: la VS DIM solo trae a tu Digimon, así que los rivales
  salen de **DIM cards normales** (`.bin`) que tengas en tu PC. La primera vez
  que elijas *Batalla → Aleatoria* el programa te pide la carpeta donde están.
  Tu Digimon debe ser Child o superior.
- **VS Online**: sala con tu avatar y tu Digimon siguiéndote. En el NPC
  *Batalla oficial* te pones disponible y retas a otros jugadores
  (15 s para aceptar). El servidor calcula la pelea una sola vez y ambos ven
  el mismo resultado.
  - **Batalla Libre**: stats + bono por trofeos (cada 10 = +50% DP, +25% HP, +1 AP; tope 120).
  - **Batalla Original**: solo los stats de la DIM.
- **Retirar**: el programa lleva la cuenta de Vital Values de cada batalla
  según la etapa del rival y, al retirar al Digimon, crea una VS DIM nueva
  para devolverlo al Vital Bracelet (la original no se toca).

⚠️ **Limitación conocida**: el Vital Bracelet acepta la VS DIM devuelta, pero
**todavía no toma el resultado**: el Digimon vuelve sin la recompensa ni el
castigo de sus batallas, como si no hubiera peleado. Se está investigando.

## Servidor del VS Online

El descargable público **no trae conexión a ningún servidor**: se está
trabajando en uno seguro y con más capacidad. Si quieres probar la sala en
línea, **pide permiso** al autor para recibir el archivo de prueba, indicando
el nombre de usuario con el que entrarás.

Para montar tu propio servidor desde el código: `.\gradlew.bat runServer`
(consola con `jugadores`, `expulsar <nombre>`, `apagar`, `ayuda`; puerto 7777).
Los jugadores se conectan con la IP de tu PC, por ejemplo a través de
[Tailscale](https://tailscale.com/). Ajustes del servidor y recompensas:
archivos `.properties` en `DigimonProjectData\config` (en `D:\` si existe; si
no, en tu carpeta de usuario).

## Para desarrolladores: leer y escribir VS DIM

Si trabajas con el Vital Bracelet, quizá te sirva esta pieza: la **VS DIM**
(el archivo que genera el VB al extraer un Digimon) **no es una DIM card
normal** y las librerías conocidas no pueden abrirla. En
[`src/main/java/org/example/dim/`](src/main/java/org/example/dim/) hay un
lector propio (`VsDimReader`, `VsDimData`) que obtiene los sprites del Digimon
transferido, su tabla de stats, el slot, los Vital Values y los trofeos, y un
escritor (`VsDimWriter`) que recalcula el checksum para generar VS DIM
válidas. El formato está documentado en los comentarios. **Úsalo libremente**
(licencia MIT, ver abajo).

## Compilar desde el código

Requiere JDK 17 (Gradle lo descarga con el wrapper).

```bash
.\gradlew.bat runTester       # edición 0.0.3.1 (sin asistente ni chat IA)
.\gradlew.bat runServer       # servidor VS Online con consola
.\gradlew.bat packageTester   # arma el paquete con Java incluido (jpackage)
```

`packageTester` usa como servidor por defecto la propiedad
`servidorProbadores` (en `~/.gradle/gradle.properties` o `-PservidorProbadores=100.x.x.x`).

El mismo código incluye la edición **0.0.3** (`.\gradlew.bat run`): un
asistente virtual con chat de IA local vía [Ollama](https://ollama.com/)
(modelo `ministral-3:3b`). La versión 0.0.3.1 lo desactiva con
`-Ddigimon.edicion=tester`.

## Licencia

El **código y el arte original** del proyecto están bajo licencia **MIT**
([`LICENSE`](LICENSE)): puedes usarlos, copiarlos, modificarlos y
redistribuirlos libremente, incluso en otros proyectos, siempre que conserves
el aviso de copyright.

La licencia **no cubre** material de terceros, que sigue perteneciendo a sus
dueños: las marcas *Digimon* y *Vital Bracelet* y los sprites de ataque del
firmware (© Bandai), y las librerías de otros autores. Detalle en
[`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md).

## Créditos

- [VB-DIM-Reader](https://github.com/cfogrady/VB-DIM-Reader) de Christopher Milne-O'Grady (MIT), para leer las DIM cards.
- Formato de las DIM y del combate contrastado con DIM-Modifier y VitalWear.
- Bono de trofeos basado en el mod *Digimon Link Pendulum*.

## Aviso legal

*Digimon* y *Vital Bracelet* son marcas registradas de Bandai; aquí se
mencionan solo para indicar con qué dispositivo funciona el programa. Este
proyecto no está afiliado, patrocinado ni respaldado por Bandai. Los sprites
de cada Digimon se leen de los archivos del propio usuario; el programa no
los incluye. Si Bandai pide retirar algún material, se retirará.
