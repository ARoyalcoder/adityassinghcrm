package com.pawanputra.bos.catalog.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawanputra.bos.catalog.api.ServiceVerticalCode;
import com.pawanputra.bos.catalog.internal.ServiceVertical;
import com.pawanputra.bos.catalog.internal.ServiceVerticalService;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.support.WebLayerTestConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ServiceVerticalController.class)
@Import(WebLayerTestConfig.class)
@ActiveProfiles("test")
class ServiceVerticalControllerTest {

    private static final UUID CCTV_ID = UUID.fromString("0b6c2a7e-6c1d-4a1e-9a55-0c3f3f1f6f10");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ServiceVerticalService service;

    @Test
    void listsVerticalsWithoutAuthentication() throws Exception {
        List<ServiceVertical> verticals = List.of(
                vertical(CCTV_ID, ServiceVerticalCode.CCTV_SECURITY, "CCTV & Security", 1),
                vertical(UUID.randomUUID(), ServiceVerticalCode.SOLAR, "Solar", 5));
        when(service.listActive()).thenReturn(verticals);

        mockMvc.perform(get("/api/v1/service-verticals"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(CCTV_ID.toString()))
                .andExpect(jsonPath("$[0].code").value("CCTV_SECURITY"))
                .andExpect(jsonPath("$[0].name").value("CCTV & Security"))
                .andExpect(jsonPath("$[0].displayOrder").value(1))
                .andExpect(jsonPath("$[1].code").value("SOLAR"));
    }

    @Test
    void unknownCodeReturnsTheStandardNotFoundBody() throws Exception {
        when(service.getByCode("REAL_ESTATE"))
                .thenThrow(new ResourceNotFoundException("Service vertical", "REAL_ESTATE"));

        mockMvc.perform(get("/api/v1/service-verticals/REAL_ESTATE"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Service vertical 'REAL_ESTATE' was not found"))
                .andExpect(jsonPath("$.instance").value("/api/v1/service-verticals/REAL_ESTATE"));
    }

    private static ServiceVertical vertical(UUID id, ServiceVerticalCode code, String name, int order) {
        ServiceVertical vertical = mock(ServiceVertical.class);
        when(vertical.getId()).thenReturn(id);
        when(vertical.getCode()).thenReturn(code);
        when(vertical.getName()).thenReturn(name);
        when(vertical.getDisplayOrder()).thenReturn(order);
        return vertical;
    }
}
