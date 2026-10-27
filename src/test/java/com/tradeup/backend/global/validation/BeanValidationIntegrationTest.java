package com.tradeup.backend.global.validation;

import com.tradeup.backend.auth.jwt.JwtTokenProvider;
import com.tradeup.backend.global.exception.BusinessException;
import com.tradeup.backend.global.exception.ErrorCode;
import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.repository.MemberRepository;
import com.tradeup.backend.member.service.MemberService;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.domain.ProductStatus;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BeanValidationIntegrationTest {
    private static final String SIGNUP = "/api/members";
    private static final String LOGIN = "/api/auth/login";
    private static final String PRODUCT = "/api/products";
    private static final String WISHLIST = "/api/wishlists";
    private static final String TRADE = "/api/trades";
    private static final String BLANK = "must not be blank";
    private static final String EMAIL = "must be a well-formed email address";
    private static final String REQUIRED = "must not be null";
    private static final String SIZE = "size must be between 0 and 255";
    private static final String POSITIVE = "must be greater than 0";
    private static final String NON_NEGATIVE = "must be greater than or equal to 0";
    private static final String HASH = new BCryptPasswordEncoder().encode("validation-password");

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
        String name = "validation-" + UUID.randomUUID();
        Member member = memberRepository.save(new Member(name + "@test.com", name, HASH));
        entityManager.flush();
        entityManager.clear();
        authenticatedMemberId = member.getId();
        clearInvocations(members, products, wishlists, trades);
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void 유효하지_않은_요청은_정확한_필드_오류를_반환하고_Service를_호출하지_않는다(
            String path, String body, String field, String message
    ) throws Exception {
        assertValidation(path, body, List.of(new FieldFailure(field, message)));
        verifyNoInteractions(members, products, wishlists, trades);
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of(SIGNUP, signup("\"invalid-email\"", "\"nickname\"", "\"password\""), "email", EMAIL),
                Arguments.of(SIGNUP, signup("\"\"", "\"nickname\"", "\"password\""), "email", BLANK),
                Arguments.of(SIGNUP, signup("null", "\"nickname\"", "\"password\""), "email", BLANK),
                Arguments.of(SIGNUP, signup("\"valid@test.com\"", "\"\"", "\"password\""), "nickname", BLANK),
                Arguments.of(SIGNUP, signup("\"valid@test.com\"", "null", "\"password\""), "nickname", BLANK),
                Arguments.of(SIGNUP, signup("\"valid@test.com\"", "\"   \"", "\"password\""), "nickname", BLANK),
                Arguments.of(SIGNUP, signup("\"valid@test.com\"", "\"nickname\"", "\"\""), "password", BLANK),
                Arguments.of(SIGNUP, signup("\"valid@test.com\"", "\"nickname\"", "null"), "password", BLANK),
                Arguments.of(SIGNUP, signup("\"valid@test.com\"", "\"nickname\"", "\"   \""), "password", BLANK),
                Arguments.of(SIGNUP, signup("\"valid@test.com\"", "\"" + "n".repeat(256) + "\"", "\"password\""), "nickname", SIZE),
                Arguments.of(LOGIN, "{\"email\":\"\",\"password\":\"password\"}", "email", BLANK),
                Arguments.of(LOGIN, "{\"email\":null,\"password\":\"password\"}", "email", BLANK),
                Arguments.of(LOGIN, "{\"email\":\"   \",\"password\":\"password\"}", "email", BLANK),
                Arguments.of(LOGIN, "{\"email\":\"valid@test.com\",\"password\":\"\"}", "password", BLANK),
                Arguments.of(LOGIN, "{\"email\":\"valid@test.com\",\"password\":null}", "password", BLANK),
                Arguments.of(LOGIN, "{\"email\":\"valid@test.com\",\"password\":\"   \"}", "password", BLANK),
                Arguments.of(PRODUCT, "{\"title\":\"\",\"price\":1}", "title", BLANK),
                Arguments.of(PRODUCT, "{\"title\":null,\"price\":1}", "title", BLANK),
                Arguments.of(PRODUCT, "{\"title\":\"   \",\"price\":1}", "title", BLANK),
                Arguments.of(PRODUCT, "{\"title\":\"valid\",\"price\":-1}", "price", NON_NEGATIVE),
                Arguments.of(PRODUCT, "{\"title\":\"valid\",\"price\":null}", "price", REQUIRED),
                Arguments.of(PRODUCT, "{\"title\":\"valid\"}", "price", REQUIRED),
                Arguments.of(PRODUCT, "{\"title\":\"" + "t".repeat(256) + "\",\"price\":1}", "title", SIZE),
                Arguments.of(PRODUCT, "{\"title\":\"valid\",\"description\":\"" + "d".repeat(256) + "\",\"price\":1}", "description", SIZE),
                Arguments.of(WISHLIST, "{\"productId\":null}", "productId", REQUIRED),
                Arguments.of(WISHLIST, "{}", "productId", REQUIRED),
                Arguments.of(WISHLIST, "{\"productId\":0}", "productId", POSITIVE),
                Arguments.of(WISHLIST, "{\"productId\":-1}", "productId", POSITIVE),
                Arguments.of(TRADE, "{\"productId\":null}", "productId", REQUIRED),
                Arguments.of(TRADE, "{}", "productId", REQUIRED),
                Arguments.of(TRADE, "{\"productId\":0}", "productId", POSITIVE),
                Arguments.of(TRADE, "{\"productId\":-1}", "productId", POSITIVE));
    }

    @ParameterizedTest
    @MethodSource("multipleFailures")
    void 여러_오류는_필드명과_동일_필드의_제약_코드_순서로_정렬된다(
            String path, String body, List<FieldFailure> expected
    ) throws Exception {
        // Expected order is explicit; neither the expected nor actual errors are sorted by this test.
        assertValidation(path, body, expected);
        verifyNoInteractions(members, products, wishlists, trades);
    }

    static Stream<Arguments> multipleFailures() {
        return Stream.of(
                Arguments.of(SIGNUP, signup("\"\"", "\"\"", "\"\""), List.of(
                        new FieldFailure("email", BLANK), new FieldFailure("nickname", BLANK), new FieldFailure("password", BLANK))),
                Arguments.of(SIGNUP, signup("\" \"", "\"\"", "\"\""), List.of(
                        new FieldFailure("email", EMAIL), new FieldFailure("email", BLANK),
                        new FieldFailure("nickname", BLANK), new FieldFailure("password", BLANK))),
                Arguments.of(LOGIN, "{\"password\":\"\",\"email\":\"\"}", List.of(
                        new FieldFailure("email", BLANK), new FieldFailure("password", BLANK))),
                Arguments.of(PRODUCT, "{\"title\":\" \",\"price\":-1,\"description\":\"" + "d".repeat(256) + "\"}", List.of(
                        new FieldFailure("description", SIZE), new FieldFailure("price", NON_NEGATIVE), new FieldFailure("title", BLANK))));
    }

    @ParameterizedTest
    @MethodSource("allowedDescriptions")
    void 제목_최대길이와_0원_가격과_선택_설명은_허용되고_DB에_저장된다(String description) throws Exception {
        String title = "t".repeat(255);
        String descriptionJson = description == null ? "null" : "\"" + description + "\"";
        long before = productRepository.count();
        mockMvc.perform(request(PRODUCT, "{\"title\":\"" + title + "\",\"description\":" + descriptionJson + ",\"price\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerId").value(authenticatedMemberId))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.price").value(0))
                .andExpect(jsonPath("$.status").value("SELLING"));

        verify(products).createProduct(authenticatedMemberId, title, description, 0L);
        verifyNoMoreInteractions(products);
        verifyNoInteractions(members, wishlists, trades);
        entityManager.flush();
        entityManager.clear();
        assertThat(productRepository.count()).isEqualTo(before + 1);
        Product saved = productRepository.findAll().stream()
                .filter(product -> product.getSeller().getId().equals(authenticatedMemberId)).findFirst().orElseThrow();
        assertThat(saved.getTitle()).isEqualTo(title);
        assertThat(saved.getDescription()).isEqualTo(description);
        assertThat(saved.getPrice()).isEqualTo(0L);
        assertThat(saved.getStatus()).isEqualTo(ProductStatus.SELLING);
    }

    static Stream<String> allowedDescriptions() { return Stream.of(null, "", "d".repeat(255)); }

    @Test
    void 로그인은_비어있지_않은_이메일을_Service에_전달하고_인증_실패는_401을_유지한다() throws Exception {
        String email = "not-an-email"; // LoginRequest intentionally has no @Email.
        assertThat(memberRepository.findByEmail(email)).isEmpty();
        mockMvc.perform(request(LOGIN, "{\"email\":\"not-an-email\",\"password\":\"password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.detail").value("이메일 또는 비밀번호가 올바르지 않습니다."))
                .andExpect(jsonPath("$.errors").doesNotHaveJsonPath())
                .andExpect(result -> {
                    assertThat(result.getResolvedException()).isExactlyInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) result.getResolvedException()).getErrorCode()).isSameAs(ErrorCode.UNAUTHORIZED);
                });
        verify(members).login(email, "password");
        verifyNoMoreInteractions(members);
        verifyNoInteractions(products, wishlists, trades);
    }

    private void assertValidation(String path, String body, List<FieldFailure> expected) throws Exception {
        var result = mockMvc.perform(request(path, body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.detail").value("요청 값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors.length()").value(expected.size()));
        for (int index = 0; index < expected.size(); index++) {
            FieldFailure failure = expected.get(index);
            result.andExpect(jsonPath("$.errors[" + index + "].field").value(failure.field()))
                    .andExpect(jsonPath("$.errors[" + index + "].message").value(failure.message()))
                    .andExpect(jsonPath("$.errors[" + index + "].length()").value(2));
        }
        MvcResult response = result.andReturn();
        assertThat(response.getResolvedException()).isExactlyInstanceOf(MethodArgumentNotValidException.class);
    }

    private MockHttpServletRequestBuilder request(String path, String body) {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(body).locale(Locale.ENGLISH);
        if (!path.equals(SIGNUP) && !path.equals(LOGIN)) {
            request.header("Authorization", "Bearer " + tokenProvider.generateAccessToken(authenticatedMemberId));
        }
        return request;
    }

    private static String signup(String emailJson, String nicknameJson, String passwordJson) {
        return "{\"email\":%s,\"nickname\":%s,\"password\":%s}".formatted(emailJson, nicknameJson, passwordJson);
    }
    private record FieldFailure(String field, String message) {}
}
