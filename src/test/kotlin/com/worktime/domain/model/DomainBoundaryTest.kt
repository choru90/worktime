package com.worktime.domain.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.*
import java.math.BigDecimal
import java.util.UUID

class DomainBoundaryTest {
    private val user=User(email="o@test",password="pw",name="O",role=UserRole.OWNER)
    private val owner=Owner(user=user)
    private val worker=Worker(name="Worker",phoneNumber="000",owner=owner)
    private val store=Store(owner=owner,name="Cafe")
    private val start=OffsetDateTime.parse("2024-01-01T09:00:00Z")
    @Test fun `entities use persistent identity for collection membership`() {
        val factories=listOf<(UUID)->Any>(
            { User(id=it,email="u@test",password="pw",name="U",role=UserRole.OWNER) },
            { Owner(id=it,user=user) },
            { Worker(id=it,name="W",phoneNumber="000",owner=owner) },
            { Store(id=it,owner=owner,name="Cafe") },
            { BusinessHours(id=it,store=store,dayOfWeek=DayOfWeek.MONDAY,openTime=LocalTime.NOON,closeTime=LocalTime.MAX) },
            { RegularShift(id=it,store=store,worker=worker,dayOfWeek=DayOfWeek.MONDAY,startTime=LocalTime.NOON,endTime=LocalTime.MAX) },
            { Schedule(id=it,worker=worker,startAtUtc=start,endAtUtc=start.plusHours(1),timezone="UTC") },
            { WorkLog(id=it,worker=worker,clockInAt=start) },
            { WageContract(id=it,worker=worker,hourlyWage=BigDecimal.TEN,startDate=start.toLocalDate()) },
            { Notification(id=it,user=user,title="Title",message="Message",type=NotificationType.SCHEDULE_PROPOSED) }
        )
        for(factory in factories) {
            val id=UUID.randomUUID();val a=factory(id);val same=factory(id);val other=factory(UUID.randomUUID())
            assertEquals(a,a);assertEquals(a,same);assertEquals(same,a);assertEquals(a.hashCode(),same.hashCode())
            assertEquals(1,setOf(a,same).size);assertEquals(2,setOf(a,other).size)
            assertNotEquals(a,other);assertFalse(a.equals(null));assertFalse(a.equals("foreign entity"))
            assertTrue(a.toString().contains(id.toString()))
        }
    }
    @Test fun `regular shifts include both effective endpoints and require matching weekday and active state`() {
        val date=LocalDate.of(2024,1,1)
        val shift=RegularShift(store=store,worker=worker,dayOfWeek=DayOfWeek.MONDAY,startTime=LocalTime.of(9,0),endTime=LocalTime.of(10,0))
        assertTrue(shift.isEffectiveOn(date));assertFalse(shift.isEffectiveOn(date.plusDays(1)))
        shift.setEffectivePeriod(date,date.plusDays(7))
        assertTrue(shift.isEffectiveOn(date));assertTrue(shift.isEffectiveOn(date.plusDays(7)))
        assertFalse(shift.isEffectiveOn(date.minusDays(7)));assertFalse(shift.isEffectiveOn(date.plusDays(14)))
        shift.deactivate();assertFalse(shift.isEffectiveOn(date));shift.activate();assertTrue(shift.isEffectiveOn(date))
        shift.setEffectivePeriod(null,date);assertTrue(shift.isEffectiveOn(date));assertFalse(shift.isEffectiveOn(date.plusDays(7)))
        shift.setEffectivePeriod(date,null);assertTrue(shift.isEffectiveOn(date.plusDays(7)))
        assertThrows<IllegalArgumentException> { shift.setEffectivePeriod(date.plusDays(1),date) }
        assertThrows<IllegalArgumentException> { shift.updateTime(LocalTime.NOON,LocalTime.NOON) }
        assertThrows<IllegalArgumentException> { shift.updateTime(LocalTime.MAX,LocalTime.MIN) }
        shift.updateTime(LocalTime.of(10,0),LocalTime.of(12,0));assertEquals(LocalTime.of(10,0),shift.startTime);assertEquals(LocalTime.NOON,shift.endTime)
        val other=Worker(name="Other",phoneNumber="001",owner=owner);shift.assignWorker(other);assertSame(other,shift.worker)
    }
    @Test fun `invitation is valid only with nonblank code and future expiry and is consumed by linking`() {
        assertFalse(worker.isInviteCodeValid())
        worker.inviteCode="ABC234";assertFalse(worker.isInviteCodeValid())
        worker.inviteCodeExpiresAt=OffsetDateTime.now().plusHours(1);assertTrue(worker.isInviteCodeValid())
        worker.inviteCode=" ";assertFalse(worker.isInviteCodeValid())
        worker.inviteCode="ABC234";worker.inviteCodeExpiresAt=OffsetDateTime.now().minusSeconds(1);assertFalse(worker.isInviteCodeValid())
        val before=OffsetDateTime.now().plusHours(24);val code=worker.generateInviteCode();val after=OffsetDateTime.now().plusHours(24)
        assertTrue(code.matches(Regex("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}")))
        assertFalse(worker.inviteCodeExpiresAt!!.isBefore(before));assertFalse(worker.inviteCodeExpiresAt!!.isAfter(after));assertTrue(worker.isInviteCodeValid())
        val account=User(email="w@test",password="pw",name="W",role=UserRole.WORKER)
        worker.linkUserAccount(account);assertSame(account,worker.userAccount);assertNull(worker.inviteCode);assertNull(worker.inviteCodeExpiresAt);assertFalse(worker.isInviteCodeValid())
    }
    @Test fun `schedule transitions enforce both pending states and preserve times on invalid update`() {
        for(status in ScheduleStatus.values()) {
            val schedule=Schedule(worker=worker,startAtUtc=start,endAtUtc=start.plusHours(1),timezone="UTC",status=status)
            if(status in listOf(ScheduleStatus.REQUESTED_BY_WORKER,ScheduleStatus.PROPOSED_BY_OWNER)) {
                schedule.confirm();assertEquals(ScheduleStatus.CONFIRMED,schedule.status)
                schedule.cancel();assertEquals(ScheduleStatus.CANCELED,schedule.status)
                schedule.status=status;schedule.reject();assertEquals(ScheduleStatus.REJECTED,schedule.status)
            } else {
                assertThrows<IllegalArgumentException> { schedule.confirm() }
                assertThrows<IllegalArgumentException> { schedule.reject() }
            }
            if(status!=ScheduleStatus.CONFIRMED) { schedule.status=status;assertThrows<IllegalArgumentException> { schedule.cancel() } }
        }
        val schedule=Schedule(worker=worker,startAtUtc=start,endAtUtc=start.plusHours(1),timezone="UTC")
        assertThrows<IllegalArgumentException> { schedule.updateTime(start,start,"UTC") };assertEquals(start.plusHours(1),schedule.endAtUtc)
        schedule.updateTime(start.plusHours(1),start.plusHours(2),"Asia/Seoul");assertEquals("Asia/Seoul",schedule.timezone);assertEquals(start.plusHours(2),schedule.endAtUtc)
    }
    @Test fun `manual work adjustment and ongoing predicate reflect actual clock-out and status`() {
        val log=WorkLog(worker=worker,clockInAt=start)
        assertTrue(log.isOngoing());log.clockOutAt=start.plusHours(1);assertFalse(log.isOngoing())
        log.clockOutAt=null;log.status=WorkLogStatus.COMPLETED;assertFalse(log.isOngoing())
        log.manuallyAdjust(start.minusHours(1),null);assertEquals(WorkLogStatus.MANUAL_ADJUSTED,log.status);assertEquals(start.minusHours(1),log.clockInAt);assertNull(log.calculateDuration())
        log.manuallyAdjust(start,start.plusMinutes(45));assertEquals(Duration.ofMinutes(45),log.calculateDuration())
    }

    private class AuditedEntity : BaseEntity() {
        fun restoreAudit(created: Instant,updated: Instant) { createdAt=created;updatedAt=updated }
    }
    @Test fun `audit base preserves distinct creation and modification timestamps for inheriting entities`() {
        val created=Instant.parse("2024-01-01T00:00:00Z");val modified=created.plusSeconds(3600)
        val entity=AuditedEntity();entity.restoreAudit(created,modified)
        assertEquals(created,entity.createdAt);assertEquals(modified,entity.updatedAt)
    }
}
