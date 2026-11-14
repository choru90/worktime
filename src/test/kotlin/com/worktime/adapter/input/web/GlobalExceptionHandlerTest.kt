package com.worktime.adapter.input.web

import com.worktime.domain.exception.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.core.MethodParameter

class GlobalExceptionHandlerTest {
    private val handler=GlobalExceptionHandler()
    private val request=ServletWebRequest(MockHttpServletRequest("GET","/api/test"))
    @Test fun `domain exceptions preserve HTTP status message and resource path`() {
        val responses=listOf(
            handler.handleResourceNotFoundException(ResourceNotFoundException("missing"),request) to (404 to "missing"),
            handler.handleBusinessException(BusinessException("rule"),request) to (400 to "rule"),
            handler.handleConflictException(WorkLogConflictException("conflict"),request) to (409 to "conflict"),
            handler.handleInvalidCredentialsException(InvalidCredentialsException("credentials"),request) to (401 to "credentials"),
            handler.handleEmailAlreadyExistsException(EmailAlreadyExistsException("taken@test"),request) to (409 to "이미 사용 중인 이메일입니다: taken@test"),
            handler.handleInvalidStateException(InvalidWorkLogStateException("state"),request) to (400 to "state")
        )
        for((response,expected) in responses) {
            assertEquals(expected.first,response.statusCode.value());assertEquals(expected.first,response.body!!.status)
            assertEquals(expected.second,response.body!!.message);assertEquals("/api/test",response.body!!.path)
        }
        assertEquals(403,handler.handleAccessDenied().statusCode.value())
        assertEquals("Access denied",handler.handleAccessDenied().body!!.message)
    }
    @Test fun `generic exceptions use fallback only for absent message`() {
        assertEquals("failure",handler.handleGenericException(Exception("failure"),request).body!!.message)
        val response=handler.handleGenericException(Exception(),request)
        assertEquals(500,response.statusCode.value());assertEquals("An unexpected error occurred",response.body!!.message)
    }
    fun endpoint(value: String) = value
    @Test fun `validation errors render both field and object errors including missing messages`() {
        val binding=BeanPropertyBindingResult(Any(),"request")
        binding.addError(FieldError("request","email","invalid email"))
        binding.addError(ObjectError("request",null as String?))
        val method=javaClass.getMethod("endpoint",String::class.java)
        val response=handler.handleValidationException(MethodArgumentNotValidException(MethodParameter(method,0),binding),request)
        assertEquals(400,response.statusCode.value());assertEquals("Validation Failed",response.body!!.error)
        assertEquals("email: invalid email, field: invalid",response.body!!.message)
    }
}
