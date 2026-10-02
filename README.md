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
3. Escribe tu nombre de usuario y, en *Servidor*, la IP que te pase el anfitrión
   (viene escrito `100.x` como ejemplo). Si no tienes una, deja cualquier cosa:
   el juego arranca igual con todas las funciones locales.
4. **Cargar mi Digimon (VS DIM)**: elige el `.bin` de tu Digimon, sacado del
   Vital Bracelet con tu lector de tarjetas. **Guarda antes una copia de ese archivo.**
   La especie se lee sola del sprite del nombre (sin IA); puedes corregirla.

## Qué se puede hacer

- **Digimon en el escritorio**: llega por un portal, pasea, se mueve con clic.
  Clic derecho = menú.
- **Ficha del Digimon**: stats, atributo, rango C/B/A/S según sus trofeos
  (10 / 30 / 70 / 120) y el bono que le suman.
- **Batalla aleatoria**: la VS DIM solo trae a tu Digimon, así que los rivales
  salen de **DIM cards normales** (`.bin`) que tengas en tu PC. Agrégalas en
  *Laboratorio → RIVALES* (una carpeta o archivos sueltos). Tu Digimon debe ser
  Child o superior.
- **ARENA 2 vs 2** (inspirada en la app Vital Bracelet Arena, con reglas y arte
  propios): tus dos Digimon (puesto 1 y 2) contra la máquina o contra otro
  jugador en línea. *ATTACK* = toca los números en orden durante 10 s (el combo
  sube tu ataque; con 10+ sale el BIG ATTACK); al ser atacado eliges *DEFENSE*
  (detén la barra en el escudo) o *PROTECT* (tu compañero recibe el golpe);
  *W-ATTACK* con el medidor lleno; *GUTS* a veces te salva con 1 HP. Los stats
  se convierten como la app (DP×120 = BP, HP×400, AP×150). No cuenta para el
  récord del VB.
- **Laboratorio**: tus Digimon guardados como cápsulas (copias de tus VS DIM),
  Digidex de especies vistas, historial de batallas y rivales. Puedes tener 2
  Digimon en el escritorio y cambiarlos sin retirarlos.
- **VS Online**: sala con tu avatar y tu Digimon siguiéndote. En el NPC
  *Batalla oficial* te pones disponible y retas a otros jugadores
  (15 s para aceptar). El servidor calcula la pelea una sola vez y ambos ven
  el mismo resultado.
  - **Batalla Libre**: stats + bono por trofeos (cada 10 = +50% DP, +25% HP, +1 AP; tope 120).
  - **Batalla Original**: solo los stats de la DIM.
- **Retirar**: al retirar al Digimon se crea una VS DIM con el **reporte de
  batalla** (el mismo formato que escribe un VB al pelear contra una tarjeta):
  al devolverla, el Vital Bracelet entrega él mismo la recompensa o el castigo
  (probado en un VB real). La VS DIM original no se toca.

## Servidor del VS Online

El descargable **no trae ningún servidor puesto**. Los servidores tienen una
**lista de acceso**: solo entran los nombres que el anfitrión agregó (máx. 20).
Si quieres probar la sala del autor, **pide permiso** indicando el nombre de
usuario con el que entrarás; te pasará la IP para escribirla al iniciar. Si tu
nombre no está en la lista, la sala te avisa y sigues con las funciones locales.

Para montar tu propio servidor desde el código: `.\gradlew.bat runServer`
(consola con `jugadores`, `expulsar <nombre>`, `apagar`, `ayuda`; puerto 7777).
Los jugadores se conectan con la IP de tu PC, por ejemplo a través de
[Tailscale](https://tailscale.com/). La lista de acceso es el archivo
`DigimonProjectDatanfigista-acceso.txt` (una persona por línea;
`anfitrion=TuNombre` para ti); el servidor la relee sola y saca de la sala a
quien retires. Una conexión que no se identifica en 10 s se corta y una IP con
muchos nombres rechazados queda bloqueada 10 minutos. Ajustes del servidor y recompensas:
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

El mismo código incluye la edición con **asistente** (`.\gradlew.bat run`): un
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
