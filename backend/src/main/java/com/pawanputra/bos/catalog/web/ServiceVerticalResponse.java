package com.pawanputra.bos.catalog.web;

import com.pawanputra.bos.catalog.api.ServiceVerticalCode;
import com.pawanputra.bos.catalog.internal.ServiceVertical;
import java.util.UUID;

public record ServiceVerticalResponse(
        UUID id, ServiceVerticalCode code, String name, String description, int displayOrder) {

    static ServiceVerticalResponse from(ServiceVertical vertical) {
        return new ServiceVerticalResponse(
                vertical.getId(),
                vertical.getCode(),
                vertical.getName(),
                vertical.getDescription(),
                vertical.getDisplayOrder());
    }
}
