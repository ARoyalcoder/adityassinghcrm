package com.pawanputra.bos.project.internal.template;

import com.pawanputra.bos.catalog.api.ServiceDirectory;
import com.pawanputra.bos.catalog.api.ServiceDirectory.ServiceSummary;
import com.pawanputra.bos.identity.api.RoleCodes;
import com.pawanputra.bos.platform.error.ConflictException;
import com.pawanputra.bos.platform.error.FieldValidationException;
import com.pawanputra.bos.platform.error.FieldValidationException.Violation;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.project.api.ProjectTemplates;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Project templates: one per service and organization, defined by administrators. The rules a
 * template must obey are in {@link TemplatePlanner}; this class stores templates and reads them back.
 */
@Service
@Transactional(readOnly = true)
public class ProjectTemplateService implements ProjectTemplates {

    private final ProjectTemplateRepository templates;
    private final ProjectTemplateStageRepository stages;
    private final TemplateJson json;
    private final ServiceDirectory services;
    private final Clock clock;

    ProjectTemplateService(
            ProjectTemplateRepository templates, ProjectTemplateStageRepository stages, TemplateJson json,
            ServiceDirectory services, Clock clock) {
        this.templates = templates;
        this.stages = stages;
        this.json = json;
        this.services = services;
        this.clock = clock;
    }

    /** What an administrator may set. The service is chosen on creation only. */
    public record Details(String name, String description, List<StageSpec> stages) {
    }

    public record ServiceRef(UUID id, String name, String verticalName) {
    }

    /** A template as shown in the list. */
    public record Summary(
            UUID id, ServiceRef service, String name, boolean active, int stageCount, int totalDays, long version) {
    }

    /** A template with its stages placed in time. */
    public record Detail(
            UUID id, ServiceRef service, String name, String description, boolean active, int revision, Plan plan,
            long version) {
    }

    // ------------------------------------------------------------------ reading

    public List<Summary> list(UUID organizationId) {
        List<ProjectTemplate> found = templates.findAllByOrganizationIdOrderByNameAsc(organizationId);
        Map<UUID, List<StageSpec>> stagesByTemplate = new HashMap<>();
        for (ProjectTemplateStage stage : stages.findAllByTemplateIdInOrderByPositionAsc(
                found.stream().map(ProjectTemplate::getId).toList())) {
            stagesByTemplate.computeIfAbsent(stage.getTemplateId(), id -> new ArrayList<>()).add(json.read(stage));
        }
        Map<UUID, ServiceSummary> named = services.findAll(
                found.stream().map(ProjectTemplate::getServiceId).collect(Collectors.toSet()));
        return found.stream().map(template -> {
            List<StageSpec> own = stagesByTemplate.getOrDefault(template.getId(), List.of());
            return new Summary(template.getId(), serviceRef(template.getServiceId(), named.get(template.getServiceId())),
                    template.getName(), template.isActive(), own.size(), TemplatePlanner.schedule(own).totalDays(),
                    template.getVersion());
        }).toList();
    }

    public Detail get(UUID organizationId, UUID id) {
        return detail(load(organizationId, id));
    }

    /** The role codes a stage or task can be made the responsibility of, in alphabetical order. */
    public List<String> assignableRoles() {
        return RoleCodes.ALL.stream().sorted().toList();
    }

    @Override
    public Optional<Template> activeForService(UUID organizationId, UUID serviceId) {
        return templates.findByOrganizationIdAndServiceId(organizationId, serviceId)
                .filter(ProjectTemplate::isActive)
                .map(template -> new Template(template.getId(), template.getServiceId(), template.getName(),
                        template.getDescription(), plan(template.getId())));
    }

    // ------------------------------------------------------------------ writing

    @Transactional
    public Detail create(UUID organizationId, UUID serviceId, Details details) {
        if (services.find(serviceId).isEmpty()) {
            throw new FieldValidationException("serviceId", "Choose a service");
        }
        if (templates.existsByOrganizationIdAndServiceId(organizationId, serviceId)) {
            throw new ConflictException("This service already has a project template. Edit that one instead.");
        }
        validate(details);
        ProjectTemplate template = new ProjectTemplate(organizationId, serviceId, details.name());
        template.setDescription(details.description());
        template = templates.saveAndFlush(template);
        saveStages(template.getId(), details.stages());
        return detail(template);
    }

    @Transactional
    public Detail update(UUID organizationId, UUID id, Details details, long expectedVersion) {
        ProjectTemplate template = load(organizationId, id);
        if (template.getVersion() != expectedVersion) {
            throw new ConflictException(
                    "This template was changed by someone else. Reload it and apply your changes again.");
        }
        validate(details);
        template.setName(details.name());
        template.setDescription(details.description());
        template.stagesSaved();
        template = templates.saveAndFlush(template);
        // The stages are replaced as a set: projects copy a template when it is applied, so nothing
        // refers to a stage row, and replacing is the only way reordering and renaming keys stay simple.
        stages.deleteAllOfTemplate(id);
        saveStages(id, details.stages());
        return detail(templates.findById(id).orElseThrow());
    }

    /** A template that is switched off is kept, but is not offered for new projects. */
    @Transactional
    public Detail setActive(UUID organizationId, UUID id, boolean active) {
        ProjectTemplate template = load(organizationId, id);
        template.setActive(active);
        return detail(templates.saveAndFlush(template));
    }

    /** Soft delete: the service can then be given a new template. Projects already planned from it are untouched. */
    @Transactional
    public void delete(UUID organizationId, UUID id) {
        load(organizationId, id).markDeleted(clock.instant());
    }

    // ------------------------------------------------------------------ helpers

    private static void validate(Details details) {
        List<Violation> violations = TemplatePlanner.validate(details.stages());
        if (!violations.isEmpty()) {
            throw new FieldValidationException(violations);
        }
    }

    private void saveStages(UUID templateId, List<StageSpec> specs) {
        List<ProjectTemplateStage> rows = new ArrayList<>();
        for (int position = 0; position < specs.size(); position++) {
            rows.add(json.write(templateId, position, specs.get(position)));
        }
        stages.saveAllAndFlush(rows);
    }

    private ProjectTemplate load(UUID organizationId, UUID id) {
        return templates.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Project template", id));
    }

    private Plan plan(UUID templateId) {
        return TemplatePlanner.schedule(
                stages.findAllByTemplateIdOrderByPositionAsc(templateId).stream().map(json::read).toList());
    }

    private Detail detail(ProjectTemplate template) {
        return new Detail(
                template.getId(),
                serviceRef(template.getServiceId(), services.find(template.getServiceId()).orElse(null)),
                template.getName(), template.getDescription(), template.isActive(), template.getRevision(),
                plan(template.getId()), template.getVersion());
    }

    private static ServiceRef serviceRef(UUID serviceId, ServiceSummary summary) {
        // A service that was removed from the catalog is still shown, as what it was.
        return summary == null
                ? new ServiceRef(serviceId, "Removed service", null)
                : new ServiceRef(summary.id(), summary.name(), summary.verticalName());
    }
}
