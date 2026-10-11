package com.pawanputra.bos.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pawanputra.bos.catalog.api.ServiceVerticalCode;
import com.pawanputra.bos.platform.error.ErrorCode;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceVerticalServiceTest {

    @Mock
    ServiceVerticalRepository repository;

    @Mock
    ServiceVertical solar;

    @InjectMocks
    ServiceVerticalService service;

    @Test
    void returnsTheVerticalForAKnownCode() {
        when(repository.findByCode(ServiceVerticalCode.SOLAR)).thenReturn(Optional.of(solar));

        assertThat(service.getByCode("SOLAR")).isSameAs(solar);
    }

    @Test
    void unknownCodeIsNotFoundWithoutTouchingTheDatabase() {
        assertThatThrownBy(() -> service.getByCode("REAL_ESTATE"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Service vertical 'REAL_ESTATE' was not found")
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(repository);
    }

    @Test
    void knownCodeMissingFromTheDatabaseIsNotFound() {
        when(repository.findByCode(ServiceVerticalCode.SOLAR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByCode("SOLAR")).isInstanceOf(ResourceNotFoundException.class);
    }
}
