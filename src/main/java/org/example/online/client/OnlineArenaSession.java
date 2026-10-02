package org.example.online.client;

import org.example.arena.ArenaConfig;
import org.example.arena.ArenaEngine;
import org.example.arena.ArenaFighter;
import org.example.arena.ArenaScreen;
import org.example.online.Protocol;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Una ARENA 2 vs 2 online vista desde ESTE jugador (siempre a la
 * izquierda). El servidor manda; aquí los mensajes "arena*" se procesan EN
 * ORDEN, uno a la vez: el siguiente espera a que termine la animación o el
 * minijuego del anterior (el servidor no espera a nadie, así que sus
 * tiempos límite son holgados).
 */
final class OnlineArenaSession {

    private final Supplier<VsClient> client;
    private final Map<Integer, LobbyDigimon> digimons;
    private final int myId;
    private final Consumer<String> notify;
    private final Consumer<OfficialBattleResult> onResult;
    private final Runnable onClosed;
    /** Antes de abrir la pantalla (p. ej. tu puesto 2 cruza su portal del escritorio); recibe "empezar". */
    private final Consumer<Runnable> beforeStart;
    private final Deque<JSONObject> queue = new ArrayDeque<>();
    private boolean busy = false;

    private ArenaScreen screen;
    private ArenaEngine mirror;
    /** En el servidor soy "a" o "b"; aquí siempre soy el lado PLAYER. */
    private String mySide;
    private int rivalStage = 2;
    private boolean ended = false;
    private boolean windowClosed = false;

    OnlineArenaSession(Supplier<VsClient> client, Map<Integer, LobbyDigimon> digimons, int myId,
                       Consumer<String> notify, Consumer<OfficialBattleResult> onResult,
                       Consumer<Runnable> beforeStart, Runnable onClosed) {
        this.beforeStart = beforeStart;
        this.client = client;
        this.digimons = digimons;
        this.myId = myId;
        this.notify = notify;
        this.onResult = onResult;
        this.onClosed = onClosed;
    }

    void handle(JSONObject m) {
        queue.add(m);
        next();
    }

    private void next() {
        if (busy || queue.isEmpty()) return;
        busy = true;
        process(queue.poll(), () -> {
            busy = false;
            next();
        });
    }

    private void process(JSONObject m, Runnable done) {
        if (screen == null && !"arenaStart".equals(m.optString("t"))) { // no pudo empezar: se ignora el resto
            done.run();
            return;
        }
        // Ventana cerrada a media pelea: el servidor la termina igual; solo se anota el final.
        if (windowClosed && !"arenaEnd".equals(m.optString("t"))) {
            done.run();
            return;
        }
        switch (m.optString("t")) {
            case "arenaStart" -> start(m, done);
            case "arenaTurn" -> {
                apply(m);
                if (mine(m.optString("side"))) {
                    screen.promptAction(m.optBoolean("canSwitch"), m.optBoolean("wReady"), action -> {
                        client.get().arenaAction(action);
                        done.run();
                    });
                } else {
                    screen.sayText("Turno del rival...");
                    done.run();
                }
            }
            case "arenaAttack" -> screen.playAttack(m.optDouble("seconds", mirror.config().comboSeconds), combo -> {
                client.get().arenaCombo(combo);
                done.run();
            });
            // Primero se elige DEFENSE (minijuego) o PROTECT (recibe el golpe el compañero), como la app.
            case "arenaDefend" -> screen.playDefense(m.optBoolean("w")
                            ? "¡El rival lanza un W-ATTACK! ¿DEFENSE o PROTECT?"
                            : "¡El rival ataca! (COMBO " + m.optInt("combo") + ") ¿DEFENSE o PROTECT?",
                    m.optBoolean("canProtect", false),
                    (protect, distance) -> {
                        client.get().arenaDefense(distance, protect);
                        done.run();
                    });
            case "arenaWait" -> {
                screen.sayText(m.optString("text"));
                done.run();
            }
            case "arenaSwitch" -> {
                apply(m);
                screen.playSwitch(side(m.optString("side")), done);
            }
            case "arenaHit" -> {
                apply(m); // el estado ya trae el golpe; la animación usa los datos del Hit
                ArenaEngine.Hit hit = new ArenaEngine.Hit(side(m.optString("attacker")), m.optBoolean("w"),
                        m.optInt("combo"), m.optBoolean("connected"), m.optInt("damage"),
                        m.optBoolean("fainted"), m.optBoolean("replaced"),
                        m.optBoolean("guts"), m.optBoolean("big"), m.optInt("apBonus"), m.optBoolean("protect"));
                screen.playHit(hit, done);
            }
            case "arenaEnd" -> {
                apply(m);
                boolean won = m.optInt("winner") == myId;
                ended = true;
                screen.showEnd(won, m.has("reason") ? m.optString("reason") + (won ? " ¡Ganas tú!" : "") : null);
                // Se avisa YA (como en las Batallas Oficiales): cerrar la ventana no lo borra.
                if (onResult != null) {
                    onResult.accept(new OfficialBattleResult(won ? "WIN" : "LOSS", rivalStage, Protocol.MODE_ARENA));
                }
                done.run();
            }
            default -> done.run();
        }
    }

    private void start(JSONObject m, Runnable done) {
        JSONObject a = m.getJSONObject("a"), b = m.getJSONObject("b");
        mySide = a.getInt("id") == myId ? "a" : "b";
        JSONObject meInfo = mySide.equals("a") ? a : b, rivalInfo = mySide.equals("a") ? b : a;
        LobbyDigimon mine = digimons.get(myId), rival = digimons.get(rivalInfo.getInt("id"));
        if (mine == null || rival == null || mine.partner == null || rival.partner == null) {
            notify.accept("* No se pudo mostrar la ARENA: faltan los datos de algún Digimon.");
            client.get().battleDone();
            done.run();
            return;
        }
        rivalStage = rival.stage;
        // Pedido del usuario: tu puesto 2 cruza su portal del escritorio ANTES de que empiece la pelea.
        // Mientras tanto, los mensajes siguientes esperan en la cola (busy sigue en true).
        Runnable open = () -> openScreen(m, meInfo, rivalInfo, mine, rival, done);
        if (beforeStart != null) beforeStart.accept(open);
        else open.run();
    }

    private void openScreen(JSONObject m, JSONObject meInfo, JSONObject rivalInfo, LobbyDigimon mine, LobbyDigimon rival,
                            Runnable done) {
        ArenaFighter[] myTeam = team(mine, meInfo.getJSONArray("team"), "Tu ");
        ArenaFighter[] rivalTeam = team(rival, rivalInfo.getJSONArray("team"), "Rival ");
        mirror = new ArenaEngine(ArenaConfig.load(), new Random(), myTeam, rivalTeam);
        screen = new ArenaScreen(mirror);
        screen.openOnline("ARENA 2 vs 2 online", won -> {
            windowClosed = true;
            client.get().battleDone();
            if (!ended) notify.accept("* Cerraste la ARENA a media pelea: el servidor la sigue hasta el final.");
            if (onClosed != null) onClosed.run();
            // Si se cerró esperando una elección o un minijuego, se destraba la cola.
            busy = false;
            next();
        });
        apply(m);
        screen.refresh();
        screen.sayText("¡Comienza la ARENA 2 vs 2 online!");
        done.run();
    }

    private static ArenaFighter[] team(LobbyDigimon d, JSONArray info, String prefix) {
        return new ArenaFighter[]{fighter(d, info.getJSONObject(0), prefix + d.species),
                fighter(d.partner, info.getJSONObject(1), prefix + d.partner.species)};
    }

    private static ArenaFighter fighter(LobbyDigimon d, JSONObject info, String name) {
        return ArenaFighter.forDisplay(name, info.getInt("maxHp"), info.getInt("attribute"), info.getInt("stage"),
                info.optInt("small", -1), info.optInt("big", -1),
                d.fullFrames[Protocol.FRAME_IDLE_1], d.fullFrames[Protocol.FRAME_IDLE_2],
                d.fullFrames[Protocol.FRAME_ATTACK], d.fullFrames[Protocol.FRAME_DODGE], d.nameImage);
    }

    private void apply(JSONObject m) {
        if (mirror != null && m.has("state")) mirror.applyState(m.getJSONObject("state"), "b".equals(mySide));
    }

    private boolean mine(String side) {
        return side.equals(mySide);
    }

    /** Lado del servidor ("a"/"b") → lado en esta pantalla (PLAYER = yo). */
    private int side(String serverSide) {
        return mine(serverSide) ? ArenaEngine.PLAYER : ArenaEngine.CPU;
    }

    boolean isOver() {
        return ended;
    }
}
