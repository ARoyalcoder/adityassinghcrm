package com.pawanputra.bos.project.internal.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawanputra.bos.platform.error.FieldValidationException.Violation;
import com.pawanputra.bos.project.api.ProjectTemplates.DocumentSpec;
import com.pawanputra.bos.project.api.ProjectTemplates.Plan;
import com.pawanputra.bos.project.api.ProjectTemplates.ScheduledStage;
import com.pawanputra.bos.project.api.ProjectTemplates.StageSpec;
import com.pawanputra.bos.project.api.ProjectTemplates.TaskSpec;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The rules every template obeys and how its stages are placed in time. The stages here are just test data. */
class TemplatePlannerTest {

    private static StageSpec stage(String key, int days, String... dependsOn) {
        return new StageSpec(key, key.replace('_', ' '), null, days, null, List.of(dependsOn), List.of(), List.of(), List.of());
    }

    /** Stages one after another, each waiting for the one before: "A → B → C". */
    private static List<StageSpec> chain(Object... keysAndDays) {
        List<StageSpec> stages = new ArrayList<>();
        for (int i = 0; i < keysAndDays.length; i += 2) {
            String key = (String) keysAndDays[i];
            stages.add(i == 0 ? stage(key, (int) keysAndDays[i + 1])
                    : stage(key, (int) keysAndDays[i + 1], (String) keysAndDays[i - 2]));
        }
        return stages;
    }

    private static List<String> timeline(Plan plan) {
        return plan.stages().stream()
                .map(s -> s.stage().key() + " " + s.startDay() + "-" + s.endDay()).toList();
    }

    private static List<String> problems(List<StageSpec> stages) {
        return TemplatePlanner.validate(stages).stream().map(v -> v.field() + ": " + v.message()).toList();
    }

    // ------------------------------------------------------------------ the examples from the brief

    @Test
    void interiorDesignRunsItsSevenStagesOneAfterAnother() {
        List<StageSpec> interior = chain("lead", 2, "requirement", 5, "concept", 7, "design_3d", 10, "revision", 5,
                "working_drawings", 10, "final_delivery", 3);

        assertThat(TemplatePlanner.validate(interior)).isEmpty();
        Plan plan = TemplatePlanner.schedule(interior);

        assertThat(timeline(plan)).containsExactly(
                "lead 0-2", "requirement 2-7", "concept 7-14", "design_3d 14-24", "revision 24-29",
                "working_drawings 29-39", "final_delivery 39-42");
        assertThat(plan.totalDays()).isEqualTo(42);
    }

    @Test
    void solarAndCctvAreOtherDataThroughTheSameRules() {
        List<StageSpec> solar = chain("site_survey", 2, "design", 4, "quotation", 2, "procurement", 14, "installation", 5,
                "testing", 1, "handover", 1);
        List<StageSpec> cctv = chain("site_survey", 1, "design", 2, "procurement", 7, "installation", 3,
                "configuration", 1, "testing", 1, "handover", 1);

        assertThat(TemplatePlanner.validate(solar)).isEmpty();
        assertThat(TemplatePlanner.validate(cctv)).isEmpty();
        assertThat(TemplatePlanner.schedule(solar).totalDays()).isEqualTo(29);
        assertThat(timeline(TemplatePlanner.schedule(cctv))).containsExactly(
                "site_survey 0-1", "design 1-3", "procurement 3-10", "installation 10-13", "configuration 13-14",
                "testing 14-15", "handover 15-16");
    }

    // ------------------------------------------------------------------ scheduling

    @Test
    void stagesThatDoNotDependOnEachOtherRunSideBySide() {
        // Procurement and the permit are both started by the design, and installation waits for both.
        List<StageSpec> stages = List.of(
                stage("design", 4),
                stage("procurement", 14, "design"),
                stage("permit", 20, "design"),
                stage("installation", 5, "procurement", "permit"),
                stage("training", 1));

        Plan plan = TemplatePlanner.schedule(stages);

        assertThat(timeline(plan)).containsExactly(
                "design 0-4", "procurement 4-18", "permit 4-24", "installation 24-29", "training 0-1");
        assertThat(plan.totalDays()).isEqualTo(29); // the longest path, not the sum (44)
    }

    @Test
    void theOrderOfTheListDoesNotDecideTheSchedule() {
        // Listed back to front: dependencies still place them correctly.
        Plan plan = TemplatePlanner.schedule(List.of(stage("handover", 1, "installation"), stage("installation", 5, "survey"),
                stage("survey", 2)));

        assertThat(timeline(plan)).containsExactly("handover 7-8", "installation 2-7", "survey 0-2");
        assertThat(TemplatePlanner.inDependencyOrder(plan)).extracting(s -> s.stage().key())
                .containsExactly("survey", "installation", "handover");
    }

    @Test
    void aStageMayTakeNoTime() {
        Plan plan = TemplatePlanner.schedule(chain("survey", 2, "sign_off", 0, "installation", 5));

        assertThat(timeline(plan)).containsExactly("survey 0-2", "sign_off 2-2", "installation 2-7");
        assertThat(TemplatePlanner.inDependencyOrder(plan)).extracting(ScheduledStage::startDay).containsExactly(0, 2, 2);
    }

    @Test
    void schedulingRefusesStagesThatWereNotValidated() {
        assertThatThrownBy(() -> TemplatePlanner.schedule(List.of(stage("a", 1, "b"), stage("b", 1, "a"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ validation

    @Test
    void aTemplateNeedsAtLeastOneStage() {
        assertThat(problems(List.of())).containsExactly("stages: Add at least one stage");
        assertThat(problems(null)).containsExactly("stages: Add at least one stage");
    }

    @Test
    void keysAndNamesAreDistinct() {
        StageSpec design = stage("design", 3);
        StageSpec again = new StageSpec("design", "DESIGN", null, 1, null, List.of(), List.of(), List.of(), List.of());

        assertThat(problems(List.of(design, again))).containsExactly(
                "stages[1].key: Another stage already has the key 'design'",
                "stages[1].name: Another stage is already called 'DESIGN'");
    }

    @Test
    void aStageDependsOnlyOnOtherStagesThatExist() {
        assertThat(problems(List.of(stage("survey", 1), stage("design", 2, "design"))))
                .containsExactly("stages[1].dependsOn: A stage cannot depend on itself");
        assertThat(problems(List.of(stage("survey", 1), stage("design", 2, "blueprint"))))
                .containsExactly("stages[1].dependsOn: There is no stage with the key 'blueprint'");
        assertThat(problems(List.of(stage("survey", 1), stage("design", 2, "survey", "survey"))))
                .containsExactly("stages[1].dependsOn: The stage 'survey' is listed twice");
    }

    @Test
    void stagesMayNotWaitForEachOtherInACircle() {
        // design → installation → testing → design; handover only waits for the circle.
        List<StageSpec> stages = List.of(
                stage("survey", 1),
                stage("design", 2, "survey", "testing"),
                stage("installation", 5, "design"),
                stage("testing", 1, "installation"),
                stage("handover", 1, "testing"));

        assertThat(TemplatePlanner.validate(stages)).extracting(Violation::field)
                .containsExactly("stages[1].dependsOn", "stages[2].dependsOn", "stages[3].dependsOn");
        assertThat(TemplatePlanner.validate(stages)).extracting(Violation::message)
                .containsOnly("This stage depends, through other stages, on itself");
    }

    @Test
    void responsibleRolesMustBeRealRoles() {
        StageSpec design = new StageSpec("design", "Design", null, 3, "DESIGNER", List.of(), List.of(),
                List.of(new TaskSpec("Draw", null, "TECHNICIAN", null, List.of()),
                        new TaskSpec("Review", null, "WIZARD", null, List.of())),
                List.of());
        StageSpec install = new StageSpec("install", "Install", null, 3, "PLUMBER", List.of(), List.of(), List.of(), List.of());

        assertThat(problems(List.of(design, install))).containsExactly(
                "stages[0].tasks[1].responsibleRole: 'WIZARD' is not a role",
                "stages[1].responsibleRole: 'PLUMBER' is not a role");
    }

    @Test
    void milestonesAndDocumentsAreNotRepeatedWithinAStage() {
        StageSpec design = new StageSpec("design", "Design", null, 3, null, List.of(),
                List.of("Client approval", "client approval"), List.of(),
                List.of(new DocumentSpec("Floor plan", true, null), new DocumentSpec("FLOOR PLAN", false, null)));
        // The same names in another stage are fine.
        StageSpec revision = new StageSpec("revision", "Revision", null, 3, null, List.of("design"),
                List.of("Client approval"), List.of(), List.of(new DocumentSpec("Floor plan", true, null)));

        assertThat(problems(List.of(design, revision))).containsExactly(
                "stages[0].milestones: The milestone 'client approval' is listed twice",
                "stages[0].documents: The document 'FLOOR PLAN' is listed twice");
    }

    @Test
    void thereIsALimitToTheNumberOfStages() {
        List<StageSpec> many = new ArrayList<>();
        for (int i = 0; i <= TemplatePlanner.MAX_STAGES; i++) {
            many.add(stage("stage_" + i, 1));
        }

        assertThat(problems(many)).containsExactly("stages: At most 40 stages");
        assertThat(problems(many.subList(0, TemplatePlanner.MAX_STAGES))).isEmpty();
    }

    @Test
    void everyProblemIsReportedAtOnce() {
        List<StageSpec> stages = List.of(
                stage("survey", 1, "nowhere"),
                new StageSpec("survey", "Survey again", null, 1, "NOBODY", List.of(), List.of(), List.of(), List.of()));

        assertThat(problems(stages)).containsExactly(
                "stages[1].key: Another stage already has the key 'survey'",
                "stages[1].responsibleRole: 'NOBODY' is not a role",
                "stages[0].dependsOn: There is no stage with the key 'nowhere'");
    }
}
