package com.tradeup.backend.member.controller;

import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.member.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MemberControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 회원가입과_조회_응답에는_비밀번호와_해시가_노출되지_않는다() throws Exception {
        String response = mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "api-signup@test.com",
                                    "nickname": "api-signup",
                                    "password": "api-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("api-signup@test.com"))
                .andExpect(jsonPath("$.nickname").value("api-signup"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        entityManager.flush();
        entityManager.clear();
        Member member = memberRepository.findAll().stream()
                .filter(candidate -> candidate.getEmail().equals("api-signup@test.com"))
                .findFirst().orElseThrow();
        assertThat(passwordEncoder.matches("api-password", member.getPasswordHash())).isTrue();
        assertThat(response).doesNotContain("api-password", member.getPasswordHash());

        String getResponse = mockMvc.perform(get("/api/members/{memberId}", member.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$.id").value(member.getId()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(getResponse).doesNotContain("api-password", member.getPasswordHash());
    }
}
