package com.worktime.auth.security

import com.worktime.application.port.output.UserRepository
import com.worktime.domain.model.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UsernameNotFoundException
import jakarta.servlet.FilterChain
import java.util.UUID

class AuthenticationBoundaryTest {
    private val secret="synthetic-auth-security-test-secret-at-least-32-bytes"
    @AfterEach fun clear()=SecurityContextHolder.clearContext()
    @Test fun `JWT rejects expired malformed and other-key tokens`() {
        val provider=JwtTokenProvider(secret,3600000);val id=UUID.randomUUID()
        for(role in UserRole.values()) {
            val token=provider.generateToken(id,role)
            assertTrue(provider.validateToken(token));assertEquals(id,provider.getUserIdFromToken(token));assertEquals(role,provider.getRoleFromToken(token))
            assertFalse(JwtTokenProvider("other-synthetic-secret-with-at-least-32-bytes",3600000).validateToken(token))
        }
        assertFalse(provider.validateToken(JwtTokenProvider(secret,-10000).generateToken(id,UserRole.OWNER)))
        for(invalid in listOf("", "not-a-token", "a.b.c")) assertFalse(provider.validateToken(invalid))
    }
    @Test fun `filter continues without authentication for missing wrong-scheme empty and invalid tokens`() {
        val filter=JwtAuthenticationFilter(JwtTokenProvider(secret,3600000))
        for(header in listOf(null,"Basic abc","Bearer ","Bearer malformed")) {
            val request=MockHttpServletRequest();if(header!=null)request.addHeader("Authorization",header)
            val response=MockHttpServletResponse();val chain=mock<FilterChain>()
            filter.doFilter(request,response,chain)
            assertNull(SecurityContextHolder.getContext().authentication)
            verify(chain).doFilter(request,response)
        }
    }
    @Test fun `filter authenticates real token with role and request details`() {
        val provider=JwtTokenProvider(secret,3600000);val id=UUID.randomUUID()
        val request=MockHttpServletRequest();request.addHeader("Authorization","Bearer ${provider.generateToken(id,UserRole.WORKER)}")
        val response=MockHttpServletResponse();val chain=mock<FilterChain>()
        JwtAuthenticationFilter(provider).doFilter(request,response,chain)
        val auth=SecurityContextHolder.getContext().authentication
        assertEquals(id.toString(),auth.name);assertEquals(listOf("ROLE_WORKER"),auth.authorities.map { it.authority })
        assertNotNull(auth.details);assertTrue(auth.isAuthenticated);verify(chain).doFilter(request,response)
    }
    @Test fun `valid signature with invalid UUID is rejected without interrupting filter chain`() {
        val key=io.jsonwebtoken.security.Keys.hmacShaKeyFor(secret.toByteArray())
        val token=io.jsonwebtoken.Jwts.builder().subject("invalid-uuid").claim("role","OWNER").signWith(key).compact()
        val request=MockHttpServletRequest();request.addHeader("Authorization","Bearer $token")
        val response=MockHttpServletResponse();val chain=mock<FilterChain>()
        JwtAuthenticationFilter(JwtTokenProvider(secret,3600000)).doFilter(request,response,chain)
        assertNull(SecurityContextHolder.getContext().authentication);verify(chain).doFilter(request,response)
    }
    @Test fun `details service exposes actual user identity and rejects missing email`() {
        val users=mock<UserRepository>();val service=CustomUserDetailsService(users)
        assertThrows<UsernameNotFoundException> { service.loadUserByUsername("missing@test") }
        val user=User(email="owner@test",password="encoded",name="Owner",role=UserRole.OWNER)
        whenever(users.findByEmail(user.email)).thenReturn(user)
        val details=service.loadUserByUsername(user.email) as CustomUserDetails
        assertSame(user,details.getUser());assertEquals(user.id,details.getUserId());assertEquals(user.role,details.getRole())
        assertEquals(user.email,details.username);assertEquals(user.password,details.password)
        assertEquals(listOf("ROLE_OWNER"),details.authorities.map { it.authority })
        assertTrue(details.isAccountNonExpired);assertTrue(details.isAccountNonLocked);assertTrue(details.isCredentialsNonExpired);assertTrue(details.isEnabled)
    }
}
