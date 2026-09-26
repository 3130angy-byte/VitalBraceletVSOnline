# Vital Bracelet VS Online

Tu Digimon del **Vital Bracelet** vive en el escritorio de tu PC, pelea y
entra a una sala en línea para enfrentarse a los Digimon de otros jugadores.
Cuando terminas, lo **retiras** y el programa te entrega una VS DIM nueva con
el resultado de sus batallas para devolverlo al Vital Bracelet.

> Proyecto de fans, sin relación con Bandai. **Versión de prueba 0.0.3.1.**

## Descargar y jugar (Windows)

1. Descarga `DigimonVS-0.0.3.1.zip` desde **[Releases](../../releases)**.
   Trae Java incluido: no hay que instalar nada más.
2. Descomprime y abre `DigimonVS-0.0.3.1\DigimonVS-0.0.3.1.exe`.
   Si Windows avisa *"Windows protegió su PC"*: **Más información → Ejecutar de todas formas**
   (el programa no tiene firma digital).
3. Escribe tu nombre de usuario y el servidor que te dio el anfitrión.
4. **Cargar mi Digimon (VS DIM)**: elige el `.bin` de tu Digimon, sacado del
   Vital Bracelet con tu lector de tarjetas. **Guarda antes una copia de ese archivo.**

Las instrucciones completas están en [`tester/LEEME.txt`](tester/LEEME.txt).

## Qué se puede hacer

- **Digimon en el escritorio**: llega por un portal, pasea, se mueve con clic.
  Clic derecho = menú.
- **Batalla aleatoria**: contra Digimon de DIM cards normales.
- **VS Online**: sala con tu avatar y tu Digimon siguiéndote. En el NPC
  *Batalla oficial* te pones disponible y retas a otros jugadores
  (15 s para aceptar). El servidor calcula la pelea una sola vez y ambos ven
  el mismo resultado.
  - **Batalla Libre**: stats + bono por trofeos (cada 10 = +50% DP, +25% HP, +1 AP; tope 120).
  - **Batalla Original**: solo los stats de la DIM.
- **Rango** C/B/A/S según los trofeos (10 / 30 / 70 / 120).
- **Retirar**: cada batalla suma o resta Vital Values según la etapa del rival
  (Ultimate 500, Perfect 200, Adult 80, Child 30). Al retirarlo se crea una VS
  DIM nueva con el saldo; la original no se toca.

⚠️ **En prueba**: devolver la VS DIM generada al Vital Bracelet todavía no
está probado en muchos aparatos. Hazlo con una copia de seguridad y, si
puedes, primero con un Digimon que no te importe perder.

## Ser anfitrión (servidor)

El servidor corre en la PC de uno de los jugadores; los demás se conectan
por [Tailscale](https://tailscale.com/) (red privada, gratis):

1. Instala Tailscale e invita a tus amigos a tu red.
2. Abre `Servidor VS Online.exe` (viene en el mismo .zip) y déjalo abierto.
   Comandos: `jugadores`, `expulsar <nombre>`, `apagar`, `ayuda`.
3. Permite el acceso cuando el firewall de Windows lo pregunte (puerto 7777).
4. Pasa tu IP de Tailscale (`100.x.x.x`) a los jugadores.

Ajustes del servidor (rangos, límites anti-abuso) y recompensas: archivos
`.properties` en `DigimonProjectData\config` (en `D:\` si existe, si no en tu
carpeta de usuario).

## Compilar desde el código

Requiere JDK 17 (Gradle lo descarga con el wrapper).

```bash
.\gradlew.bat runTester       # edición 0.0.3.1 (sin asistente ni chat IA)
.\gradlew.bat runServer       # servidor VS Online con consola
.\gradlew.bat packageTester   # arma el .zip con Java incluido (jpackage)
```

`packageTester` usa como servidor por defecto la propiedad
`servidorProbadores` (en `~/.gradle/gradle.properties` o `-PservidorProbadores=100.x.x.x`).

El mismo código incluye la edición **0.0.3** (`.\gradlew.bat run`): un
asistente virtual con chat de IA local vía [Ollama](https://ollama.com/)
(modelo `ministral-3:3b`). La versión 0.0.3.1 lo desactiva con
`-Ddigimon.edicion=tester`.

## Créditos

- [VB-DIM-Reader](https://github.com/cfogrady/VB-DIM-Reader) de cfogrady, para leer las DIM cards.
- Formato de las DIM y del combate contrastado con DIM-Modifier y VitalWear.
- Bono de trofeos basado en el mod *Digimon Link Pendulum*.

## Aviso legal

Proyecto de fans sin fines de lucro, no afiliado ni respaldado por Bandai.
*Digimon* y *Vital Bracelet* son marcas de Bandai. Los sprites de ataque
provienen del firmware del Vital Bracelet y pertenecen a sus dueños. Los
sprites de cada Digimon se leen de los archivos del propio usuario; el
programa no los incluye.
