package tulita.dataset.model;
/** Store metadata is separate from canonical identity; unknown variants are never filled in. */
public record RecordProvenance(String store,String sourceKey,String sourceUrl,String license,
        String dateNote,String availability,String size,String color,String gender,String quantity) {
    public static RecordProvenance flipkart(){return new RecordProvenance("Flipkart",DatasetSnapshot.SOURCE_KEY,DatasetSnapshot.URL,DatasetSnapshot.LICENSE,"Original row timestamp supplied.","Unknown","","","","");}
}
