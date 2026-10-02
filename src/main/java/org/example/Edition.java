package org.example;

/**
 * Edición del programa, decidida al arrancar con -Ddigimon.edicion:
 *  - (sin valor) 0.0.3.z: la del ANFITRIÓN (pedido del usuario, 2026-10-02):
 *                todo — asistente virtual y chat con IA local, ARENA 2 vs 2
 *                local y online, lector de nombres sin IA — más la pestaña
 *                ACCESO del Laboratorio: la lista de quién puede entrar al
 *                servidor VS Online de esta PC.
 *  - "tester"    0.0.3.1: la versión para probadores, SIN asistente ni chat
 *                IA (decisión del usuario): Digimon en el escritorio,
 *                Batalla aleatoria, ARENA local y online, VS Online (si el
 *                anfitrión los tiene en su lista), Laboratorio y retirar al
 *                Digimon. No necesita Ollama.
 * Es el mismo código: los paquetes (packageAdmin / packageTester) solo cambian
 * -Ddigimon.edicion.
 */
public final class Edition {

    public static final boolean ASSISTANT = !"tester".equalsIgnoreCase(System.getProperty("digimon.edicion", ""));

    /** Administra la lista de acceso al servidor (pestaña ACCESO del Laboratorio). */
    public static final boolean ADMIN = ASSISTANT;

    public static final String VERSION = ASSISTANT ? "0.0.3.z" : "0.0.3.1";

    private Edition() {}
}
