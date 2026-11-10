package com.upoharlab.order;

import static com.upoharlab.order.OrderTestSupport.*;
import static org.assertj.core.api.Assertions.*;

import com.upoharlab.landing.LandingForm;
import com.upoharlab.order.OrderTestSupport.Browser;
import com.upoharlab.product.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.validation.BeanPropertyBindingResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LandingAdminFlowTest {
  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.15-alpine");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) throws Exception {
    registry.add("app.admin.username", () -> "test-owner");
    registry.add("app.admin.password", () -> "test-only-password-2026");
    registry.add("app.orders.max-per-hour", () -> "100000");
    registry.add("app.uploads-directory", Files.createTempDirectory("landing-admin")::toString);
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired JdbcTemplate db;
  @Autowired ProductEditor editor;
  @Autowired ProductStore products;
  @Autowired ImageStorage images;
  @MockitoBean OrderAlerts.Sender sender;
  @LocalServerPort int port;

  @BeforeEach
  void open() {
    clear(db);
    setting(db, "accepting_orders", "true");
    setting(db, "whatsapp", "01700000000");
  }

  @AfterEach
  void clean() {
    clear(db);
    db.update("DELETE FROM product_image");
    db.update("DELETE FROM product WHERE slug='admin-mug'");
  }

  private static byte[] jpeg() throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(600, 300, BufferedImage.TYPE_INT_RGB), "jpg", out);
    return out.toByteArray();
  }

  private ProductForm details(String status) {
    var f = new ProductForm();
    f.setTitle("মগ");
    f.setSlug("admin-mug");
    f.setPrice(500);
    f.setShortDescription("নামের মগ");
    f.setIncludes("একটি মগ");
    f.setMaterial("সিরামিক");
    f.setSize("১১ আউন্স");
    f.setEngravingHelp("একটি নাম");
    f.setMakingDaysMin(1);
    f.setMakingDaysMax(2);
    f.setStatus(status);
    return f;
  }

  private LandingForm landing(String image) {
    LandingForm f = LandingForm.from(null);
    f.submitted = true;
    f.enabled = true;
    f.hero.headline = "নিজের নামের মগ";
    f.hero.heroImage = image;
    f.hero.oldPrice = 700;
    f.hero.offerPrice = 500;
    f.benefits = new ArrayList<>(List.of("এক", "দুই", "তিন", "চার"));
    int[] prices = {500, 650, 900};
    for (int i = 0; i < 3; i++) {
      f.packages.get(i).name = "প্যাক " + i;
      f.packages.get(i).price = prices[i];
    }
    f.popularPackage = 1;
    f.advanceAmount = 100;
    f.delivery.insideDhakaCharge = 60;
    f.delivery.outsideDhakaCharge = 110;
    f.preview.defaultText = "রহমান";
    f.preview.maxChars = 20;
    f.preview.fonts.add("Galada");
    var d = f.preview.designs.get(0);
    d.name = "ক্লাসিক";
    d.baseImage = image;
    d.textX = 300;
    d.textY = 150;
    d.maxWidth = 400;
    d.fontSize = 60;
    d.minFontSize = 20;
    d.color = "#112233";
    return f;
  }

  private long createPublished() throws Exception {
    var draft = details("DRAFT");
    long id =
        editor.save(
            null,
            draft,
            new BeanPropertyBindingResult(draft, "f"),
            List.of(new MockMultipartFile("photos", "a.jpg", "image/jpeg", jpeg())));
    images.uploadLanding(id, new MockMultipartFile("photo", "b.jpg", "image/jpeg", jpeg()), "হিরো");
    String image = products.get(id).landingPhotos().get(0).filename();
    var form = details("ACTIVE");
    var errors = new BeanPropertyBindingResult(form, "f");
    editor.save(id, form, landing(image), errors, List.of());
    assertThat(errors.getAllErrors()).isEmpty();
    return id;
  }

  private Browser admin() throws Exception {
    Browser b = new Browser(port);
    b.get("/admin/login");
    assertThat(
            b.post("/admin/login", Map.of("username", "test-owner", "password", "test-only-password-2026"))
                .statusCode())
        .isEqualTo(302);
    return b;
  }

  @Test
  void aProductMadeOnlyThroughTheAdminGetsACompletePage() throws Exception {
    long id = createPublished();
    assertThat(products.get(id).details().getPrice()).as("follows popular package").isEqualTo(650);
    assertThat(products.get(id).photos()).as("landing images stay out of the gallery").hasSize(1);
    assertThat(products.get(id).landing().preview().designs().get(0).imageWidth()).isEqualTo(600);
    String html = new Browser(port).get("/order/admin-mug").body();
    assertThat(html).contains("নিজের নামের মগ", text("product.submit"), text("product.packages.popular"));
  }

  @Test
  void publishingAnIncompletePageIsRefusedAndNothingIsSaved() throws Exception {
    var draft = details("DRAFT");
    long id = editor.save(null, draft, new BeanPropertyBindingResult(draft, "f"),
        List.of(new MockMultipartFile("photos", "a.jpg", "image/jpeg", jpeg())));
    var form = details("ACTIVE");
    LandingForm incomplete = LandingForm.from(null);
    incomplete.submitted = true;
    incomplete.enabled = true;
    var errors = new BeanPropertyBindingResult(form, "f");
    editor.save(id, form, incomplete, errors, List.of());
    assertThat(errors.hasGlobalErrors()).isTrue();
    assertThat(products.get(id).details().getStatus()).isEqualTo("DRAFT");
    assertThat(products.get(id).landing()).isNull();
  }

  @Test
  void aProductCannotGoPublicWithoutALandingPage() throws Exception {
    var draft = details("DRAFT");
    long id = editor.save(null, draft, new BeanPropertyBindingResult(draft, "f"),
        List.of(new MockMultipartFile("photos", "a.jpg", "image/jpeg", jpeg())));
    var form = details("ACTIVE");
    LandingForm off = LandingForm.from(null);
    off.submitted = true;
    var errors = new BeanPropertyBindingResult(form, "f");
    editor.save(id, form, off, errors, List.of());
    assertThat(errors.getGlobalErrors()).anyMatch(e -> e.getDefaultMessage().contains("landing page to publish"));
    assertThat(products.get(id).details().getStatus()).isEqualTo("DRAFT");
    Browser b = admin();
    assertThat(b.get("/admin/products").statusCode()).isEqualTo(200);
  }

  @Test
  void anImageInUseCannotBeDeleted() throws Exception {
    long id = createPublished();
    long photo = products.get(id).landingPhotos().get(0).id();
    assertThatThrownBy(() -> products.removePhoto(id, photo))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("landing page");
  }

  @Test
  void editorAndPreviewRenderForTheOwner() throws Exception {
    long id = createPublished();
    Browser b = admin();
    String editorHtml = b.get("/admin/products/" + id).body();
    assertThat(editorHtml).contains("Landing page", "name=\"packages[0].price\"", "name=\"preview.designs[0].textX\"");
    assertThat(b.get("/admin/products/" + id + "/landing-preview").body()).contains("Preview only");
    long seed = db.queryForObject("SELECT id FROM product WHERE slug='nameplate'", Long.class);
    assertThat(b.get("/admin/products/" + seed + "/landing-preview").statusCode()).isEqualTo(200);
    assertThat(b.get("/admin/products/new").statusCode()).isEqualTo(200);
  }

  @Test
  void editorPostSavesRowsThroughFormBinding() throws Exception {
    long id = createPublished();
    Browser b = admin();
    b.get("/admin/products/" + id);
    Map<String, String> f = new LinkedHashMap<>();
    var d = products.get(id).details();
    f.put("title", d.getTitle()); f.put("slug", d.getSlug()); f.put("status", "DRAFT");
    f.put("sortOrder", "0"); f.put("submitted", "true"); f.put("_enabled", "on");
    f.put("hero.headline", "নতুন শিরোনাম");
    f.put("packages[0].name", "এক"); f.put("packages[0].price", "321"); f.put("popularPackage", "0");
    assertThat(b.post("/admin/products/" + id, f).statusCode()).isEqualTo(302);
    var saved = products.get(id).landing();
    assertThat(saved.hero().headline()).isEqualTo("নতুন শিরোনাম");
    assertThat(saved.packages()).hasSize(1);
    assertThat(saved.packages().get(0).price()).isEqualTo(321);
    assertThat(saved.packages().get(0).isPopular()).isTrue();
    assertThat(saved.enabled()).isFalse();
  }

  @Test
  void theFullEditorFitsInOneMultipartPost() throws Exception {
    long id = createPublished();
    Browser b = admin();
    b.get("/admin/products/" + id);
    Map<String, String> f = new LinkedHashMap<>();
    f.put("_csrf", b.csrf); f.put("title", "মগ"); f.put("slug", "admin-mug"); f.put("status", "DRAFT");
    f.put("sortOrder", "0"); f.put("submitted", "true");
    for (int i = 0; i < 300; i++) f.put("benefits[" + i % 4 + "]x" + i, "");
    assertThat(b.multipart("/admin/products/" + id, f).statusCode()).isEqualTo(302);
  }

  private Map<String, String> publishFields(Browser b) {
    Map<String, String> f = new LinkedHashMap<>();
    f.put("_csrf", b.csrf);
    f.put("title", "মগ"); f.put("slug", "admin-mug"); f.put("status", "ACTIVE"); f.put("sortOrder", "0");
    f.put("shortDescription", "নামের মগ"); f.put("includes", "একটি"); f.put("material", "সিরামিক");
    f.put("size", "১১"); f.put("engravingHelp", "নাম"); f.put("makingDaysMin", "1"); f.put("makingDaysMax", "2");
    f.put("submitted", "true"); f.put("enabled", "true");
    f.put("hero.headline", "নতুন মগ"); f.put("hero.offerPrice", "500");
    for (int i = 0; i < 4; i++) f.put("benefits[" + i + "]", "সুবিধা " + i);
    for (int i = 0; i < 3; i++) { f.put("packages[" + i + "].name", "প্যাক " + i); f.put("packages[" + i + "].price", String.valueOf(500 + i)); }
    f.put("popularPackage", "1"); f.put("advanceAmount", "100");
    f.put("delivery.insideDhakaCharge", "60"); f.put("delivery.outsideDhakaCharge", "110");
    f.put("preview.defaultText", "নাম"); f.put("preview.maxChars", "20"); f.put("preview.fonts", "Galada");
    f.put("preview.designs[0].name", "ক"); f.put("preview.designs[0].textX", "300"); f.put("preview.designs[0].textY", "150");
    f.put("preview.designs[0].maxWidth", "400"); f.put("preview.designs[0].fontSize", "60"); f.put("preview.designs[0].minFontSize", "20");
    f.put("preview.designs[0].color", "#112233");
    return f;
  }

  @Test
  void aNewProductPublishesInOnePostWithItsLandingImages() throws Exception {
    Browser b = admin();
    b.get("/admin/products/new");
    Map<String, byte[]> files = new LinkedHashMap<>();
    files.put("photos", jpeg());
    files.put("landingUpload/hero/heroImage", jpeg());
    files.put("landingUpload/preview/designs(0)/baseImage", jpeg());
    var res = b.multipart("/admin/products/new", publishFields(b), files);
    assertThat(res.statusCode()).isEqualTo(302);
    long id = db.queryForObject("SELECT id FROM product WHERE slug='admin-mug'", Long.class);
    var page = products.get(id);
    assertThat(page.details().getStatus()).isEqualTo("ACTIVE");
    assertThat(page.landingPhotos()).hasSize(2);
    assertThat(page.photos()).hasSize(1);
    assertThat(page.landing().hero().heroImage()).isEqualTo(page.landingPhotos().get(0).filename());
    assertThat(page.landing().preview().designs().get(0).imageWidth()).isEqualTo(600);
    assertThat(new Browser(port).get("/order/admin-mug").statusCode()).isEqualTo(200);
  }

  @Test
  void aFailedPublishLeavesNoProductOrLandingImages() throws Exception {
    Browser b = admin();
    b.get("/admin/products/new");
    Map<String, String> f = publishFields(b);
    f.remove("advanceAmount");
    var files = Map.of("photos", jpeg(), "landingUpload/hero/heroImage", jpeg(), "landingUpload/preview/designs(0)/baseImage", jpeg());
    var res = b.multipart("/admin/products/new", f, files);
    assertThat(res.statusCode()).isEqualTo(200);
    assertThat(res.body()).contains("set the advance amount", "select your photos again");
    assertThat(db.queryForObject("SELECT count(*) FROM product WHERE slug='admin-mug'", Integer.class)).isZero();
    assertThat(db.queryForObject("SELECT count(*) FROM product_image", Integer.class)).isZero();
  }

  @Test
  void onlyKnownImageFieldsCanReceiveUploads() {
    assertThat(LandingForm.fieldForUpload("landingUpload/hero/heroImage")).isEqualTo("hero.heroImage");
    assertThat(LandingForm.fieldForUpload("landingUpload/options(1)/values(2)/image")).isEqualTo("options[1].values[2].image");
    assertThat(LandingForm.fieldForUpload("landingUpload/advanceAmount")).isNull();
    assertThat(LandingForm.fieldForUpload("photos")).isNull();
    assertThatThrownBy(() -> new LandingForm().setImage("urgencyLine", "x"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aBadNumberIsExplainedNotCrashed() throws Exception {
    long id = createPublished();
    Browser b = admin();
    b.get("/admin/products/" + id);
    Map<String, String> f = new LinkedHashMap<>();
    f.put("title", "মগ"); f.put("slug", "admin-mug"); f.put("status", "DRAFT"); f.put("sortOrder", "0");
    f.put("submitted", "true"); f.put("advanceAmount", "abc");
    var res = b.post("/admin/products/" + id, f);
    assertThat(res.statusCode()).isEqualTo(200);
    assertThat(res.body()).contains("enter a whole number for advanceAmount");
  }

  @Test
  void orderDetailShowsChoicesAndServesTheSvg() throws Exception {
    createPublished();
    Browser customer = new Browser(port);
    customer.get("/order/admin-mug");
    Map<String, String> f = new LinkedHashMap<>();
    f.put("engraving", "ক্ষেত্র"); f.put("confirmEngraving", "ক্ষেত্র"); f.put("font", "Galada");
    f.put("design", "0"); f.put("packageIndex", "1"); f.put("name", "গ্রাহক");
    f.put("phone", "01712345678"); f.put("area", "INSIDE"); f.put("address", "ঠিকানা");
    f.put("fontSize", "40"); f.put("shownTotal", "710"); f.put("neededBy", "");
    f.put("submitToken", customer.submitToken); f.put("action", "submit");
    assertThat(customer.post("/order/admin-mug", f).statusCode()).isEqualTo(302);
    String code = db.queryForObject("SELECT code FROM orders", String.class);

    assertThat(new Browser(port).get("/admin/orders/" + code + "/preview.svg").statusCode())
        .as("owner only").isEqualTo(302);
    Browser b = admin();
    assertThat(b.get("/admin/orders/" + code).body()).contains("Download SVG", "Galada", "Advance ৳100");
    assertThat(b.get("/admin/products").body()).contains("/landing-preview");
    db.update("UPDATE setting SET value='01811111111' WHERE key='bkash_number'");
    db.update("UPDATE orders SET status='DESIGN_SENT', design_approved_at=now()");
    String approved = b.get("/admin/orders/" + code).body();
    assertThat(approved).contains("Open advance message", "01811111111", "Advance still due: ৳100");
    db.update("UPDATE setting SET value='' WHERE key='bkash_number'");
    assertThat(b.get("/admin/orders/" + code).body()).contains("Add your bKash number");
    var full = b.get("/admin/orders/" + code + "/preview.svg");
    assertThat(full.headers().firstValue("content-type").orElseThrow()).startsWith("image/svg+xml");
    assertThat(full.body()).contains(">ক্ষেত্র<", "<image", "font/woff2");
    var download = b.get("/admin/orders/" + code + "/preview.svg?download=true");
    assertThat(download.headers().firstValue("content-disposition").orElseThrow()).contains("attachment");
    assertThat(download.body()).contains(">ক্ষেত্র<").doesNotContain("<image");
    assertThat(download.headers().firstValue("x-content-type-options").orElseThrow()).isEqualTo("nosniff");
  }

  /** The current default wording of a site text, so tests follow copy edits in site-text.properties. */
  static String text(String key) {
    return new com.upoharlab.text.SiteTextCatalog().get(key).defaultText();
  }
}
