package com.pawanputra.bos.system.web;

import java.time.Instant;

public record SystemInfoResponse(String name, String version, String apiVersion, Instant serverTime) {
}
