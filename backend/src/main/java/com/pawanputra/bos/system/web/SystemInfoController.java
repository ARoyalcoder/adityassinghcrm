package com.pawanputra.bos.system.web;

import com.pawanputra.bos.platform.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.V1 + "/system")
@Tag(name = "System")
public class SystemInfoController {

    private static final String PRODUCT_NAME = "Pawan Putra Business OS";

    private final String version;
    private final Clock clock;

    public SystemInfoController(ObjectProvider<BuildProperties> buildProperties, Clock clock) {
        // BuildProperties only exists when the app was packaged by Maven (not when run from an IDE).
        BuildProperties build = buildProperties.getIfAvailable();
        this.version = build != null ? build.getVersion() : "dev";
        this.clock = clock;
    }

    @GetMapping("/info")
    @Operation(summary = "Product name, build version and server time. Public.")
    public SystemInfoResponse info() {
        return new SystemInfoResponse(PRODUCT_NAME, version, "v1", clock.instant());
    }
}
