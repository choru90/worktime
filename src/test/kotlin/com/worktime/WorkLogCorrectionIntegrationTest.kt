package com.worktime
import com.fasterxml.jackson.databind.ObjectMapper
import com.worktime.auth.security.JwtTokenProvider
import com.worktime.domain.model.*
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.transaction.support.TransactionTemplate
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.*
@SpringBootTest(properties=["spring.datasource.url=jdbc:h2:mem:wtcorrection;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false","spring.jpa.show-sql=false","logging.level.org.hibernate.SQL=WARN","jwt.secret=synthetic-correction-test-key-with-at-least-32-bytes","jwt.expiration=3600000"])
@AutoConfigureMockMvc
class WorkLogCorrectionIntegrationTest {
 companion object {@JvmStatic @DynamicPropertySource fun mysql(registry:DynamicPropertyRegistry){ConcurrentScheduleIntegrationTest.mysql(registry)}}
 @Autowired lateinit var em:EntityManager
 @Autowired lateinit var tx:TransactionTemplate
 @Autowired lateinit var mvc:MockMvc
 @Autowired lateinit var json:ObjectMapper
 @Autowired lateinit var jwt:JwtTokenProvider
 private val start=OffsetDateTime.parse("2026-01-01T09:00:00Z")
 data class Fixture(val id:UUID,val worker:UUID,val token:String,val owner:String)
 private fun fixture(state:WorkLogStatus=WorkLogStatus.ONGOING,overlap:Boolean=false):Fixture=tx.execute {
  fun user(role:UserRole)=User(email="correction-${UUID.randomUUID()}@example.invalid",password="synthetic",name="Synthetic",role=role).also{em.persist(it)}
  val ownerUser=user(UserRole.OWNER);val owner=Owner(user=ownerUser).also{em.persist(it)}
  val account=user(UserRole.WORKER);val worker=Worker(name="Synthetic",phoneNumber="00000000000",owner=owner,userAccount=account).also{em.persist(it)}
  val log=WorkLog(worker=worker,clockInAt=start,clockOutAt=if(state==WorkLogStatus.ONGOING)null else start.plusHours(4),status=state).also{em.persist(it)}
  if(overlap)em.persist(WorkLog(worker=worker,clockInAt=start.minusDays(2),clockOutAt=start.plusDays(2),status=WorkLogStatus.CONFIRMED))
  Fixture(log.id,worker.id,"Bearer ${jwt.generateToken(account.id,account.role)}","Bearer ${jwt.generateToken(ownerUser.id,ownerUser.role)}")
 }!!
 private fun correct(f:Fixture,state:WorkLogStatus=WorkLogStatus.ONGOING,revision:Int=0,end:OffsetDateTime=start.plusHours(5),reason:String="합성 퇴근 누락")=mvc.perform(post("/api/worklogs/my/${f.id}/corrections").header("Authorization",f.token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(mapOf("clockInAt" to start.toString(),"clockOutAt" to end.toString(),"reason" to reason,"expectedStatus" to state.name,"expectedCorrectionCount" to revision)))).andReturn().response
 @Test fun `missing checkout becomes pending approval and retains immutable history`() {
  val f=fixture();assertEquals(200,correct(f).status)
  tx.execute {val log=em.find(WorkLog::class.java,f.id);assertEquals(WorkLogStatus.COMPLETED,log.status);assertEquals(1,log.correctionCount);assertNull(log.corrections.single().previousOut)}
  assertEquals(409,correct(f).status)
  assertEquals(200,mvc.perform(post("/api/work-logs/${f.id}/confirm").header("Authorization",f.owner)).andReturn().response.status)
  assertEquals(400,correct(f,WorkLogStatus.CONFIRMED,1).status)
 }
 @Test fun `rejected record can resubmit but foreign worker and owner cannot correct`() {
  val f=fixture(WorkLogStatus.REJECTED);val foreign=fixture()
  assertEquals(404,correct(f.copy(token=foreign.token),WorkLogStatus.REJECTED).status)
  assertEquals(403,correct(f.copy(token=f.owner),WorkLogStatus.REJECTED).status)
  assertEquals(200,correct(f,WorkLogStatus.REJECTED).status)
 }
 @Test fun `invalid future blank and containing overlap are rejected without audit writes`() {
  val f=fixture();assertEquals(400,correct(f,end=start).status);assertEquals(400,correct(f,end=OffsetDateTime.now().plusDays(1)).status);assertEquals(400,correct(f,reason=" ").status)
  val overlapping=fixture(overlap=true);assertEquals(409,correct(overlapping).status)
  tx.execute {assertEquals(0,em.find(WorkLog::class.java,f.id).correctionCount)}
 }
 @Test fun `two identical corrections commit once`() {
  val f=fixture();val gate=CountDownLatch(1);val pool=Executors.newFixedThreadPool(2)
  try{val jobs=(1..2).map{pool.submit<Int>{gate.await();correct(f).status}};gate.countDown();assertEquals(listOf(200,409),jobs.map{it.get(20,TimeUnit.SECONDS)}.sorted())}finally{pool.shutdownNow()}
 }
 @Test fun `approval and correction serialize and stale edit cannot change confirmed time`() {
  val f=fixture(WorkLogStatus.COMPLETED);val gate=CountDownLatch(1);val pool=Executors.newFixedThreadPool(2)
  try{
   val edit=pool.submit<Int>{gate.await();correct(f,WorkLogStatus.COMPLETED).status}
   val approve=pool.submit<Int>{gate.await();mvc.perform(post("/api/work-logs/${f.id}/confirm").header("Authorization",f.owner)).andReturn().response.status}
   gate.countDown();assertEquals(200,approve.get(20,TimeUnit.SECONDS));assertTrue(edit.get(20,TimeUnit.SECONDS) in listOf(200,409))
   val before=tx.execute{em.find(WorkLog::class.java,f.id).clockOutAt}
   assertEquals(409,correct(f,WorkLogStatus.COMPLETED).status)
   tx.execute{assertEquals(before,em.find(WorkLog::class.java,f.id).clockOutAt)}
  }finally{pool.shutdownNow()}
 }
}
