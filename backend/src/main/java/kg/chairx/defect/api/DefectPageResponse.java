package kg.chairx.defect.api;

import java.util.List;
import kg.chairx.defect.domain.Defect;
public record DefectPageResponse(List<Defect> items,int page,int size,long total) {}
