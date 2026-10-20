package tulita.photocatalog;
import org.junit.jupiter.api.*;
import tulita.photocatalog.dao.*;
import tulita.photocatalog.service.*;
import static org.junit.jupiter.api.Assertions.*;
class PhotoVisionTest{
 @Test void actualOnnxModelRanksCatalogPhotoAndAlternateView()throws Exception{
  Assumptions.assumeTrue(Boolean.getBoolean("tulita.photoVisionTests"));var root=JsonPhotoCatalogDao.defaultRoot();var data=new JsonPhotoCatalogDao(root).load();
  try(var visual=new VisualSearch(ModelFiles.path())){
   visual.index(data,root,s->{});var product=data.stream().filter(p->p.id().equals("reynolds")).findFirst().orElseThrow();var image=VisualSearch.read(CatalogValidator.safe(root,product.photos().get(0).path()));var hits=visual.search(image,5);assertEquals("reynolds",hits.get(0).product().id());assertTrue(hits.get(0).similarity()>.99);assertEquals(5,hits.size());assertEquals(5,hits.stream().map(h->h.product().id()).distinct().count());
  }
  // Held-out retailer view: index only Walmart images, query Amazon/Target images.
  var limited=data.stream().map(p->new tulita.photocatalog.model.PhotoProduct(p.id(),p.name(),p.brand(),p.model(),p.variant(),p.category(),p.reviewNote(),p.offers(),p.photos().stream().filter(i->i.store().equals("Walmart")).toList())).toList();
  try(var visual=new VisualSearch(ModelFiles.path())){visual.index(limited,root,s->{});for(String id:java.util.List.of("reynolds","hamilton","ovente")){var p=data.stream().filter(x->x.id().equals(id)).findFirst().orElseThrow();var image=VisualSearch.read(CatalogValidator.safe(root,p.photos().stream().filter(i->i.store().equals("Amazon")).findFirst().orElseThrow().path()));var hits=visual.search(image,5);System.out.println("Held-out retailer photo "+id+" -> "+hits.stream().map(h->h.product().id()).toList());assertTrue(hits.stream().anyMatch(h->h.product().id().equals(id)));}}
 }
}
