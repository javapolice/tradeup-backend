package com.tradeup.backend.auth.jwt;

import com.jayway.jsonpath.JsonPath;
import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.service.MemberService;
import com.tradeup.backend.product.domain.Product;
import com.tradeup.backend.product.repository.ProductRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(JwtAuthenticationIntegrationTest.AuthenticationObservation.class)
class JwtAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MemberService memberService;

    @Autowired
    private ProductRepository productRepository;

    @Value("${jwt.secret-base64}")
    private String secretBase64;

    @Test
    void 회원가입과_로그인은_JWT_없이_가능하고_로그인_JWT로_상품을_등록한다() throws Exception {
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jwt-api@test.com","nickname":"jwt-api","password":"jwt-password"}
                                """))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jwt-api@test.com","password":"jwt-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        Long memberId = jwtTokenProvider.getMemberId(token);

        MvcResult created = mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"JWT product","description":"test","price":1000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("JWT product"))
                .andReturn();

        assertThat(created.getRequest().getAttribute("observedAuthentication"))
                .isInstanceOfSatisfying(Authentication.class, authentication -> {
                    assertThat(authentication.isAuthenticated()).isTrue();
                    assertThat(authentication.getPrincipal()).isEqualTo(memberId);
                });
    }

    @Test
    void 상품_목록과_상세는_JWT_없이_조회한다() throws Exception {
        Member seller = createMember();
        Product product = productRepository.save(new Product(seller, "Public product", "test", 1000L));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + product.getId() + ")].title")
                        .value(org.hamcrest.Matchers.contains("Public product")));
        mockMvc.perform(get("/api/products/{productId}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.getId()));
    }

    @Test
    void JWT_없는_보호_조회는_거부된다() throws Exception {
        Member member = createMember();
        // SecurityConfig의 authentication entry point가 미인증 요청에 401을 반환한다.
        mockMvc.perform(get("/api/members/{memberId}", member.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void JWT_없는_보호_POST는_거부되고_상품이_저장되지_않는다() throws Exception {
        long before = productRepository.count();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Rejected product","description":"test","price":1000}
                                """))
                .andExpect(status().isUnauthorized());

        assertThat(productRepository.count()).isEqualTo(before);
    }

    @Test
    void 정상_키로_서명했어도_만료된_JWT는_401이다() throws Exception {
        Member member = createMember();
        String expired = Jwts.builder()
                .subject(member.getId().toString())
                .issuedAt(Date.from(Instant.parse("2000-01-01T00:00:00Z")))
                .expiration(Date.from(Instant.parse("2000-01-01T01:00:00Z")))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretBase64)), Jwts.SIG.HS256)
                .compact();

        assertInvalidToken(member, expired);
    }

    @Test
    void 다른_키로_위조한_JWT는_401이다() throws Exception {
        Member member = createMember();
        byte[] otherKey = new byte[32];
        new SecureRandom().nextBytes(otherKey);
        String forged = new JwtTokenProvider(Base64.getEncoder().encodeToString(otherKey), 3600)
                .generateAccessToken(member.getId());

        assertInvalidToken(member, forged);
    }

    @Test
    void payload를_변조한_JWT는_401이다() throws Exception {
        Member member = createMember();
        String[] parts = jwtTokenProvider.generateAccessToken(member.getId()).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String modified = payload.replace("\"sub\":\"" + member.getId() + "\"", "\"sub\":\"0\"");
        assertThat(modified).isNotEqualTo(payload);
        String tampered = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(modified.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        assertInvalidToken(member, tampered);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-a-jwt", "a.b.c", "a.b.c.d"})
    void 잘못된_형식의_Bearer_JWT는_401이다(String token) throws Exception {
        assertInvalidToken(createMember(), token);
    }

    @Test
    void 정상_인증_후_같은_스레드의_별도_요청에는_인증이_남지_않는다() throws Exception {
        Member member = createMember();
        MvcResult authenticated = mockMvc.perform(get("/api/members/{memberId}", member.getId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtTokenProvider.generateAccessToken(member.getId())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(authenticated.getRequest().getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        mockMvc.perform(get("/api/members/{memberId}", member.getId()))
                .andExpect(status().isUnauthorized());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private Member createMember() {
        return memberService.signUp("jwt-security@test.com", "jwt-security", "jwt-password");
    }

    private void assertInvalidToken(Member member, String token) throws Exception {
        MvcResult rejected = mockMvc.perform(get("/api/members/{memberId}", member.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat(rejected.getHandler()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /** Observes the real filter's authentication at MVC entry without changing security rules. */
    @TestConfiguration(proxyBeanMethods = false)
    static class AuthenticationObservation implements WebMvcConfigurer {
        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new HandlerInterceptor() {
                @Override
                public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                    request.setAttribute("observedAuthentication",
                            SecurityContextHolder.getContext().getAuthentication());
                    return true;
                }
            });
        }
    }
}
