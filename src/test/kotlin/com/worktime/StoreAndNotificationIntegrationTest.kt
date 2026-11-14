package com.worktime

import com.fasterxml.jackson.databind.ObjectMapper
import com.worktime.application.service.*
import com.worktime.auth.security.JwtTokenProvider
import com.worktime.domain.model.*
import com.worktime.domain.exception.*
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.*
import java.util.UUID

@SpringBootTest(properties=[
    "spring.datasource.url=jdbc:h2:mem:wt001_store_notification;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false","spring.jpa.show-sql=false","logging.level.org.hibernate.SQL=WARN",
    "jwt.secret=synthetic-store-test-key-with-at-least-32-bytes","jwt.expiration=3600000"
])
@AutoConfigureMockMvc
@Transactional
class StoreAndNotificationIntegrationTest {
    @Autowired lateinit var em: EntityManager
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var json: ObjectMapper
    @Autowired lateinit var jwt: JwtTokenProvider
    @Autowired lateinit var stores: StoreService
    @Autowired lateinit var hours: BusinessHoursService
    @Autowired lateinit var shifts: RegularShiftService
    @Autowired lateinit var notifications: NotificationService
    @Autowired lateinit var workerRepository: com.worktime.application.port.output.WorkerRepository
    @Autowired lateinit var scheduleRepository: com.worktime.application.port.output.ScheduleRepository
    @Autowired lateinit var logRepository: com.worktime.application.port.output.WorkLogRepository
    @Autowired lateinit var wageRepository: com.worktime.application.port.output.WageContractRepository
    @Autowired lateinit var ownerRepository: com.worktime.application.port.output.OwnerRepository
    private fun user(role: UserRole=UserRole.OWNER)=User(email="${UUID.randomUUID()}@test.invalid",password="synthetic",name="Test",role=role).also(em::persist)
    private fun owner()=Owner(user=user()).also(em::persist)
    private fun token(user: User)="Bearer ${jwt.generateToken(user.id,user.role)}"
    private fun store(owner: Owner)=Store(owner=owner,name="Cafe").also(em::persist)
    @Test fun `store lifecycle persists changes and duplicate names fail`() {
        val owner=owner()
        val first=stores.createStore(owner.id,"Cafe","A","UTC")
        assertEquals("A",first.address);assertEquals("UTC",first.timezone)
        assertThrows<BusinessException> { stores.createStore(owner.id,"Cafe") }
        val other=stores.createStore(owner.id,"Other")
        assertThrows<BusinessException> { stores.updateStore(first.id,"Other") }
        assertEquals("Cafe",stores.updateStore(first.id,"Cafe").name)
        assertEquals("Cafe",stores.updateStore(first.id).name)
        stores.updateStore(first.id,"Renamed","B","Asia/Seoul")
        stores.deactivateStore(first.id)
        em.flush();em.clear()
        val saved=stores.getStore(first.id)
        assertEquals("Renamed",saved.name);assertEquals("B",saved.address);assertEquals("Asia/Seoul",saved.timezone);assertFalse(saved.active)
        assertEquals(setOf(first.id,other.id),stores.getStoresByOwner(owner.id).map { it.id }.toSet())
        assertEquals(listOf(other.id),stores.getActiveStoresByOwner(owner.id).map { it.id })
        assertTrue(stores.activateStore(first.id).active)
        assertThrows<ResourceNotFoundException> { stores.getStore(UUID.randomUUID()) }
        assertThrows<ResourceNotFoundException> { stores.createStore(UUID.randomUUID(),"Missing") }
    }
    @Test fun `business hours replacement and per-day reopening persist with inclusive time bounds`() {
        val store=store(owner());val open=LocalTime.of(9,0);val close=LocalTime.of(18,0)
        hours.setBusinessHours(store.id,listOf(BusinessHoursRequest(DayOfWeek.MONDAY,open,close),BusinessHoursRequest(DayOfWeek.TUESDAY,close,open,true)))
        assertEquals(2,hours.getBusinessHours(store.id).size);assertEquals(1,hours.getOpenDays(store.id).size)
        val monday=hours.getDayHours(store.id,DayOfWeek.MONDAY)!!
        assertTrue(monday.isWithinBusinessHours(open));assertTrue(monday.isWithinBusinessHours(close))
        assertFalse(monday.isWithinBusinessHours(open.minusMinutes(1)));assertFalse(monday.isWithinBusinessHours(close.plusMinutes(1)))
        monday.markAsClosed();assertFalse(monday.isWithinBusinessHours(open));monday.markAsOpen();assertTrue(monday.isWithinBusinessHours(open))
        hours.updateDayHours(store.id,DayOfWeek.MONDAY,open,close,true)
        assertTrue(hours.getDayHours(store.id,DayOfWeek.MONDAY)!!.isClosed)
        hours.updateDayHours(store.id,DayOfWeek.MONDAY,open.plusHours(1),close)
        hours.updateDayHours(store.id,DayOfWeek.WEDNESDAY,open,close)
        em.flush();em.clear()
        assertEquals(open.plusHours(1),hours.getDayHours(store.id,DayOfWeek.MONDAY)!!.openTime)
        assertNull(hours.getDayHours(store.id,DayOfWeek.FRIDAY))
        assertThrows<IllegalArgumentException> { hours.updateDayHours(store.id,DayOfWeek.MONDAY,close,open) }
        assertThrows<ResourceNotFoundException> { hours.setBusinessHours(UUID.randomUUID(),emptyList()) }
        assertThrows<ResourceNotFoundException> { hours.updateDayHours(UUID.randomUUID(),DayOfWeek.MONDAY,open,close) }
    }
    @Test fun `regular shift lifecycle generates schedules and retains effective dates and assignment`() {
        val owner=owner();val store=store(owner)
        val worker=Worker(name="First",phoneNumber="000",owner=owner).also(em::persist)
        val other=Worker(name="Second",phoneNumber="001",owner=owner).also(em::persist)
        val today=LocalDate.now();val open=LocalTime.of(9,0);val close=LocalTime.of(10,0)
        em.flush()
        val shift=shifts.createRegularShift(store.id,worker.id,today.dayOfWeek,open,close,"Morning",today,today.plusMonths(1))
        assertEquals("Morning",shift.shiftName)
        val generated=em.createQuery("select s from Schedule s where s.worker.id=:id",Schedule::class.java).setParameter("id",worker.id).resultList
        val expectedDates=generateSequence(today) { it.plusDays(1) }.takeWhile { !it.isAfter(today.plusMonths(1)) }
            .filter { it.dayOfWeek==today.dayOfWeek }.toList()
        assertEquals(expectedDates,generated.map { it.startAtUtc.atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDate() }.sorted())
        assertTrue(generated.all { it.worker.id==worker.id && it.status==ScheduleStatus.CONFIRMED })
        assertEquals(listOf(shift.id),shifts.getRegularShiftsByStore(store.id).map { it.id })
        assertEquals(listOf(shift.id),shifts.getRegularShiftsByDay(store.id,today.dayOfWeek).map { it.id })
        assertEquals(listOf(shift.id),shifts.getTodayShifts(store.id).map { it.id })
        assertEquals(listOf(shift.id),shifts.getRegularShiftsByWorker(worker.id).map { it.id })
        shifts.updateShiftTime(shift.id,open.plusHours(2),close.plusHours(2));shifts.assignWorker(shift.id,other.id)
        shifts.setEffectivePeriod(shift.id,null,today.plusDays(7));shifts.deactivateShift(shift.id)
        em.flush();em.clear()
        val saved=shifts.getRegularShift(shift.id)
        assertEquals(other.id,saved.worker.id);assertEquals(open.plusHours(2),saved.startTime);assertFalse(saved.active)
        assertNull(saved.effectiveFrom);assertEquals(today.plusDays(7),saved.effectiveUntil)
        assertTrue(shifts.getActiveRegularShiftsByStore(store.id).isEmpty())
        assertThrows<ResourceNotFoundException> { shifts.getRegularShift(UUID.randomUUID()) }
        assertThrows<ResourceNotFoundException> { shifts.assignWorker(shift.id,UUID.randomUUID()) }
        assertThrows<ResourceNotFoundException> { shifts.createRegularShift(UUID.randomUUID(),worker.id,today.dayOfWeek,open,close) }
        assertThrows<ResourceNotFoundException> { shifts.createRegularShift(store.id,UUID.randomUUID(),today.dayOfWeek,open,close) }
        assertThrows<IllegalArgumentException> { shifts.createRegularShift(store.id,worker.id,today.dayOfWeek,close,open) }
    }
    @Test fun `notification API isolates recipients and updates read counts in persistence`() {
        val first=user();val second=user()
        val one=notifications.createNotification(first.id,NotificationType.SCHEDULE_PROPOSED,"Proposal","New shift",UUID.randomUUID())
        val two=notifications.createNotification(first.id,NotificationType.SCHEDULE_CONFIRMED,"Confirmed","Confirmed shift")
        val foreign=notifications.createNotification(second.id,NotificationType.SCHEDULE_REJECTED,"Private","Private shift")
        em.flush()
        val body=mvc.perform(get("/api/notifications").header("Authorization",token(first))).andExpect(status().isOk).andReturn().response.contentAsString
        val result=json.readTree(body)
        assertEquals(2,result["unreadCount"].asInt());assertEquals(2,result["notifications"].size())
        assertFalse(body.contains(foreign.id.toString()))
        mvc.perform(post("/api/notifications/${one.id}/read").header("Authorization",token(first))).andExpect(status().isOk)
        em.flush();em.clear();assertTrue(em.find(Notification::class.java,one.id).isRead)
        val count=mvc.perform(get("/api/notifications/unread-count").header("Authorization",token(first))).andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals(1,json.readTree(count)["count"].asInt())
        assertThrows<IllegalArgumentException> { notifications.markAsRead(foreign.id,first.id) }
        val all=mvc.perform(post("/api/notifications/read-all").header("Authorization",token(first))).andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals(1,json.readTree(all)["updatedCount"].asInt());em.flush();em.clear()
        assertTrue(em.find(Notification::class.java,two.id).isRead);assertFalse(em.find(Notification::class.java,foreign.id).isRead)
        assertThrows<ResourceNotFoundException> { notifications.markAsRead(UUID.randomUUID(),first.id) }
        assertThrows<ResourceNotFoundException> { notifications.createNotification(UUID.randomUUID(),NotificationType.SCHEDULE_CANCELED,"Missing","Missing") }
    }
    @Test fun `store API persists authenticated owner and blocks worker role`() {
        val owner=owner();val authorization=token(owner.user)
        val body=mvc.perform(post("/api/stores").header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"API Cafe","address":"A","timezone":"UTC"}""")).andExpect(status().isCreated).andReturn().response.contentAsString
        val id=UUID.fromString(json.readTree(body)["id"].asText());em.flush();em.clear()
        assertEquals(owner.id,stores.getStore(id).owner.id)
        val list=mvc.perform(get("/api/stores").header("Authorization",authorization)).andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals(id.toString(),json.readTree(list).single()["id"].asText())
        mvc.perform(get("/api/stores/$id").header("Authorization",authorization)).andExpect(status().isOk)
        mvc.perform(put("/api/stores/$id").header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON).content("""{"name":"Updated"}""")).andExpect(status().isOk)
        mvc.perform(put("/api/stores/$id/business-hours").header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON)
            .content("""{"hours":[{"dayOfWeek":"MONDAY","openTime":"09:00","closeTime":"18:00","isClosed":false}]}""")).andExpect(status().isOk)
        val hoursBody=mvc.perform(get("/api/stores/$id/business-hours").header("Authorization",authorization)).andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals("MONDAY",json.readTree(hoursBody).single()["dayOfWeek"].asText())
        mvc.perform(delete("/api/stores/$id").header("Authorization",authorization)).andExpect(status().isNoContent)
        assertFalse(stores.getStore(id).active)
        val worker=user(UserRole.WORKER)
        mvc.perform(get("/api/stores").header("Authorization",token(worker))).andExpect(status().isForbidden)
        mvc.perform(get("/api/stores")).andExpect(status().isUnauthorized)
        val orphan=user();mvc.perform(get("/api/stores").header("Authorization",token(orphan))).andExpect(status().isNotFound)
    }

    @Test fun `worker CRUD and invite API enforce cross-owner isolation and persist resignation`() {
        val owner=owner();val otherOwner=owner();val store=store(owner);val foreignStore=store(otherOwner)
        val authorization=token(owner.user)
        fun create(name: String, storeId: UUID?=null): UUID {
            val payload=json.writeValueAsString(mapOf("name" to name,"phoneNumber" to "01012345678","storeId" to storeId))
            val body=mvc.perform(post("/api/workers").header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated).andReturn().response.contentAsString
            return UUID.fromString(json.readTree(body)["id"].asText())
        }
        val id=create("First",store.id);val withoutStore=create("No store")
        em.flush()
        val forbidden=json.writeValueAsString(mapOf("name" to "Foreign","phoneNumber" to "01012345678","storeId" to foreignStore.id))
        mvc.perform(post("/api/workers").header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON).content(forbidden)).andExpect(status().isNotFound)
        mvc.perform(get("/api/workers/$id").header("Authorization",token(otherOwner.user))).andExpect(status().isNotFound)
        mvc.perform(put("/api/workers/$id").header("Authorization",token(otherOwner.user)).contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Foreign","phoneNumber":"01012345678"}""")).andExpect(status().isNotFound)
        val detail=mvc.perform(get("/api/workers/$id").header("Authorization",authorization)).andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals("First",json.readTree(detail)["worker"]["name"].asText())
        val update=json.writeValueAsString(mapOf("name" to "Updated","phoneNumber" to "01098765432","storeId" to store.id))
        mvc.perform(put("/api/workers/$id").header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isOk)
        em.flush() // Flush pending changes before the invite operation refreshes the worker lock; the test transaction is not committed.
        val invite=mvc.perform(post("/api/workers/$id/invite").header("Authorization",authorization)).andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals(6,json.readTree(invite)["inviteCode"].asText().length)
        em.flush();em.clear();assertEquals("Updated",em.find(Worker::class.java,id).name)
        assertEquals(json.readTree(invite)["inviteCode"].asText(),em.find(Worker::class.java,id).inviteCode)
        mvc.perform(post("/api/workers/$id/resign").param("resignDate","2025-01-01").header("Authorization",authorization)).andExpect(status().isOk)
        mvc.perform(delete("/api/workers/$withoutStore").header("Authorization",authorization)).andExpect(status().isNoContent)
        for(params in listOf(emptyMap(),mapOf("active" to "false"),mapOf("storeId" to store.id.toString()),mapOf("storeId" to store.id.toString(),"active" to "false"))) {
            val request=get("/api/workers").header("Authorization",authorization);params.forEach { (k,v)->request.param(k,v) }
            val body=mvc.perform(request).andExpect(status().isOk).andReturn().response.contentAsString
            val ids=json.readTree(body).map { it["id"].asText() }
            assertTrue(ids.contains(id.toString()));assertEquals(if(params.containsKey("storeId"))1 else 2,ids.size)
        }
        em.flush();em.clear();assertFalse(em.find(Worker::class.java,id).active);assertFalse(em.find(Worker::class.java,withoutStore).active)
    }
    @Test fun `owner signup login and invited worker signup round trip through security and persistence`() {
        val email="${UUID.randomUUID()}@test.invalid"
        val payload=json.writeValueAsString(mapOf("email" to email,"password" to "password123","name" to "New owner","businessName" to "Cafe"))
        val response=mvc.perform(post("/api/auth/owners/signup").contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isCreated).andReturn().response.contentAsString
        val signed=json.readTree(response);val id=UUID.fromString(signed["userId"].asText())
        assertEquals("OWNER",signed["role"].asText());assertEquals(id,jwt.getUserIdFromToken(signed["accessToken"].asText()))
        val login=json.writeValueAsString(mapOf("email" to email,"password" to "password123"))
        val logged=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login)).andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals(signed["ownerId"].asText(),json.readTree(logged)["ownerId"].asText())
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login.replace("password123","incorrect"))).andExpect(status().isUnauthorized)
        mvc.perform(post("/api/auth/owners/signup").contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isConflict)
        val owner=em.find(Owner::class.java,UUID.fromString(signed["ownerId"].asText()))
        val worker=Worker(name="Invited",phoneNumber="01012345678",owner=owner).also(em::persist);val code=worker.generateInviteCode();em.flush()
        val workerEmail="${UUID.randomUUID()}@test.invalid"
        val workerPayload=json.writeValueAsString(mapOf("inviteCode" to code.lowercase(),"email" to workerEmail,"password" to "password123"))
        val created=mvc.perform(post("/api/auth/workers/signup").contentType(MediaType.APPLICATION_JSON).content(workerPayload)).andExpect(status().isCreated).andReturn().response.contentAsString
        val workerResult=json.readTree(created);assertEquals(worker.id.toString(),workerResult["workerId"].asText())
        val own=mvc.perform(get("/api/workers/my").header("Authorization","Bearer ${workerResult["accessToken"].asText()}")).andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals(worker.id.toString(),json.readTree(own)["id"].asText())
        em.flush();em.clear();val saved=em.find(Worker::class.java,worker.id)
        assertNull(saved.inviteCode);assertNull(saved.inviteCodeExpiresAt);assertEquals(workerEmail,saved.userAccount!!.email)
        mvc.perform(get("/api/workers/my").header("Authorization",token(user(UserRole.WORKER)))).andExpect(status().isNotFound)
    }

    @Test fun `regular shift API creates reads updates generates and deactivates persisted shift`() {
        val owner=owner();val store=store(owner);val authorization=token(owner.user)
        val worker=Worker(name="Shift worker",phoneNumber="01012345678",owner=owner).also(em::persist)
        val other=Worker(name="Replacement",phoneNumber="01098765432",owner=owner).also(em::persist);em.flush()
        val payload=json.writeValueAsString(mapOf("workerId" to worker.id,"dayOfWeek" to LocalDate.now().dayOfWeek.name,
            "startTime" to "09:00","endTime" to "10:00","shiftName" to "Morning"))
        val created=mvc.perform(post("/api/stores/${store.id}/regular-shifts").header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val id=UUID.fromString(json.readTree(created)["id"].asText())
        for(path in listOf("/api/stores/${store.id}/regular-shifts","/api/stores/${store.id}/regular-shifts?activeOnly=false",
            "/api/stores/${store.id}/regular-shifts/today","/api/stores/${store.id}/regular-shifts/date/${LocalDate.now()}","/api/workers/${worker.id}/regular-shifts")) {
            val body=mvc.perform(get(path).header("Authorization",authorization)).andExpect(status().isOk).andReturn().response.contentAsString
            assertEquals(id.toString(),json.readTree(body).single()["id"].asText())
        }
        mvc.perform(get("/api/regular-shifts/$id").header("Authorization",authorization)).andExpect(status().isOk)
        val updates=listOf(emptyMap<String,Any>(),mapOf("startTime" to "10:00"),mapOf("endTime" to "12:00"),
            mapOf("workerId" to other.id,"startTime" to "10:00","endTime" to "12:00","effectiveFrom" to "2024-01-01","effectiveUntil" to "2024-12-31"),
            mapOf("effectiveUntil" to "2024-06-30"))
        for(update in updates) mvc.perform(put("/api/regular-shifts/$id").header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(update))).andExpect(status().isOk)
        em.flush();em.clear();val saved=shifts.getRegularShift(id)
        assertEquals(other.id,saved.worker.id);assertEquals(LocalTime.of(10,0),saved.startTime);assertEquals(LocalTime.NOON,saved.endTime)
        assertNull(saved.effectiveFrom);assertEquals(LocalDate.of(2024,6,30),saved.effectiveUntil)
        val generated=mvc.perform(post("/api/stores/${store.id}/generate-schedules").header("Authorization",authorization).param("year","2024").param("month","2"))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val generatedItems=json.readTree(generated)["schedules"]
        val expectedDates=generateSequence(LocalDate.of(2024,2,1)) { it.plusDays(1) }.takeWhile { it.monthValue==2 }
            .filter { it.dayOfWeek==saved.dayOfWeek }.toList()
        assertEquals(expectedDates,generatedItems.map { OffsetDateTime.parse(it["startAtUtc"].asText()).toLocalDate() }.sorted())
        assertTrue(generatedItems.all { it["workerId"].asText()==other.id.toString() && it["status"].asText()=="CONFIRMED" })
        mvc.perform(delete("/api/regular-shifts/$id").header("Authorization",authorization)).andExpect(status().isNoContent)
        em.flush();em.clear();assertFalse(shifts.getRegularShift(id).active)
    }

    @Test fun `repository queries preserve worker identity status time range and deletion semantics`() {
        val owner=owner();val foreignOwner=owner();val store=store(owner)
        val worker=workerRepository.save(Worker(name="Repository",phoneNumber="01011111111",owner=owner,store=store))
        val foreign=workerRepository.save(Worker(name="Foreign",phoneNumber="01022222222",owner=foreignOwner,active=false))
        val start=OffsetDateTime.parse("2024-02-01T09:00:00Z");val end=start.plusHours(4)
        val schedule=scheduleRepository.save(Schedule(worker=worker,startAtUtc=start,endAtUtc=start.plusHours(1),timezone="UTC",status=ScheduleStatus.CONFIRMED))
        val foreignSchedule=scheduleRepository.save(Schedule(worker=foreign,startAtUtc=start,endAtUtc=start.plusHours(1),timezone="UTC",status=ScheduleStatus.CONFIRMED))
        val completed=logRepository.save(WorkLog(worker=worker,schedule=schedule,clockInAt=start,clockOutAt=start.plusHours(1),status=WorkLogStatus.COMPLETED))
        val ongoing=logRepository.save(WorkLog(worker=worker,clockInAt=start.plusHours(2)))
        val foreignLog=logRepository.save(WorkLog(worker=foreign,schedule=foreignSchedule,clockInAt=start,clockOutAt=start.plusHours(1),status=WorkLogStatus.CONFIRMED))
        em.flush()
        assertEquals(setOf(worker.id,foreign.id),workerRepository.findAll().map { it.id }.toSet())
        assertEquals(listOf(worker.id),workerRepository.findByActive(true).map { it.id });assertEquals(listOf(foreign.id),workerRepository.findByActive(false).map { it.id })
        assertEquals(worker.id,workerRepository.findByPhoneNumber("01011111111")!!.id)
        assertTrue(workerRepository.existsByPhoneNumber("01011111111"));assertFalse(workerRepository.existsByPhoneNumber("missing"));assertNull(workerRepository.findByPhoneNumber("missing"))
        assertEquals(owner.id,ownerRepository.findById(owner.id)!!.id);assertNull(ownerRepository.findById(UUID.randomUUID()))
        assertEquals(setOf(schedule.id,foreignSchedule.id),scheduleRepository.findByTimeRange(start,end).map { it.id }.toSet())
        assertEquals(listOf(schedule.id),scheduleRepository.findByWorkerAndStatusAndTimeRange(worker,ScheduleStatus.CONFIRMED,start,end).map { it.id })
        assertTrue(scheduleRepository.findByWorkerAndStatusAndTimeRange(worker,ScheduleStatus.REJECTED,start,end).isEmpty())
        assertEquals(setOf(completed.id,ongoing.id,foreignLog.id),logRepository.findByTimeRange(start,end).map { it.id }.toSet())
        assertEquals(listOf(ongoing.id),logRepository.findOngoingWorkLogsOlderThan(owner.id,end).map { it.id })
        assertEquals(listOf(ongoing.id),logRepository.findOngoingWorkLogsByOwner(owner.id).map { it.id })
        assertEquals(listOf(completed.id),logRepository.findCompletedWorkLogsByOwnerAndTimeRange(owner.id,start,end).map { it.id })
        assertTrue(logRepository.findByScheduleIds(emptyList()).isEmpty());assertTrue(wageRepository.findByWorkerIn(emptyList()).isEmpty())
        val unreferenced=workerRepository.save(Worker(name="Temporary",phoneNumber="01033333333",owner=owner));em.flush()
        workerRepository.delete(unreferenced);em.flush();em.clear();assertNull(workerRepository.findById(unreferenced.id))
    }

    @Test fun `worker schedule and log endpoints reject absent users and unlinked accounts`() {
        val missing=User(id=UUID.randomUUID(),email="missing@test.invalid",password="pw",name="Missing",role=UserRole.WORKER)
        val unlinked=user(UserRole.WORKER)
        for(account in listOf(missing,unlinked)) for(path in listOf("/api/worker/schedules","/api/worklogs/my","/api/worklogs/my/current")) {
            mvc.perform(get(path).header("Authorization",token(account))).andExpect(status().isNotFound)
        }
    }
    @Test fun `worker current clock APIs select confirmed schedules and persist completion`() {
        val owner=owner();val account=user(UserRole.WORKER)
        val worker=Worker(name="Clock",phoneNumber="01012345678",owner=owner,userAccount=account).also(em::persist)
        val today=LocalDate.now().atStartOfDay().atOffset(ZoneOffset.UTC)
        val schedules=(1..3).map { i -> Schedule(worker=worker,startAtUtc=today.plusHours(9),endAtUtc=today.plusHours(10),timezone="UTC",
            status=if(i==3)ScheduleStatus.REJECTED else ScheduleStatus.CONFIRMED).also(em::persist) };em.flush()
        val authorization=token(account)
        val before=mvc.perform(get("/api/worklogs/my/current").header("Authorization",authorization)).andExpect(status().isOk).andReturn().response.contentAsString
        assertTrue(before.isBlank())
        val created=mvc.perform(post("/api/worklogs/my/clock-in").header("Authorization",authorization)).andExpect(status().isCreated).andReturn().response.contentAsString
        val node=json.readTree(created);val id=UUID.fromString(node["id"].asText())
        assertTrue(node["scheduleId"].asText() in schedules.take(2).map { it.id.toString() });em.flush()
        val current=mvc.perform(get("/api/worklogs/my/current").header("Authorization",authorization)).andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals(id.toString(),json.readTree(current)["id"].asText())
        mvc.perform(post("/api/worklogs/my/clock-out").header("Authorization",authorization)).andExpect(status().isOk)
        em.flush();em.clear();assertEquals(WorkLogStatus.COMPLETED,em.find(WorkLog::class.java,id).status)
        for(params in listOf(emptyMap(),mapOf("fromDate" to LocalDate.now().toString()),mapOf("toDate" to LocalDate.now().toString()),
            mapOf("fromDate" to LocalDate.now().toString(),"toDate" to LocalDate.now().toString()))) {
            val request=get("/api/worklogs/my").header("Authorization",authorization);params.forEach { (k,v)->request.param(k,v) }
            val body=mvc.perform(request).andExpect(status().isOk).andReturn().response.contentAsString
            assertEquals(if(params.size==2)1 else 0,json.readTree(body).size())
        }
    }
    @Test fun `owner review API supports absent body and verifies persisted log queries`() {
        val owner=owner();val worker=Worker(name="Review",phoneNumber="01012345678",owner=owner).also(em::persist)
        val start=OffsetDateTime.parse("2024-02-01T09:00:00Z")
        val first=WorkLog(worker=worker,clockInAt=start,clockOutAt=start.plusHours(1),status=WorkLogStatus.COMPLETED).also(em::persist)
        val second=WorkLog(worker=worker,clockInAt=start.plusHours(2),clockOutAt=start.plusHours(3),status=WorkLogStatus.COMPLETED).also(em::persist);em.flush()
        val authorization=token(owner.user)
        mvc.perform(post("/api/work-logs/${first.id}/confirm").header("Authorization",authorization)).andExpect(status().isOk)
        mvc.perform(post("/api/work-logs/${second.id}/reject").header("Authorization",authorization)).andExpect(status().isOk)
        em.flush();em.clear();assertEquals(WorkLogStatus.CONFIRMED,em.find(WorkLog::class.java,first.id).status)
        assertEquals(WorkLogStatus.REJECTED,em.find(WorkLog::class.java,second.id).status);assertNull(em.find(WorkLog::class.java,second.id).rejectionReason)
        val one=mvc.perform(get("/api/work-logs/${first.id}").header("Authorization",authorization)).andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals(first.id.toString(),json.readTree(one)["id"].asText())
        for(path in listOf("/api/workers/${worker.id}/work-logs","/api/work-logs")) {
            val request=get(path).header("Authorization",authorization).param("from",start.toString()).param("to",start.plusDays(1).toString())
            val result=mvc.perform(request).andExpect(status().isOk).andReturn().response.contentAsString
            assertEquals(setOf(first.id.toString(),second.id.toString()),json.readTree(result).map { it["id"].asText() }.toSet())
        }
    }
    @Test fun `dashboard APIs default dates and serialize empty owner metrics and authorization failures`() {
        val owner=owner();val authorization=token(owner.user)
        for(path in listOf("summary","actions","attendance","top-workers")) {
            val body=mvc.perform(get("/api/owner/dashboard/$path").header("Authorization",authorization)).andExpect(status().isOk).andReturn().response.contentAsString
            val node=json.readTree(body)
            when(path) {
                "summary" -> { assertEquals(0,node["today"]["scheduledCount"].asInt());assertEquals(0,node["month"]["accruedWage"].asInt());assertEquals(0,node["actionNeededCount"].asInt()) }
                "actions" -> { assertEquals(0,node["totalCount"].asInt());assertEquals(0,node["items"].size()) }
                "attendance" -> { assertEquals(0,node["summary"]["scheduledCount"].asInt());assertEquals(0,node["exceptions"].size()) }
                "top-workers" -> { assertEquals(7,node["range"]["days"].asInt());assertEquals(0,node["items"].size()) }
            }
            mvc.perform(get("/api/owner/dashboard/$path").header("Authorization",token(user(UserRole.WORKER)))).andExpect(status().isForbidden)
        }
        mvc.perform(get("/api/owner/dashboard/summary").header("Authorization",token(user()))).andExpect(status().isNotFound)
        val datedAttendance=mvc.perform(get("/api/owner/dashboard/attendance").param("date","2024-02-01").header("Authorization",authorization)).andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals("2024-02-01",json.readTree(datedAttendance)["date"].asText())
        val actions=mvc.perform(get("/api/owner/dashboard/actions").param("types","PENDING_SCHEDULE_APPROVAL").header("Authorization",authorization)).andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals(0,json.readTree(actions)["items"].size())
    }

    @Test fun `foreign owner cannot read mutate generate schedules or reassign another owners store and shift`() {
        val owner=owner();val foreignOwner=owner();val store=store(owner)
        val worker=Worker(name="Private",phoneNumber="01012345678",owner=owner).also(em::persist)
        val foreignWorker=Worker(name="Other owner worker",phoneNumber="01098765432",owner=foreignOwner).also(em::persist)
        val shift=RegularShift(store=store,worker=worker,dayOfWeek=DayOfWeek.MONDAY,startTime=LocalTime.of(9,0),endTime=LocalTime.of(10,0)).also(em::persist);em.flush()
        val authorization=token(foreignOwner.user)
        for(path in listOf("/api/stores/${store.id}","/api/stores/${store.id}/business-hours","/api/stores/${store.id}/regular-shifts",
            "/api/stores/${store.id}/regular-shifts/today","/api/stores/${store.id}/regular-shifts/date/2024-02-01",
            "/api/regular-shifts/${shift.id}","/api/workers/${worker.id}/regular-shifts")) {
            mvc.perform(get(path).header("Authorization",authorization)).andExpect(status().isNotFound)
        }
        mvc.perform(put("/api/stores/${store.id}").header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON).content("""{"name":"Hijacked"}""")).andExpect(status().isNotFound)
        mvc.perform(delete("/api/stores/${store.id}").header("Authorization",authorization)).andExpect(status().isNotFound)
        mvc.perform(put("/api/stores/${store.id}/business-hours").header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON).content("""{"hours":[]}""")).andExpect(status().isNotFound)
        mvc.perform(put("/api/regular-shifts/${shift.id}").header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON).content("""{"startTime":"10:00","endTime":"11:00"}""")).andExpect(status().isNotFound)
        mvc.perform(delete("/api/regular-shifts/${shift.id}").header("Authorization",authorization)).andExpect(status().isNotFound)
        mvc.perform(post("/api/stores/${store.id}/generate-schedules").header("Authorization",authorization).param("year","2024").param("month","2")).andExpect(status().isNotFound)
        val payload=json.writeValueAsString(mapOf("workerId" to foreignWorker.id,"dayOfWeek" to "MONDAY","startTime" to "09:00","endTime" to "10:00"))
        mvc.perform(post("/api/stores/${store.id}/regular-shifts").header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isNotFound)
        mvc.perform(post("/api/stores/${store.id}/regular-shifts").header("Authorization",token(owner.user)).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isNotFound)
        mvc.perform(put("/api/regular-shifts/${shift.id}").header("Authorization",token(owner.user)).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(mapOf("workerId" to foreignWorker.id)))).andExpect(status().isNotFound)
        em.flush();em.clear()
        assertEquals("Cafe",stores.getStore(store.id).name);assertTrue(stores.getStore(store.id).active)
        assertTrue(shifts.getRegularShift(shift.id).active);assertEquals(worker.id,shifts.getRegularShift(shift.id).worker.id)
        assertEquals(LocalTime.of(9,0),shifts.getRegularShift(shift.id).startTime)
    }

    @Test fun `wage API forbids foreign owner and mismatched contract worker without changing pay`() {
        val owner=owner();val foreignOwner=owner()
        val worker=Worker(name="Payroll private",phoneNumber="01012345678",owner=owner).also(em::persist)
        val other=Worker(name="Another worker",phoneNumber="01012345679",owner=owner).also(em::persist)
        val contract=WageContract(worker=worker,hourlyWage=java.math.BigDecimal("10000"),startDate=LocalDate.of(2024,1,1)).also(em::persist);em.flush()
        val authorization=token(foreignOwner.user)
        for(path in listOf("/api/workers/${worker.id}/wage?date=2024-02-01","/api/workers/${worker.id}/wage-contracts"))
            mvc.perform(get(path).header("Authorization",authorization)).andExpect(status().isNotFound)
        mvc.perform(post("/api/workers/${worker.id}/wage-contracts").header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON)
            .content("""{"hourlyWage":20000,"startDate":"2025-01-01"}""")).andExpect(status().isNotFound)
        mvc.perform(patch("/api/workers/${worker.id}/wage-contracts/${contract.id}/terminate").param("endDate","2024-12-31").header("Authorization",authorization)).andExpect(status().isNotFound)
        mvc.perform(patch("/api/workers/${other.id}/wage-contracts/${contract.id}/terminate").param("endDate","2024-12-31").header("Authorization",token(owner.user))).andExpect(status().isNotFound)
        em.flush();em.clear();assertNull(em.find(WageContract::class.java,contract.id).endDate)
        assertEquals(java.math.BigDecimal("10000.00"),em.find(WageContract::class.java,contract.id).hourlyWage)
    }

    @Test fun `wage API reads history and terminates only the correct owners worker contract`() {
        val owner=owner();val worker=Worker(name="Contract",phoneNumber="01012345678",owner=owner).also(em::persist);em.flush()
        val authorization=token(owner.user)
        val created=mvc.perform(post("/api/workers/${worker.id}/wage-contracts").header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON)
            .content("""{"hourlyWage":10000,"startDate":"2024-01-01"}""")).andExpect(status().isCreated).andReturn().response.contentAsString
        val id=UUID.fromString(json.readTree(created)["id"].asText());em.flush()
        val history=mvc.perform(get("/api/workers/${worker.id}/wage-contracts").header("Authorization",authorization)).andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals(id.toString(),json.readTree(history).single()["id"].asText())
        val detail=mvc.perform(get("/api/workers/${worker.id}").header("Authorization",authorization)).andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals(id.toString(),json.readTree(detail)["currentWage"]["id"].asText())
        val wage=mvc.perform(get("/api/workers/${worker.id}/wage").param("date","2024-02-01").header("Authorization",authorization)).andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals(10000,json.readTree(wage)["hourlyWage"].asInt());assertTrue(json.readTree(wage)["hasContract"].asBoolean())
        mvc.perform(patch("/api/workers/${worker.id}/wage-contracts/$id/terminate").param("endDate","2024-12-31").header("Authorization",authorization)).andExpect(status().isOk)
        em.flush();em.clear();assertEquals(LocalDate.of(2024,12,31),em.find(WageContract::class.java,id).endDate)
        mvc.perform(patch("/api/workers/${worker.id}/wage-contracts/${UUID.randomUUID()}/terminate").param("endDate","2024-12-31").header("Authorization",authorization)).andExpect(status().isNotFound)
    }

    @Test fun `schedule API round trip binds requests transitions manual work and payroll summary`() {
        val owner=owner();val account=user(UserRole.WORKER)
        val worker=Worker(name="Schedule API",phoneNumber="01012345678",owner=owner,userAccount=account).also(em::persist)
        WageContract(worker=worker,hourlyWage=java.math.BigDecimal("10000"),startDate=LocalDate.of(2024,1,1)).also(em::persist);em.flush()
        val ownerToken=token(owner.user);val workerToken=token(account);val start=OffsetDateTime.parse("2024-02-01T09:00:00Z")
        fun create(type: String,at: OffsetDateTime,authorization: String): UUID {
            val payload=json.writeValueAsString(mapOf("workerId" to worker.id,"startAtUtc" to at,"endAtUtc" to at.plusHours(1),"timezone" to "UTC","requestType" to type))
            val body=mvc.perform(post("/api/workers/${worker.id}/schedules").header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated).andReturn().response.contentAsString
            em.flush();return UUID.fromString(json.readTree(body)["id"].asText())
        }
        val requested=create("WORKER_REQUEST",start,workerToken)
        mvc.perform(patch("/api/schedules/$requested").header("Authorization",ownerToken).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(mapOf("startAtUtc" to start,"endAtUtc" to start.plusHours(2),"timezone" to "UTC")))).andExpect(status().isOk)
        em.flush();mvc.perform(post("/api/schedules/$requested/confirm").header("Authorization",ownerToken)).andExpect(status().isOk)
        em.flush();mvc.perform(get("/api/schedules/$requested").header("Authorization",workerToken)).andExpect(status().isOk)
        mvc.perform(post("/api/schedules/$requested/cancel").header("Authorization",ownerToken)).andExpect(status().isOk);em.flush()
        val rejected=create("WORKER_REQUEST",start.plusDays(1),workerToken)
        mvc.perform(post("/api/schedules/$rejected/reject").header("Authorization",ownerToken)).andExpect(status().isOk);em.flush()
        val workerRejected=create("OWNER_PROPOSAL",start.plusDays(2),ownerToken)
        mvc.perform(post("/api/worker/schedules/$workerRejected/reject").header("Authorization",workerToken)).andExpect(status().isOk);em.flush()
        val confirmed=create("OWNER_PROPOSAL",start.plusDays(3),ownerToken)
        mvc.perform(post("/api/worker/schedules/$confirmed/confirm").header("Authorization",workerToken)).andExpect(status().isOk);em.flush()
        val work=mvc.perform(post("/api/worker/schedules/$confirmed/worklog").header("Authorization",workerToken).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(mapOf("clockInAt" to start.plusDays(3),"clockOutAt" to start.plusDays(3).plusHours(1)))))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val logId=UUID.fromString(json.readTree(work)["id"].asText());em.flush()
        mvc.perform(post("/api/work-logs/$logId/confirm").header("Authorization",ownerToken).contentType(MediaType.APPLICATION_JSON).content("""{"expectedCorrectionCount":0}""")).andExpect(status().isOk);em.flush()
        for(path in listOf("/api/worker/schedules","/api/workers/${worker.id}/schedules")) {
            val body=mvc.perform(get(path).param("from",start.toString()).param("to",start.plusDays(5).toString()).header("Authorization",workerToken)).andExpect(status().isOk).andReturn().response.contentAsString
            assertEquals(4,json.readTree(body)["totalCount"].asInt())
        }
        val listed=mvc.perform(get("/api/schedules").param("from",start.toString()).param("to",start.plusDays(5).toString()).param("workerId",worker.id.toString()).param("status","CONFIRMED")
            .header("Authorization",ownerToken)).andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals(1,json.readTree(listed)["totalCount"].asInt())
        val summary=mvc.perform(get("/api/workers/${worker.id}/work-summary").param("from","2024-02-01").param("to","2024-02-29").header("Authorization",workerToken))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals(60,json.readTree(summary)["totalMinutes"].asInt());assertEquals(1,json.readTree(summary)["workLogCount"].asInt())
        val payroll=mvc.perform(get("/api/workers/${worker.id}/payroll").param("from","2024-02-01").param("to","2024-02-29").header("Authorization",ownerToken))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals(10000,json.readTree(payroll)["totalPay"].asInt())
        val monthly=mvc.perform(get("/api/payroll/owner/monthly").param("year","2024").param("month","2").param("workerId",worker.id.toString()).header("Authorization",ownerToken))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals(10000,json.readTree(monthly).single()["totalPay"].asInt())
    }

    @Test fun `linked worker can read own wage but cannot write contracts or read another workers wage`() {
        val owner=owner();val account=user(UserRole.WORKER);val foreignAccount=user(UserRole.WORKER)
        val worker=Worker(name="Linked",phoneNumber="01012345678",owner=owner,userAccount=account).also(em::persist)
        Worker(name="Other linked",phoneNumber="01012345679",owner=owner,userAccount=foreignAccount).also(em::persist)
        val contract=WageContract(worker=worker,hourlyWage=java.math.BigDecimal("10000"),startDate=LocalDate.of(2024,1,1)).also(em::persist);em.flush()
        for(path in listOf("/api/workers/${worker.id}/wage?date=2024-02-01","/api/workers/${worker.id}/wage-contracts")) {
            mvc.perform(get(path).header("Authorization",token(account))).andExpect(status().isOk)
            mvc.perform(get(path).header("Authorization",token(foreignAccount))).andExpect(status().isNotFound)
        }
        mvc.perform(post("/api/workers/${worker.id}/wage-contracts").header("Authorization",token(account)).contentType(MediaType.APPLICATION_JSON)
            .content("""{"hourlyWage":20000,"startDate":"2025-01-01"}""")).andExpect(status().isForbidden)
        mvc.perform(patch("/api/workers/${worker.id}/wage-contracts/${contract.id}/terminate").param("endDate","2024-12-31").header("Authorization",token(account))).andExpect(status().isForbidden)
        em.flush();em.clear();assertNull(em.find(WageContract::class.java,contract.id).endDate)
    }

    @Test fun `default request fields and explicit owner clock review APIs preserve persisted values`() {
        val owner=owner();val authorization=token(owner.user)
        val created=mvc.perform(post("/api/stores").header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON).content("""{"name":"Defaults"}"""))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val storeId=UUID.fromString(json.readTree(created)["id"].asText())
        assertEquals("Asia/Seoul",json.readTree(created)["timezone"].asText());assertTrue(json.readTree(created)["address"].isNull)
        mvc.perform(put("/api/stores/$storeId/business-hours").header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON)
            .content("""{"hours":[{"dayOfWeek":"MONDAY","openTime":"09:00","closeTime":"18:00"}]}""")).andExpect(status().isOk)
        val monday=hours.getDayHours(storeId,DayOfWeek.MONDAY)!!;assertEquals(storeId,monday.store.id);assertFalse(monday.isClosed);assertEquals(monday.id,em.find(BusinessHours::class.java,monday.id).id)
        val worker=Worker(name="Explicit clock",phoneNumber="01012345678",owner=owner).also(em::persist);em.flush()
        val body=mvc.perform(post("/api/workers/${worker.id}/clock-in").header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON)
            .content("""{"clockInAt":"2024-02-01T09:00:00Z"}""")).andExpect(status().isCreated).andReturn().response.contentAsString
        val id=UUID.fromString(json.readTree(body)["id"].asText());em.flush()
        mvc.perform(post("/api/workers/${worker.id}/clock-out").header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON)
            .content("""{"clockOutAt":"2024-02-01T10:00:00Z"}""")).andExpect(status().isOk);em.flush()
        mvc.perform(post("/api/work-logs/$id/reject").header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON)
            .content("""{"reason":"  Incorrect time  "}""")).andExpect(status().isOk)
        em.flush();em.clear();assertEquals(WorkLogStatus.REJECTED,em.find(WorkLog::class.java,id).status);assertEquals("Incorrect time",em.find(WorkLog::class.java,id).rejectionReason)
        val listed=mvc.perform(get("/api/work-logs").param("workerId",worker.id.toString()).param("from","2024-02-01T00:00:00Z").param("to","2024-02-02T00:00:00Z").header("Authorization",authorization))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals(id.toString(),json.readTree(listed).single()["id"].asText())
    }
    @Test fun `legacy generic signup persists signed identity and missing notification user yields not found`() {
        val email="${UUID.randomUUID()}@test.invalid"
        val created=mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(mapOf("email" to email,"password" to "password123","name" to "Legacy","role" to "WORKER"))))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val node=json.readTree(created);val id=UUID.fromString(node["userId"].asText())
        assertEquals(id,jwt.getUserIdFromToken(node["accessToken"].asText()));assertEquals("WORKER",node["role"].asText())
        em.flush();em.clear();assertEquals(email,em.find(User::class.java,id).email)
        val absent=User(email="absent@test.invalid",password="pw",name="Absent",role=UserRole.WORKER)
        for(path in listOf("/api/notifications","/api/notifications/unread-count"))
            mvc.perform(get(path).header("Authorization",token(absent))).andExpect(status().isNotFound)
    }
}
