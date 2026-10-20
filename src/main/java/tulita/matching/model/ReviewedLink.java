package tulita.matching.model;
import java.time.LocalDate;
public record ReviewedLink(CanonicalProduct product,String store,String recordId,String recordSha256,
        String evidenceUrl,LocalDate reviewedOn,String reviewNote) { }
