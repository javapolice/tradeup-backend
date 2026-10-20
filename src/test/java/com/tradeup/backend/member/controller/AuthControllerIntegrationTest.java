package com.tradeup.backend.member.controller;

import com.jayway.jsonpath.JsonPath;
import com.tradeup.backend.auth.jwt.JwtTokenProvider;
import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.repository.MemberRepository;
import com.tradeup.backend.member.service.MemberService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerIntegrationTest {

    private static final String LOGIN_FAILURE_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberService memberService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 실제_회원가입으로_저장된_BCrypt_회원은_로그인하고_회원정보_없이_유효한_JWT를_받는다() throws Exception {
        Member member = signUpThroughApi();

        assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
        assertThat(member.getPasswordHash()).isNotEqualTo("login-password");
        assertThat(member.getPasswordHash()).matches("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");
        assertThat(passwordEncoder.matches("login-password", member.getPasswordHash())).isTrue();

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(member.getEmail(), "login-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.id").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.email").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.nickname").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.password").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.passwordHash").doesNotHaveJsonPath())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("login-password", member.getPasswordHash());
        String accessToken = JsonPath.read(response, "$.accessToken");
        assertThat(jwtTokenProvider.getMemberId(accessToken)).isEqualTo(member.getId());
    }

    @Test
    void 틀린_비밀번호는_서비스_예외와_메시지가_MockMvc까지_전파된다() throws Exception {
        Member member = signUpThroughApi();

        assertLoginFailure(member.getEmail(), "wrong-password");
    }

    @Test
    void 없는_이메일은_서비스_예외와_메시지가_MockMvc까지_전파된다() {
        String email = "api-login-missing@test.com";
        assertThat(memberRepository.findByEmail(email)).isEmpty();

        assertLoginFailure(email, "login-password");
    }

    @Test
    void NULL_해시_기존_회원은_서비스_예외와_메시지가_MockMvc까지_전파된다() {
        String email = "api-login-legacy@test.com";
        // 신규 Member 생성자는 NULL을 금지하므로 기존 DB 회원을 직접 생성한다.
        entityManager.createNativeQuery("INSERT INTO members (email, nickname) VALUES (:email, :nickname)")
                .setParameter("email", email)
                .setParameter("nickname", "api-login-legacy")
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        Member member = memberRepository.findByEmail(email).orElseThrow();
        assertThat(member.getPasswordHash()).isNull();

        assertLoginFailure(email, "login-password");
    }

    private Member signUpThroughApi() throws Exception {
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "api-login@test.com",
                                    "nickname": "api-login",
                                    "password": "login-password"
                                }
                                """))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        return memberRepository.findByEmail("api-login@test.com").orElseThrow();
    }

    private void assertLoginFailure(String email, String password) {
        assertThatThrownBy(() -> memberService.login(email, password))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(LOGIN_FAILURE_MESSAGE);

        // 전역 예외 처리기가 없어 HTTP 401 대신 ServletException의 원인 예외를 검증한다.
        assertThatThrownBy(() -> mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password))))
                .isInstanceOf(ServletException.class)
                .cause()
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(LOGIN_FAILURE_MESSAGE);
    }

    private String loginBody(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }
}
