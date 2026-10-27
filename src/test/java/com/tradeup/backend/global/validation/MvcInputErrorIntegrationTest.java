package com.tradeup.backend.global.validation;

import com.jayway.jsonpath.JsonPath;
import com.tradeup.backend.auth.jwt.JwtTokenProvider;
import com.tradeup.backend.global.exception.BusinessException;
import com.tradeup.backend.global.exception.ErrorCode;
import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.repository.MemberRepository;
import com.tradeup.backend.member.service.MemberService;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.repository.ProductRepository;
import com.tradeup.backend.product.service.ProductService;
import com.tradeup.backend.trade.service.TradeService;
import com.tradeup.backend.wishlist.service.WishlistService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MvcInputErrorIntegrationTest {
    private static final String BODY_DETAIL = "요청 본문이 없거나 JSON 형식 또는 자료형이 올바르지 않습니다.";
    private static final String TYPE_DETAIL = "요청 경로 또는 파라미터의 자료형이 올바르지 않습니다.";
    private static final String HASH = new BCryptPasswordEncoder().encode("input-error-password");
    private static final List<String> BODY_PATHS = List.of(
            "/api/members", "/api/auth/login", "/api/products", "/api/wishlists", "/api/trades");
    private static final List<PathEndpoint> PATHS = List.of(
            new PathEndpoint("GET", "/api/products/%s", "productId", false),
            new PathEndpoint("DELETE", "/api/products/%s", "productId", true),
            new PathEndpoint("GET", "/api/members/%s", "memberId", true),
            new PathEndpoint("GET", "/api/members/%s/wishlists", "memberId", true),
            new PathEndpoint("GET", "/api/members/%s/purchases", "memberId", true),
            new PathEndpoint("POST", "/api/trades/%s/accept", "tradeId", true),
            new PathEndpoint("POST", "/api/trades/%s/reject", "tradeId", true),
            new PathEndpoint("POST", "/api/trades/%s/complete", "tradeId", true),
            new PathEndpoint("DELETE", "/api/wishlists/%s", "wishlistId", true),
            new PathEndpoint("GET", "/api/products/%s/trades", "productId", true));

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider tokenProvider;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private EntityManager entityManager;
    @MockitoSpyBean private MemberService members;
    @MockitoSpyBean private ProductService products;
    @MockitoSpyBean private WishlistService wishlists;
    @MockitoSpyBean private TradeService trades;
    private Long authenticatedMemberId;

    @BeforeEach
    void setUp() {
        String name = "input-error-" + UUID.randomUUID();
        Member member = memberRepository.save(new Member(name + "@test.com", name, HASH));
        entityManager.flush();
        entityManager.clear();
        authenticatedMemberId = member.getId();
        clearInvocations(members, products, wishlists, trades);
    }

    @ParameterizedTest
    @MethodSource("unreadableBodies")
    void 잘못된_본문은_일반화된_400이며_Service를_호출하지_않는다(String path, String body) throws Exception {
        var builder = post(path).contentType(MediaType.APPLICATION_JSON);
        if (body != null) builder.content(body); // null means truly absent body, rather than JSON null.
        if (protectedBodyPath(path)) authenticate(builder);

        MvcResult result = assertSafe400(builder, BODY_DETAIL);
        assertThat(result.getResolvedException()).isExactlyInstanceOf(HttpMessageNotReadableException.class);
        verifyNoInteractions(members, products, wishlists, trades);
    }

    static Stream<Arguments> unreadableBodies() {
        Stream<Arguments> common = BODY_PATHS.stream().flatMap(path -> Stream.of(
                Arguments.of(path, "{\"internalDiagnostic\":\"PRIVATE_JSON_DIAGNOSTIC\","),
                Arguments.of(path, (String) null), Arguments.of(path, "null"), Arguments.of(path, "[]")));
        Stream<Arguments> fieldTypes = Stream.of(
                Arguments.of("/api/products", "{\"title\":\"valid\",\"price\":\"abc\"}"),
                Arguments.of("/api/wishlists", "{\"productId\":\"abc\"}"),
                Arguments.of("/api/trades", "{\"productId\":\"abc\"}"),
                Arguments.of("/api/products", "{\"title\":\"valid\",\"price\":9223372036854775808}"),
                Arguments.of("/api/wishlists", "{\"productId\":9223372036854775808}"),
                Arguments.of("/api/trades", "{\"productId\":9223372036854775808}"),
                Arguments.of("/api/products", ""));
        return Stream.concat(common, fieldTypes);
    }

    @ParameterizedTest
    @MethodSource("invalidPaths")
    void 모든_Long_경로의_문자열과_범위_초과는_변환_오류_400이다(PathEndpoint endpoint, String value) throws Exception {
        var builder = request(HttpMethod.valueOf(endpoint.method()), endpoint.template().formatted(value));
        if (endpoint.protectedPath()) authenticate(builder);

        MvcResult result = assertSafe400(builder, TYPE_DETAIL);
        assertThat(result.getResolvedException()).isExactlyInstanceOf(MethodArgumentTypeMismatchException.class);
        var exception = (MethodArgumentTypeMismatchException) result.getResolvedException();
        assertThat(exception.getName()).isEqualTo(endpoint.parameter());
        assertThat(exception.getRequiredType()).isEqualTo(Long.class);
        assertThat(exception.getValue()).isEqualTo(value);
        verifyNoInteractions(members, products, wishlists, trades);
    }

    static Stream<Arguments> invalidPaths() {
        return PATHS.stream().flatMap(endpoint -> Stream.of("abc", "9223372036854775808", "-9223372036854775809")
                .map(value -> Arguments.of(endpoint, value)));
    }

    @ParameterizedTest
    @MethodSource("unauthenticatedRequests")
    void 보호_API는_본문이나_경로가_잘못되어도_JWT가_없으면_기존_401이다(String method, String path, String body) throws Exception {
        var builder = request(HttpMethod.valueOf(method), path);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(body);
        MvcResult result = mockMvc.perform(builder).andExpect(status().isUnauthorized()).andReturn();
        assertThat(result.getHandler()).isNull();
        assertThat(result.getResolvedException()).isNull();
        verifyNoInteractions(members, products, wishlists, trades);
    }

    static Stream<Arguments> unauthenticatedRequests() {
        return Stream.concat(
                PATHS.stream().filter(PathEndpoint::protectedPath)
                        .map(endpoint -> Arguments.of(endpoint.method(), endpoint.template().formatted("abc"), (String) null)),
                BODY_PATHS.stream().filter(MvcInputErrorIntegrationTest::protectedBodyPath)
                        .map(path -> Arguments.of("POST", path, "{invalid")));
    }

    @Test
    void 정상_JSON과_숫자_ID는_등록과_상세_조회를_유지한다() throws Exception {
        MvcResult created = mockMvc.perform(authenticate(post("/api/products"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"safe product\",\"description\":\"description\",\"price\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerId").value(authenticatedMemberId))
                .andExpect(jsonPath("$.title").value("safe product"))
                .andExpect(jsonPath("$.price").value(1000))
                .andExpect(jsonPath("$.status").value("SELLING"))
                .andReturn();
        Number id = JsonPath.read(new String(created.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), "$.id");
        mockMvc.perform(get("/api/products/{productId}", id.longValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.longValue()))
                .andExpect(jsonPath("$.sellerId").value(authenticatedMemberId))
                .andExpect(jsonPath("$.title").value("safe product"))
                .andExpect(jsonPath("$.description").value("description"))
                .andExpect(jsonPath("$.price").value(1000));
        verify(products).createProduct(authenticatedMemberId, "safe product", "description", 1000L);
        verify(products).getProduct(id.longValue());
        verifyNoMoreInteractions(products);
        verifyNoInteractions(members, wishlists, trades);
        entityManager.flush();
        entityManager.clear();
        Product saved = productRepository.findById(id.longValue()).orElseThrow();
        assertThat(saved.getTitle()).isEqualTo("safe product");
        assertThat(saved.getDescription()).isEqualTo("description");
        assertThat(saved.getPrice()).isEqualTo(1000L);
        assertThat(saved.getSeller().getId()).isEqualTo(authenticatedMemberId);
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, Long.MAX_VALUE})
    void Long_경계값은_변환에_성공하여_Service의_404로_진행한다(long id) throws Exception {
        assertThat(productRepository.existsById(id)).isFalse();
        mockMvc.perform(get("/api/products/{productId}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("상품을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.errors").doesNotHaveJsonPath())
                .andExpect(result -> {
                    assertThat(result.getResolvedException()).isExactlyInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) result.getResolvedException()).getErrorCode()).isSameAs(ErrorCode.NOT_FOUND);
                });
        verify(products).getProduct(id);
        verifyNoMoreInteractions(products);
        verifyNoInteractions(members, wishlists, trades);
    }

    private MvcResult assertSafe400(MockHttpServletRequestBuilder builder, String detail) throws Exception {
        MvcResult result = mockMvc.perform(builder)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").isNumber())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.detail").value(detail))
                .andExpect(jsonPath("$.errors").doesNotHaveJsonPath())
                .andReturn();
        assertThat(result.getHandler()).isNotNull();
        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        Map<String, Object> payload = JsonPath.read(body, "$");
        // The current serializer omits default type=about:blank (RFC 9457 section 3.1.1).
        // Keep an exact key whitelist so arbitrary exception properties cannot leak.
        assertThat(payload).containsOnlyKeys("title", "status", "detail", "instance", "code");
        assertThat(payload.get("title")).isEqualTo("Bad Request");
        assertThat(payload.get("instance")).isEqualTo(result.getRequest().getRequestURI());
        assertThat(body).doesNotContain("java.lang", "tools.jackson", "com.fasterxml", "com.tradeup.backend",
                "HttpMessageNotReadableException", "MethodArgumentTypeMismatchException", "NumberFormatException",
                "JsonParseException", "InvalidFormatException", "PRIVATE_JSON_DIAGNOSTIC", "Failed to convert", "Cannot deserialize");
        return result;
    }

    private MockHttpServletRequestBuilder authenticate(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + tokenProvider.generateAccessToken(authenticatedMemberId));
    }
    private static boolean protectedBodyPath(String path) {
        return !path.equals("/api/members") && !path.equals("/api/auth/login");
    }
    private record PathEndpoint(String method, String template, String parameter, boolean protectedPath) {}
}
