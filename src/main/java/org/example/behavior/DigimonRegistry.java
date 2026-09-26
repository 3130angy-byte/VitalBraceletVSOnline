package org.example.behavior;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DigimonRegistry {

    private final List<DigimonInstance> activeInstances = new ArrayList<>();

    public void add(DigimonInstance instance) {
        activeInstances.add(instance);
    }

    /** Quita una instancia del registro (p. ej. si falló al aparecer). */
    public void remove(String instanceId) {
        activeInstances.removeIf(i -> i.getInstanceId().equals(instanceId));
    }

    public List<DigimonInstance> getActiveInstances() {
        return activeInstances;
    }

    public boolean isEmpty() {
        return activeInstances.isEmpty();
    }

    public Optional<DigimonInstance> getOther(String instanceId) {
        return activeInstances.stream().filter(i -> !i.getInstanceId().equals(instanceId)).findFirst();
    }

    public Optional<DigimonInstance> getAssistant() {
        return activeInstances.stream().filter(DigimonInstance::isAssistant).findFirst();
    }

    public void setAssistant(String instanceId) {
        for (DigimonInstance instance : activeInstances) {
            instance.setAssistant(instance.getInstanceId().equals(instanceId));
        }
    }
}