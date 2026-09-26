package org.example.chat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lee una base de personalidad editable por el usuario, fuera del código
 * Java -- para poder ajustar tono/reglas/conocimiento sin recompilar. Se
 * lee de nuevo en cada llamada (barato: al aparecer el Digimon y cada vez
 * que se abre el chat) para que un cambio en el archivo aplique sin
 * reiniciar el programa.
 */
public final class PersonalityBaseLoader {

    private static final String DEFAULT_TEMPLATE = """
            # Base de personalidad -- edita este archivo libremente.
            # Todo lo que escribas aquí (fuera de comentarios) se agrega al
            # contexto del Digimon, además de las reglas fijas del programa.
            # Las líneas que empiezan con # son comentarios y se ignoran.
            #
            # Ejemplos de lo que puedes agregar:
            # - Temas que debe evitar o tratar con cuidado.
            # - Cosas que "sabe" sobre el mundo Digimon.
            # - Matices de tono (más formal, más juguetón, etc.).
            """;

    private PersonalityBaseLoader() {}

    public static String load() {
        Path file = AppPaths.config().resolve("personality_base.txt");

        if (!Files.exists(file)) {
            try {
                Files.writeString(file, DEFAULT_TEMPLATE, StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.out.println("No se pudo crear personality_base.txt: " + e.getMessage());
                return "";
            }
        }

        try {
            StringBuilder sb = new StringBuilder();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                sb.append(line).append("\n");
            }
            return sb.toString().trim();
        } catch (IOException e) {
            System.out.println("No se pudo leer personality_base.txt: " + e.getMessage());
            return "";
        }
    }
}