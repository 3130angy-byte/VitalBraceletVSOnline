package org.example.chat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Toda ruta de datos generados por el programa vive bajo D:, nunca C: (en
 * la laptop del usuario C: casi no tiene espacio). En otra PC sin disco D:
 * (probadores de 0.0.3.1) se usa la carpeta del usuario de Windows.
 */
public final class AppPaths {

    private static final Path BASE = Files.isDirectory(Paths.get("D:\\"))
            ? Paths.get("D:", "DigimonProjectData")
            : Paths.get(System.getProperty("user.home"), "DigimonProjectData");

    private AppPaths() {}

    public static Path base() { return ensure(BASE); }
    public static Path chats() { return ensure(BASE.resolve("chats")); }
    public static Path summaries() { return ensure(BASE.resolve("summaries")); }
    public static Path instances() { return ensure(BASE.resolve("instances")); }
    public static Path config() { return ensure(BASE.resolve("config")); }
    /** VS DIM de vuelta al Vital Bracelet (al retirar un Digimon). */
    public static Path returns() { return ensure(BASE.resolve("devoluciones")); }

    private static Path ensure(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            System.out.println("No se pudo crear " + path + ": " + e.getMessage());
        }
        return path;
    }
}