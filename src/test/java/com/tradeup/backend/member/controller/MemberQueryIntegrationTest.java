package com.tradeup.backend.member.controller;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MemberQueryIntegrationTest {

    private static final String PASSWORD = "member-query-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    // 실제 Service 로직을 유지하면서 조회 전 권한 검사를 관찰한다.
    @MockitoSpyBean
    private MemberService memberService;

    @Test
    void A의_JWT로_A의_정보를_조회하면_정확한_공개_필드만_반환한다() throws Exception {
        Fixture fixture = fixture();
        Member memberA = fixture.memberA();

        MvcResult result = mockMvc.perform(get("/api/members/{memberId}", memberA.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberA.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$.id").value(memberA.getId()))
                .andExpect(jsonPath("$.email").value(memberA.getEmail()))
                .andExpect(jsonPath("$.nickname").value(memberA.getNickname()))
                .andExpect(jsonPath("$.password").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.passwordHash").doesNotHaveJsonPath())
                .andReturn();

        String json = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(json).doesNotContain(PASSWORD, memberA.getPasswordHash(),
                fixture.memberB().getEmail(), fixture.memberB().getNickname());
        verify(memberService).getMember(memberA.getId());
        verifyNoMoreInteractions(memberService);
    }

    @Test
    void A의_JWT로_B의_정보를_조회하면_Service_호출_전에_거부된다() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> mockMvc.perform(get("/api/members/{memberId}", fixture.memberB().getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.memberA().getId()))))
                .isInstanceOf(ServletException.class)
                .cause()
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("본인의 회원 정보만 조회할 수 있습니다.");

        verifyNoInteractions(memberService);
    }

    @Test
    void JWT_없는_회원_조회는_401이고_Service를_호출하지_않는다() throws Exception {
        Fixture fixture = fixture();

        MvcResult result = mockMvc.perform(get("/api/members/{memberId}", fixture.memberA().getId()))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        verifyNoInteractions(memberService);
    }

    @Test
    void JWT_없는_회원가입은_공개이며_비밀번호를_해시해_저장하고_응답에는_노출하지_않는다() throws Exception {
        String email = "member-query-signup@test.com";
        String nickname = "member-query-signup";
        long before = memberRepository.count();

        MvcResult result = mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","nickname":"%s","password":"%s"}
                                """.formatted(email, nickname, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.nickname").value(nickname))
                .andExpect(jsonPath("$.password").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.passwordHash").doesNotHaveJsonPath())
                .andReturn();

        entityManager.flush();
        entityManager.clear();
        Member saved = memberRepository.findByEmail(email).orElseThrow();
        assertThat(memberRepository.count()).isEqualTo(before + 1);
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
        String json = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(json).doesNotContain(PASSWORD, saved.getPasswordHash());
        verify(memberService).signUp(email, nickname, PASSWORD);
        verifyNoMoreInteractions(memberService);
    }

    private Fixture fixture() {
        Member memberA = memberService.signUp("member-query-a@test.com", "member-query-a", PASSWORD);
        Member memberB = memberService.signUp("member-query-b@test.com", "member-query-b", PASSWORD);
        entityManager.flush();
        entityManager.clear();
        clearInvocations(memberService);
        return new Fixture(memberA, memberB);
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(memberId);
    }

    private record Fixture(Member memberA, Member memberB) {}
}
