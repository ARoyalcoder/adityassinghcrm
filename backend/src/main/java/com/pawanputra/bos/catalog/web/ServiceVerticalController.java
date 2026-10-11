package com.pawanputra.bos.catalog.web;

import com.pawanputra.bos.catalog.internal.ServiceVerticalService;
import com.pawanputra.bos.platform.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.V1 + "/service-verticals")
@Tag(name = "Service verticals")
public class ServiceVerticalController {

    private final ServiceVerticalService service;

    public ServiceVerticalController(ServiceVerticalService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List the active service verticals in display order. Public; not paginated "
            + "because the set is small and fixed.")
    public List<ServiceVerticalResponse> list() {
        return service.listActive().stream().map(ServiceVerticalResponse::from).toList();
    }

    @GetMapping("/{code}")
    @Operation(summary = "Get one service vertical by its code. Public.")
    public ServiceVerticalResponse get(@PathVariable String code) {
        return ServiceVerticalResponse.from(service.getByCode(code));
    }
}
