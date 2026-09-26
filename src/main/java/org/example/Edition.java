package org.example;

/**
 * Edición del programa, decidida al arrancar con -Ddigimon.edicion:
 *  - (sin valor) 0.0.3: con el asistente virtual y el chat con IA local.
 *  - "tester"    0.0.3.1: la versión para probadores, SIN asistente ni chat
 *                IA (decisión del usuario): Digimon en el escritorio,
 *                Batalla aleatoria, VS Online y retirar al Digimon. No
 *                necesita Ollama.
 * Es el mismo código: el paquete de 0.0.3.1 (tarea jpackageTester) solo
 * arranca con -Ddigimon.edicion=tester.
 */
public final class Edition {

    public static final boolean ASSISTANT = !"tester".equalsIgnoreCase(System.getProperty("digimon.edicion", ""));

    public static final String VERSION = ASSISTANT ? "0.0.3" : "0.0.3.1";

    private Edition() {}
}
