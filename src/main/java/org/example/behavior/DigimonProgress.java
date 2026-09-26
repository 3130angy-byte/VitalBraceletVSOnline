package org.example.behavior;

import org.example.battle.VitalRewards;
import org.example.battle.VitalRewards.BattleOutcome;

/**
 * Datos de progreso del Digimon (sin crianza: nada de hambre, suciedad,
 * corazones ni evolución -- depuración de 0.0.3). Vital Values y Power
 * Trophies llegan de la VS DIM; el récord de batallas se acumula en el
 * programa (Batalla aleatoria y Batallas Oficiales del VS Online) y, al
 * RETIRAR al Digimon, el saldo de Vital Values viaja de vuelta al Vital
 * Bracelet en la VS DIM (VsDimWriter). Positivo = el Digimon vuelve
 * ganando; negativo = vuelve perdiendo.
 *
 * Ojo: no se guarda en disco (la persistencia nunca se implementó): si el
 * programa se cierra sin retirar al Digimon, el saldo se pierde.
 */
public class DigimonProgress {

    private final int importedVitalValues;
    private final int powerTrophies;
    private int battles = 0;
    private int wins = 0;
    private int losses = 0;
    private int draws = 0;
    /** Suma de lo ganado y perdido en todas las batallas (puede ser negativa). */
    private int vitalDelta = 0;

    public DigimonProgress(int vitalValues, int powerTrophies) {
        this.importedVitalValues = Math.max(0, vitalValues);
        this.powerTrophies = Math.max(0, powerTrophies);
    }

    /** Lo que traía la VS DIM al importarlo. */
    public int getImportedVitalValues() { return importedVitalValues; }
    public int getPowerTrophies() { return powerTrophies; }
    public int getBattles() { return battles; }
    public int getWins() { return wins; }
    public int getLosses() { return losses; }
    public int getDraws() { return draws; }
    public int getVitalDelta() { return vitalDelta; }

    /** Vital Values que se escribirán al retirarlo: lo importado + el saldo, entre 0 y el tope. */
    public int getVitalValues(VitalRewards rewards) {
        return Math.max(0, Math.min(rewards.maxVitalValues, importedVitalValues + vitalDelta));
    }

    /** Registra una batalla; devuelve el cambio de Vital Values que produjo. */
    public int recordBattle(BattleOutcome outcome, int rivalStage, VitalRewards rewards) {
        int delta = rewards.deltaFor(outcome, rivalStage);
        battles++;
        switch (outcome) {
            case WIN -> wins++;
            case LOSS -> losses++;
            case DRAW -> draws++;
        }
        vitalDelta += delta;
        System.out.printf("[BATALLA] %s contra etapa %d: %+d VV (saldo %+d; %d batallas, %d-%d-%d)%n",
                outcome, rivalStage, delta, vitalDelta, battles, wins, losses, draws);
        return delta;
    }
}
