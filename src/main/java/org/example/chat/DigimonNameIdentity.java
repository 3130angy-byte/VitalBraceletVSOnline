package org.example.chat;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Identidad de nombre de UNA vida: qué nombre corresponde a cada slot
 * (según lo que el usuario fue transcribiendo del sprite real), y el
 * nombre único permanente si decidió darle uno propio. currentSlot lo
 * mantiene sincronizado DigimonInstance cada vez que cambia.
 */
public class DigimonNameIdentity {

    private final Map<Integer, String> slotNameHistory = new LinkedHashMap<>();
    private String uniqueName;
    private int currentSlot = 0;

    private Integer pendingNameSlot;
    private String pendingNameCandidate;

    private boolean justReachedUnnamedSlot = false;

    public void setCurrentSlot(int slot) { this.currentSlot = slot; }
    public int getCurrentSlot() { return currentSlot; }

    public boolean hasNameForSlot(int slot) { return slotNameHistory.containsKey(slot); }
    public String getNameForSlot(int slot) { return slotNameHistory.get(slot); }
    public void learnSlotName(int slot, String name) { slotNameHistory.put(slot, name); }
    public Map<Integer, String> getSlotNameHistory() { return slotNameHistory; }

    public boolean hasUniqueName() { return uniqueName != null; }
    public String getUniqueName() { return uniqueName; }
    public void setUniqueName(String name) { this.uniqueName = name; }

    public void startPendingConfirmation(int slot, String candidateName) {
        this.pendingNameSlot = slot;
        this.pendingNameCandidate = candidateName;
    }

    public boolean hasPendingConfirmation() { return pendingNameSlot != null; }
    public Integer getPendingNameSlot() { return pendingNameSlot; }
    public String getPendingNameCandidate() { return pendingNameCandidate; }

    public void clearPendingConfirmation() {
        this.pendingNameSlot = null;
        this.pendingNameCandidate = null;
    }

    /** Se marca al llegar a un slot nuevo sin nombre conocido; el contexto dinámico lo consume una sola vez. */
    public void markJustReachedUnnamedSlot() { this.justReachedUnnamedSlot = true; }
    public boolean isJustReachedUnnamedSlot() { return justReachedUnnamedSlot; }
    public void clearJustReachedUnnamedSlot() { this.justReachedUnnamedSlot = false; }

    /** El nombre que debe usarse para dirigirse al Digimon ahora mismo, o null si aún no se conoce ninguno. */
    public String effectiveName() {
        if (uniqueName != null) return uniqueName;
        return slotNameHistory.get(currentSlot);
    }
}