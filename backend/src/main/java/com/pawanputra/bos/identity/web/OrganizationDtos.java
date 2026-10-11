package com.pawanputra.bos.identity.web;

import com.pawanputra.bos.identity.api.OrganizationStatus;
import com.pawanputra.bos.identity.internal.Branch;
import com.pawanputra.bos.identity.internal.BranchLocation;
import com.pawanputra.bos.identity.internal.BranchService;
import com.pawanputra.bos.identity.internal.Organization;
import com.pawanputra.bos.identity.internal.OrganizationService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request and response bodies of the organization and branch endpoints. */
final class OrganizationDtos {

    private static final String CODE_PATTERN = "^[A-Z][A-Z0-9_-]*$";
    private static final String CODE_MESSAGE =
            "must start with a capital letter and contain only capital letters, digits, hyphens and underscores";
    private static final String PHONE_PATTERN = "^[+0-9][0-9 ()-]{5,28}$";
    private static final String PHONE_MESSAGE = "must be a phone number";

    private OrganizationDtos() {
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ------------------------------------------------------------------ organization

    record OrganizationResponse(
            UUID id, String code, String name, String legalName, String taxId, String email, String phone,
            OrganizationStatus status, long version, Instant updatedAt) {

        static OrganizationResponse from(Organization organization) {
            return new OrganizationResponse(
                    organization.getId(), organization.getCode(), organization.getName(),
                    organization.getLegalName(), organization.getTaxId(), organization.getEmail(),
                    organization.getPhone(), organization.getStatus(), organization.getVersion(),
                    organization.getUpdatedAt());
        }
    }

    record UpdateOrganizationRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 200) String legalName,
            @Size(max = 30) String taxId,
            @Email @Size(max = 254) String email,
            @Pattern(regexp = PHONE_PATTERN, message = PHONE_MESSAGE) String phone,
            @NotNull Long version) {

        OrganizationService.Details details() {
            return new OrganizationService.Details(
                    name.trim(), trimToNull(legalName), trimToNull(taxId), trimToNull(email), trimToNull(phone));
        }
    }

    // ------------------------------------------------------------------ branches

    record BranchResponse(
            UUID id,
            String code,
            String name,
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String postalCode,
            String countryCode,
            String phone,
            String email,
            Boolean headOffice,
            boolean active,
            long version,
            Instant updatedAt) {

        static BranchResponse from(Branch branch) {
            return new BranchResponse(
                    branch.getId(), branch.getCode(), branch.getName(), branch.getAddressLine1(),
                    branch.getAddressLine2(), branch.getCity(), branch.getState(), branch.getPostalCode(),
                    branch.getCountryCode(), branch.getPhone(), branch.getEmail(), branch.isHeadOffice(),
                    branch.isActive(), branch.getVersion(), branch.getUpdatedAt());
        }
    }

    record CreateBranchRequest(
            @NotBlank @Size(max = 50) @Pattern(regexp = CODE_PATTERN, message = CODE_MESSAGE) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 200) String addressLine1,
            @Size(max = 200) String addressLine2,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 100) String state,
            @Size(max = 20) String postalCode,
            @Pattern(regexp = "^[A-Z]{2}$", message = "must be a two-letter country code") String countryCode,
            @Pattern(regexp = PHONE_PATTERN, message = PHONE_MESSAGE) String phone,
            @Email @Size(max = 254) String email,
            Boolean headOffice) {

        BranchService.Details details() {
            return toDetails(name, addressLine1, addressLine2, city, state, postalCode, countryCode, phone, email,
                    headOffice);
        }
    }

    record UpdateBranchRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 200) String addressLine1,
            @Size(max = 200) String addressLine2,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 100) String state,
            @Size(max = 20) String postalCode,
            @Pattern(regexp = "^[A-Z]{2}$", message = "must be a two-letter country code") String countryCode,
            @Pattern(regexp = PHONE_PATTERN, message = PHONE_MESSAGE) String phone,
            @Email @Size(max = 254) String email,
            Boolean headOffice,
            @NotNull Long version) {

        BranchService.Details details() {
            return toDetails(name, addressLine1, addressLine2, city, state, postalCode, countryCode, phone, email,
                    headOffice);
        }
    }

    private static BranchService.Details toDetails(
            String name, String addressLine1, String addressLine2, String city, String state, String postalCode,
            String countryCode, String phone, String email, Boolean headOffice) {
        return new BranchService.Details(
                name.trim(), trimToNull(addressLine1), trimToNull(addressLine2), city.trim(), state.trim(),
                trimToNull(postalCode), countryCode == null ? "IN" : countryCode, trimToNull(phone),
                trimToNull(email), Boolean.TRUE.equals(headOffice));
    }

    /** @param cities the cities of that state in which the organization has a branch */
    record StateLocations(String state, List<String> cities) {

        static List<StateLocations> from(List<BranchLocation> locations) {
            Map<String, List<String>> byState = new LinkedHashMap<>();
            for (BranchLocation location : locations) {
                byState.computeIfAbsent(location.state(), state -> new java.util.ArrayList<>()).add(location.city());
            }
            return byState.entrySet().stream()
                    .map(entry -> new StateLocations(entry.getKey(), List.copyOf(entry.getValue())))
                    .toList();
        }
    }
}
