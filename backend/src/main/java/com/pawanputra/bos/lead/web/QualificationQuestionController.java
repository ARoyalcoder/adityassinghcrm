package com.pawanputra.bos.lead.web;

import com.pawanputra.bos.identity.api.Permissions;
import com.pawanputra.bos.lead.internal.QualificationQuestionService;
import com.pawanputra.bos.lead.internal.QualificationQuestionService.View;
import com.pawanputra.bos.lead.web.LeadDtos.CreateQuestionRequest;
import com.pawanputra.bos.lead.web.LeadDtos.UpdateQuestionRequest;
import com.pawanputra.bos.platform.config.OpenApiConfig;
import com.pawanputra.bos.platform.security.CurrentUser;
import com.pawanputra.bos.platform.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The organization's qualification questions, with what each answer is worth. Only those who may
 * configure leads see the points; people answering the questions (see {@code LeadController}) do not.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/lead-qualification/questions")
@Tag(name = "Lead qualification questions")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class QualificationQuestionController {

    private static final String CAN_CONFIGURE = "hasAuthority('" + Permissions.LEAD_CONFIGURE + "')";

    private final QualificationQuestionService questions;

    public QualificationQuestionController(QualificationQuestionService questions) {
        this.questions = questions;
    }

    @GetMapping
    @PreAuthorize(CAN_CONFIGURE)
    @Operation(summary = "Every question, switched on or off, in the order it is asked. Requires LEAD_CONFIGURE.")
    public List<View> list() {
        return questions.list(organizationId());
    }

    @PostMapping
    @PreAuthorize(CAN_CONFIGURE)
    @Operation(summary = "Add a question. Open leads are rescored. Requires LEAD_CONFIGURE.")
    public ResponseEntity<View> create(@Valid @RequestBody CreateQuestionRequest request) {
        return ResponseEntity.status(201).body(questions.create(organizationId(), request.key(), request.details()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_CONFIGURE)
    @Operation(summary = "Edit a question, its options and their points. Open leads are rescored. "
            + "Requires LEAD_CONFIGURE.")
    public View update(@PathVariable UUID id, @Valid @RequestBody UpdateQuestionRequest request) {
        return questions.update(organizationId(), id, request.details(), request.version());
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize(CAN_CONFIGURE)
    @Operation(summary = "Switch a question on. Requires LEAD_CONFIGURE.")
    public View activate(@PathVariable UUID id) {
        return questions.setActive(organizationId(), id, true);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize(CAN_CONFIGURE)
    @Operation(summary = "Switch a question off: it is no longer asked and its answers no longer count. "
            + "Requires LEAD_CONFIGURE.")
    public View deactivate(@PathVariable UUID id) {
        return questions.setActive(organizationId(), id, false);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN_CONFIGURE)
    @Operation(summary = "Remove a question (its key becomes reusable). Requires LEAD_CONFIGURE.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        questions.delete(organizationId(), id);
        return ResponseEntity.noContent().build();
    }

    private static UUID organizationId() {
        return CurrentUser.require().organizationId();
    }
}
