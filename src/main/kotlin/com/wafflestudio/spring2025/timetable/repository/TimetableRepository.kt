package com.wafflestudio.spring2025.timetable.repository

import com.wafflestudio.spring2025.common.enum.Semester
import com.wafflestudio.spring2025.timetable.model.Timetable
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.ListCrudRepository

interface TimetableRepository : ListCrudRepository<Timetable, Long> {
    fun findByUserId(userId: Long): List<Timetable>

    fun existsByUserIdAndYearAndSemesterAndName(
        userId: Long,
        year: Int,
        semester: Semester,
        name: String,
    ): Boolean

    @Query("SELECT * FROM timetables WHERE id = :id FOR UPDATE")
    fun findByIdWithLock(id: Long): Timetable?
}
