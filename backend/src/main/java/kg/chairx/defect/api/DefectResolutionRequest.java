package kg.chairx.defect.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record DefectResolutionRequest(@NotBlank @Size(max=4000) String resolutionNote) {}
