package tulita.photocatalog;
import org.junit.jupiter.api.*;
import tulita.photocatalog.dao.*;
import tulita.photocatalog.model.PhotoProduct;
import tulita.photocatalog.service.*;
import java.util.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;
class PhotoCatalogTest{
 static List<PhotoProduct> data;static Path root;
 @BeforeAll static void load()throws Exception{root=JsonPhotoCatalogDao.defaultRoot();data=new JsonPhotoCatalogDao(root).load();}
 @Test void genuineCatalogContainsReviewedMultiStoreProducts(){assertEquals(79,data.size());assertEquals(161,data.stream().mapToInt(p->p.offers().size()).sum());assertTrue(data.stream().allMatch(p->p.offers().size()>=2));assertEquals(data.size(),data.stream().map(p->p.offers().stream().map(tulita.photocatalog.model.PhotoProduct.Offer::store).distinct().count()).filter(c->c>=2).count());}
 @Test void exactlyThreeThreeStoreGroups(){var selected=data.stream().filter(p->p.offers().size()==3).map(PhotoProduct::id).sorted().toList();assertEquals(List.of("franklin-dartboard","hamilton","reynolds"),selected);assertEquals(Set.of("Amazon","Walmart","Target"),data.stream().filter(p->p.id().equals("franklin-dartboard")).findFirst().orElseThrow().offers().stream().map(PhotoProduct.Offer::store).collect(java.util.stream.Collectors.toSet()));}
 @Test void photosAreRealDecodableLocalImages()throws Exception{assertEquals(237,data.stream().mapToInt(p->p.photos().size()).sum());for(var p:data)for(var image:p.photos())assertNotNull(VisualSearch.read(CatalogValidator.safe(root,image.path())));}
 @Test void originalUsPricesNotConverted(){var p=data.stream().filter(x->x.id().equals("reynolds")).findFirst().orElseThrow();assertEquals("USD 4.79",CatalogService.money(p.lowest(),p.currency()));assertEquals(Set.of("USD"),data.stream().flatMap(x->x.offers().stream()).map(PhotoProduct.Offer::currency).collect(java.util.stream.Collectors.toSet()));}
 @Test void textSearchModelAndVariant(){var service=new CatalogService(data);assertEquals(1,service.search("58149","All categories","All stores",false,false,Set.of(),0).size());assertEquals(1,service.search("16 GB","All categories","All stores",false,false,Set.of(),0).size());}
 @Test void filtersAndWatchlist(){var service=new CatalogService(data);assertEquals(3,service.search("","All categories","Target",true,false,Set.of(),0).size());assertEquals(1,service.search("","All categories","All stores",false,true,Set.of("ovente"),0).size());assertTrue(service.search("not-a-real-product","All categories","All stores",false,false,Set.of(),0).isEmpty());}
 @Test void sortingByRecordedUsdAmount(){var result=new CatalogService(data).search("","All categories","All stores",false,false,Set.of(),1);assertEquals("glade-solid-air-freshener",result.get(0).id());}
 @Test void everyProductDeclaresConsistentVariantEvidence(){for(var p:data){assertNotEquals("",p.variant());assertTrue(p.reviewNote().length()>=30);for(var o:p.offers())assertTrue(p.offers().stream().filter(x->x.store().equals(o.store())).count()==1);assertTrue(p.photos().stream().allMatch(ph->p.offers().stream().anyMatch(o->o.store().equals(ph.store()))));}}
 @Test void catalogSpansManyCategories(){assertTrue(data.stream().map(PhotoProduct::category).distinct().count()>=8);assertTrue(data.stream().anyMatch(p->p.id().equals("lego-bookshop-10270")));assertTrue(data.stream().anyMatch(p->p.id().equals("vitamix-a2300")));}
 @Test void cannotReuseProductIdentity()throws Exception{var bad=new ArrayList<>(data);bad.add(data.get(0));assertThrows(IllegalArgumentException.class,()->CatalogValidator.validate(bad,root));}
 @Test void cannotChangeOriginalSourcePrice(){var p=data.get(0);var o=p.offers().get(0);var forged=new PhotoProduct.Offer(o.store(),o.price().add(java.math.BigDecimal.ONE),o.currency(),o.title(),o.url(),o.observedAt(),o.sourceFile(),o.sourceRow(),o.sourceSide(),o.sourceRowSha256(),o.specifications(),o.description());var offers=new ArrayList<>(p.offers());offers.set(0,forged);var changed=new PhotoProduct(p.id(),p.name(),p.brand(),p.model(),p.variant(),p.category(),p.reviewNote(),offers,p.photos());assertThrows(IllegalArgumentException.class,()->CatalogValidator.validate(List.of(changed),root));}
 @Test void inventedModelRejected(){var p=data.stream().filter(x->x.id().equals("ovente")).findFirst().orElseThrow();var changed=new PhotoProduct(p.id(),p.name(),p.brand(),"WRONG999",p.variant(),p.category(),p.reviewNote(),p.offers(),p.photos());assertThrows(IllegalArgumentException.class,()->CatalogValidator.validate(List.of(changed),root));}
 @Test void singleStoreCardsNotPermitted(){var p=data.get(0);var one=new PhotoProduct(p.id(),p.name(),p.brand(),p.model(),p.variant(),p.category(),p.reviewNote(),p.offers().subList(0,1),p.photos());assertThrows(IllegalArgumentException.class,()->CatalogValidator.validate(List.of(one),root));}
 @Test void noInventedCaptureDates(){assertTrue(data.stream().flatMap(p->p.offers().stream()).allMatch(o->o.observedAt().isBlank()));}
 @Test void safeAssetPathsRejectTraversal(){assertThrows(IllegalArgumentException.class,()->CatalogValidator.safe(root,"../../secret"));assertThrows(IllegalArgumentException.class,()->CatalogValidator.safe(root,"/tmp/secret"));}
 @Test void clipPreprocessHasExpectedShapeAndNormalization(){var image=new BufferedImage(500,300,BufferedImage.TYPE_INT_RGB);var input=VisualSearch.preprocess(image);assertEquals(150528,input.length);assertEquals(-.48145466f/.26862954f,input[112*224+112],.0001f);}
}
