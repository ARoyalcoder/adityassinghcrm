package com.pawanputra.bos.project.internal.template;

import com.pawanputra.bos.identity.api.RoleCodes;
import com.pawanputra.bos.platform.error.FieldValidationException.Violation;
import com.pawanputra.bos.project.api.ProjectTemplates.DocumentSpec;
import com.pawanputra.bos.project.api.ProjectTemplates.Plan;
import com.pawanputra.bos.project.api.ProjectTemplates.ScheduledStage;
import com.pawanputra.bos.project.api.ProjectTemplates.StageSpec;
import com.pawanputra.bos.project.api.ProjectTemplates.TaskSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The rules every template obeys, whatever its stages are: stages have distinct keys and names,
 * depend only on stages that exist, and never (directly or through others) on themselves. From a
 * valid template it works out when each stage can start.
 *
 * <p>Pure functions of the stages given: no stage, service or vertical is named here.
 */
public final class TemplatePlanner {

    public static final int MAX_STAGES = 40;

    private TemplatePlanner() {
    }

    /**
     * Problems with the stages as a set, each under the path of the offending value
     * ({@code stages[2].dependsOn}), so the editor can show it in place. Empty when the stages are valid.
     */
    public static List<Violation> validate(List<StageSpec> stages) {
        List<Violation> violations = new ArrayList<>();
        if (stages == null || stages.isEmpty()) {
            violations.add(new Violation("stages", "Add at least one stage"));
            return violations;
        }
        if (stages.size() > MAX_STAGES) {
            violations.add(new Violation("stages", "At most " + MAX_STAGES + " stages"));
        }

        Set<String> keys = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < stages.size(); i++) {
            StageSpec stage = stages.get(i);
            String at = "stages[" + i + "]";
            if (!keys.add(stage.key())) {
                violations.add(new Violation(at + ".key", "Another stage already has the key '" + stage.key() + "'"));
            }
            if (!names.add(stage.name().toLowerCase(Locale.ROOT))) {
                violations.add(new Violation(at + ".name", "Another stage is already called '" + stage.name() + "'"));
            }
            checkRole(stage.responsibleRole(), at + ".responsibleRole", violations);
            checkDistinct(stage.milestones(), at + ".milestones", "milestone", violations);
            checkDistinct(stage.documents().stream().map(DocumentSpec::name).toList(), at + ".documents", "document",
                    violations);
            for (int t = 0; t < stage.tasks().size(); t++) {
                TaskSpec task = stage.tasks().get(t);
                checkRole(task.responsibleRole(), at + ".tasks[" + t + "].responsibleRole", violations);
            }
        }

        Set<String> known = Set.copyOf(keys);
        boolean dependenciesSound = true;
        for (int i = 0; i < stages.size(); i++) {
            StageSpec stage = stages.get(i);
            String at = "stages[" + i + "].dependsOn";
            Set<String> seen = new HashSet<>();
            for (String dependency : stage.dependsOn()) {
                if (dependency.equals(stage.key())) {
                    violations.add(new Violation(at, "A stage cannot depend on itself"));
                    dependenciesSound = false;
                } else if (!known.contains(dependency)) {
                    violations.add(new Violation(at, "There is no stage with the key '" + dependency + "'"));
                    dependenciesSound = false;
                } else if (!seen.add(dependency)) {
                    violations.add(new Violation(at, "The stage '" + dependency + "' is listed twice"));
                }
            }
        }
        // Looking for circles only makes sense once every dependency points at a real, other stage.
        if (dependenciesSound && keys.size() == stages.size()) {
            Set<String> circular = stagesInACircle(stages);
            for (int i = 0; i < stages.size(); i++) {
                if (circular.contains(stages.get(i).key())) {
                    violations.add(new Violation("stages[" + i + "].dependsOn",
                            "This stage depends, through other stages, on itself"));
                }
            }
        }
        return violations;
    }

    /**
     * Places valid stages in time. A stage starts when the last stage it depends on has ended, or
     * on day 0 if it depends on none; stages that do not depend on each other run side by side.
     */
    public static Plan schedule(List<StageSpec> stages) {
        Map<String, StageSpec> byKey = new HashMap<>();
        stages.forEach(stage -> byKey.put(stage.key(), stage));
        Map<String, Integer> endDays = new HashMap<>();
        List<ScheduledStage> scheduled = new ArrayList<>();
        int total = 0;
        for (StageSpec stage : stages) {
            int end = endDay(stage, byKey, endDays, new HashSet<>());
            scheduled.add(new ScheduledStage(stage, end - stage.durationDays(), end));
            total = Math.max(total, end);
        }
        return new Plan(scheduled, total);
    }

    /**
     * The stages of a plan in an order in which every stage comes after the stages it depends on
     * (otherwise as the administrator listed them). For work that must build one stage on another.
     */
    public static List<ScheduledStage> inDependencyOrder(Plan plan) {
        List<ScheduledStage> remaining = new ArrayList<>(plan.stages());
        List<ScheduledStage> ordered = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        while (!remaining.isEmpty()) {
            ScheduledStage next = remaining.stream()
                    .filter(candidate -> placed.containsAll(candidate.stage().dependsOn()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Stages depend on each other in a circle"));
            remaining.remove(next);
            ordered.add(next);
            placed.add(next.stage().key());
        }
        return ordered;
    }

    private static int endDay(
            StageSpec stage, Map<String, StageSpec> byKey, Map<String, Integer> known, Set<String> inProgress) {
        Integer already = known.get(stage.key());
        if (already != null) {
            return already;
        }
        if (!inProgress.add(stage.key())) {
            throw new IllegalArgumentException("Stages depend on each other in a circle: validate before scheduling");
        }
        int start = 0;
        for (String dependency : stage.dependsOn()) {
            start = Math.max(start, endDay(byKey.get(dependency), byKey, known, inProgress));
        }
        inProgress.remove(stage.key());
        int end = start + stage.durationDays();
        known.put(stage.key(), end);
        return end;
    }

    /** The keys of all stages that can never start because they wait, in the end, for themselves. */
    private static Set<String> stagesInACircle(List<StageSpec> stages) {
        Map<String, StageSpec> byKey = new HashMap<>();
        stages.forEach(stage -> byKey.put(stage.key(), stage));
        Set<String> circular = new HashSet<>();
        for (StageSpec stage : stages) {
            // Follow what the stage waits for; if that leads back to the stage, it is in a circle.
            // (A stage that merely waits for a circular one is not itself reported.)
            Set<String> visited = new HashSet<>();
            List<String> toVisit = new ArrayList<>(stage.dependsOn());
            while (!toVisit.isEmpty()) {
                String key = toVisit.removeLast();
                if (key.equals(stage.key())) {
                    circular.add(stage.key());
                    break;
                }
                if (visited.add(key)) {
                    toVisit.addAll(byKey.get(key).dependsOn());
                }
            }
        }
        return circular;
    }

    private static void checkRole(String role, String path, List<Violation> violations) {
        if (role != null && !RoleCodes.ALL.contains(role)) {
            violations.add(new Violation(path, "'" + role + "' is not a role"));
        }
    }

    private static void checkDistinct(List<String> values, String path, String what, List<Violation> violations) {
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (!seen.add(value.toLowerCase(Locale.ROOT))) {
                violations.add(new Violation(path, "The " + what + " '" + value + "' is listed twice"));
            }
        }
    }
}
