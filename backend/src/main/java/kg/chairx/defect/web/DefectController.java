package kg.chairx.defect.web;

import kg.chairx.defect.api.*;
import kg.chairx.defect.application.DefectService;
import kg.chairx.defect.domain.Defect;
import kg.chairx.defect.domain.DefectStatus;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/defects")
public class DefectController {
    private final DefectService defects;
    public DefectController(DefectService defects) { this.defects=defects; }

    @PostMapping
    public ResponseEntity<Defect> open(@Valid @RequestBody OpenDefectRequest request,
                                        Authentication authentication) {
        Defect result=defects.open(request.warehouseId(),request.productVariantId(),
                request.supplierId(),request.purchaseReceiptItemId(),
                request.quantity(),request.description(),authentication.getName());
        return ResponseEntity.created(URI.create("/api/defects/"+result.id())).body(result);
    }

    @GetMapping("/{id}")
    public Defect get(@PathVariable UUID id) { return defects.get(id); }

    @GetMapping
    public DefectPageResponse list(@RequestParam(required=false) DefectStatus status,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return defects.list(status,page,size);
    }

    @PostMapping("/{id}/wait-for-parts")
    public Defect waitForParts(@PathVariable UUID id) { return defects.waitForParts(id); }

    @PostMapping("/{id}/resolve")
    public Defect resolve(@PathVariable UUID id,@Valid @RequestBody DefectResolutionRequest request,
                           Authentication authentication) {
        return defects.resolve(id,request.resolutionNote(),authentication.getName());
    }

    @PostMapping("/{id}/write-off")
    public Defect writeOff(@PathVariable UUID id,@Valid @RequestBody DefectResolutionRequest request,
                            Authentication authentication) {
        return defects.writeOff(id,request.resolutionNote(),authentication.getName());
    }
}
