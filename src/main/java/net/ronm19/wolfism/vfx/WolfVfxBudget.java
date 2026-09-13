package net.ronm19.wolfism.vfx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/** Source-fair admission with a reserved share for readable ability signatures. */
public final class WolfVfxBudget {
    private WolfVfxBudget() {}

    public static int emergencyShare(int limit) { return Math.min(4, Math.max(1, limit / 4)); }

    public static List<WolfVfxPayload> select(List<WolfVfxPayload> candidates, int limit, long tick) {
        return select(candidates, limit, tick, emergencyShare(limit));
    }

    /** Emergency slots stay inside the packet/tick limit; ordinary sources retain their share. */
    public static List<WolfVfxPayload> select(List<WolfVfxPayload> candidates, int limit, long tick, int emergencySlots) {
        if (candidates.isEmpty() || limit <= 0) return new ArrayList<>();
        var emergency = candidates.stream().filter(p -> p.phase() == WolfVfxPhase.EMERGENCY).toList();
        var ordinary = candidates.stream().filter(p -> p.phase() != WolfVfxPhase.EMERGENCY).toList();
        var selected = new ArrayList<>(selectOrdinary(emergency, Math.min(emergencyShare(limit), Math.max(0, emergencySlots)), tick));
        selected.addAll(selectOrdinary(ordinary, limit - selected.size(), tick));
        return selected;
    }

    private static List<WolfVfxPayload> selectOrdinary(List<WolfVfxPayload> candidates, int limit, long tick) {
        if (candidates.isEmpty() || limit <= 0) return List.of();
        var grouped = new TreeMap<Long, List<WolfVfxPayload>>();
        for (var candidate : candidates) grouped.computeIfAbsent(candidate.sourceKey(), ignored -> new ArrayList<>()).add(candidate);
        for (var group : grouped.values()) group.sort(Comparator.comparingInt(WolfVfxPayload::presentationPriority).reversed());
        var sources = new ArrayList<>(grouped.values());
        int offset = Math.floorMod(tick * Math.max(1, limit), sources.size());
        var selected = new ArrayList<WolfVfxPayload>(Math.min(limit, candidates.size()));
        int[] used = new int[sources.size()];
        // Three quarters prefer important cues. The remainder still rotates through
        // every source, including wolves currently producing only quiet ornament.
        int reserved = Math.max(1, limit * 3 / 4);
        for (int round = 0; selected.size() < reserved; round++) {
            boolean found = false;
            for (int i = 0; i < sources.size() && selected.size() < reserved; i++) {
                int index = (offset + i) % sources.size();
                var source = sources.get(index);
                if (round >= source.size() || !source.get(round).signature()) continue;
                selected.add(source.get(round)); used[index]++; found = true;
            }
            if (!found) break;
        }
        // Start with sources that have not yet received a slot, before admitting
        // a second effect from a source that already has a signature this tick.
        for (int round = 0; selected.size() < limit; round++) {
            boolean remaining = false;
            for (int i = 0; i < sources.size() && selected.size() < limit; i++) {
                int index = (offset + i) % sources.size();
                var source = sources.get(index);
                if (used[index] >= source.size()) continue;
                remaining = true;
                if (used[index] > round) continue;
                selected.add(source.get(used[index]++));
            }
            if (!remaining) break;
        }
        return selected;
    }
}
