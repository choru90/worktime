package com.worktime.application.service

import com.worktime.application.port.output.*
import com.worktime.adapter.input.web.dto.auth.*
import com.worktime.auth.security.JwtTokenProvider
import com.worktime.domain.model.*
import com.worktime.domain.exception.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.OffsetDateTime
import java.util.UUID

class AuthServiceBoundaryTest {
    private val users=mock<UserRepository>()
    private val owners=mock<OwnerRepository>()
    private val workers=mock<WorkerRepository>()
    private val encoder=BCryptPasswordEncoder(4)
    private val jwt=JwtTokenProvider("synthetic-unit-test-secret-at-least-32-bytes",3600000)
    private val service=AuthService(users,owners,workers,encoder,jwt)
    private val ownerUser=User(email="owner@test",password=encoder.encode("password"),name="Owner",role=UserRole.OWNER)
    private val owner=Owner(user=ownerUser)
    private val worker=Worker(name="Worker",phoneNumber="000",owner=owner,inviteCode="ABC234",inviteCodeExpiresAt=OffsetDateTime.now().plusHours(1))
    private val request=WorkerSignUpRequest("abc234","new@test","password")
    private fun saves() {
        whenever(users.save(any())).thenAnswer { it.arguments[0] as User }
        whenever(owners.save(any())).thenAnswer { it.arguments[0] as Owner }
        whenever(workers.save(any())).thenAnswer { it.arguments[0] as Worker }
    }
    private fun assertToken(response: LoginResponse) {
        assertTrue(jwt.validateToken(response.accessToken))
        assertEquals(response.userId,jwt.getUserIdFromToken(response.accessToken))
        assertEquals(response.role,jwt.getRoleFromToken(response.accessToken))
    }
    @Test fun `owner signup persists encoded password and business profile`() {
        saves()
        val response=service.signUpOwner(OwnerSignUpRequest("new@test","password","New owner","Cafe"))
        val user=argumentCaptor<User>();verify(users).save(user.capture())
        assertEquals("new@test",user.firstValue.email);assertEquals("New owner",user.firstValue.name)
        assertTrue(encoder.matches("password",user.firstValue.password));assertNotEquals("password",user.firstValue.password)
        assertEquals(UserRole.OWNER,response.role);assertEquals(user.firstValue.id,response.userId);assertNull(response.workerId)
        val profile=argumentCaptor<Owner>();verify(owners).save(profile.capture())
        assertSame(user.firstValue,profile.firstValue.user);assertEquals("Cafe",profile.firstValue.businessName)
        assertEquals(profile.firstValue.id,response.ownerId);assertToken(response)
    }
    @Test fun `duplicate email rejects every signup without saving`() {
        whenever(users.existsByEmail("new@test")).thenReturn(true)
        whenever(workers.findByInviteCode("ABC234")).thenReturn(worker)
        assertThrows<EmailAlreadyExistsException> { service.signUp(SignUpRequest("new@test","password","New",UserRole.OWNER)) }
        assertThrows<EmailAlreadyExistsException> { service.signUpOwner(OwnerSignUpRequest("new@test","password","New")) }
        assertThrows<EmailAlreadyExistsException> { service.signUpWorker(request) }
        verify(users,never()).save(any());verify(owners,never()).save(any());verify(workers,never()).save(any())
        assertNull(worker.userAccount);assertEquals("ABC234",worker.inviteCode)
    }
    @Test fun `generic signup returns optional worker identifier and signed token`() {
        saves();whenever(workers.findById(worker.id)).thenReturn(worker)
        val without=service.signUp(SignUpRequest("new@test","password","New",UserRole.OWNER))
        assertNull(without.workerId);assertNull(without.ownerId);assertToken(without)
        val with=service.signUp(SignUpRequest("other@test","password","New",UserRole.WORKER,worker.id))
        assertEquals(worker.id,with.workerId);assertEquals(UserRole.WORKER,with.role);assertToken(with)
        assertThrows<ResourceNotFoundException> { service.signUp(SignUpRequest("missing@test","password","New",UserRole.WORKER,UUID.randomUUID())) }
    }
    @Test fun `worker signup uppercases invite and consumes it when linking account`() {
        saves();whenever(workers.findByInviteCode("ABC234")).thenReturn(worker)
        val response=service.signUpWorker(request)
        val saved=argumentCaptor<User>();verify(users).save(saved.capture())
        assertEquals(worker.name,saved.firstValue.name);assertEquals("new@test",saved.firstValue.email)
        assertTrue(encoder.matches("password",saved.firstValue.password))
        assertSame(saved.firstValue,worker.userAccount);assertNull(worker.inviteCode);assertNull(worker.inviteCodeExpiresAt)
        verify(workers).lockForWrite(worker);verify(workers).save(worker)
        assertEquals(worker.id,response.workerId);assertEquals(UserRole.WORKER,response.role);assertNull(response.ownerId);assertToken(response)
    }
    @Test fun `invalid expired or already linked invitation never creates account`() {
        assertThrows<ResourceNotFoundException> { service.signUpWorker(request) }
        whenever(workers.findByInviteCode("ABC234")).thenReturn(worker)
        worker.inviteCodeExpiresAt=OffsetDateTime.now().minusSeconds(1)
        assertTrue(assertThrows<BusinessException> { service.signUpWorker(request) }.message!!.contains("만료"))
        worker.inviteCodeExpiresAt=OffsetDateTime.now().plusHours(1);worker.userAccount=ownerUser
        assertTrue(assertThrows<BusinessException> { service.signUpWorker(request) }.message!!.contains("이미"))
        verify(users,never()).save(any());verify(workers,never()).save(any())
    }
    @Test fun `login rejects nonexistent user and mismatched password identically`() {
        val missing=assertThrows<InvalidCredentialsException> { service.login(LoginRequest("missing@test","password")) }
        whenever(users.findByEmail(ownerUser.email)).thenReturn(ownerUser)
        val wrong=assertThrows<InvalidCredentialsException> { service.login(LoginRequest(ownerUser.email,"wrong")) }
        assertEquals(missing.message,wrong.message);verifyNoInteractions(owners,workers)
    }
    @Test fun `login returns only profile associated with actual role and handles missing profile`() {
        whenever(users.findByEmail(ownerUser.email)).thenReturn(ownerUser)
        val noOwner=service.login(LoginRequest(ownerUser.email,"password"));assertNull(noOwner.ownerId);assertNull(noOwner.workerId);assertToken(noOwner)
        whenever(owners.findByUserId(ownerUser.id)).thenReturn(owner)
        val ownerLogin=service.login(LoginRequest(ownerUser.email,"password"));assertEquals(owner.id,ownerLogin.ownerId);assertNull(ownerLogin.workerId);assertToken(ownerLogin)
        val employee=User(email="employee@test",password=encoder.encode("password"),name="Employee",role=UserRole.WORKER)
        whenever(users.findByEmail(employee.email)).thenReturn(employee)
        val noWorker=service.login(LoginRequest(employee.email,"password"));assertNull(noWorker.workerId);assertNull(noWorker.ownerId);assertToken(noWorker)
        whenever(workers.findByUserAccountId(employee.id)).thenReturn(worker)
        val workerLogin=service.login(LoginRequest(employee.email,"password"));assertEquals(worker.id,workerLogin.workerId);assertNull(workerLogin.ownerId);assertToken(workerLogin)
    }
}
