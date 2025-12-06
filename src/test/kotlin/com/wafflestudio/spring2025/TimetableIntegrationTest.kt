package com.wafflestudio.spring2025

import com.fasterxml.jackson.databind.ObjectMapper
import com.wafflestudio.spring2025.common.enum.Semester
import com.wafflestudio.spring2025.course.crawling.ClassPlaceAndTime
import com.wafflestudio.spring2025.course.crawling.DayOfWeek
import com.wafflestudio.spring2025.course.dto.CourseSearchResponse
import com.wafflestudio.spring2025.helper.DataGenerator
import com.wafflestudio.spring2025.helper.mock.MockRedis
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.Executors

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureMockMvc
@ContextConfiguration(initializers = [MockRedis::class])
class TimetableIntegrationTest
    @Autowired
    constructor(
        private val mvc: MockMvc,
        private val mapper: ObjectMapper,
        private val dataGenerator: DataGenerator,
    ) {
        @BeforeEach
        fun setUp() {
            dataGenerator.cleanupCourses()
        }

        // ========== 시간표 생성 테스트 ==========
        @Test
        fun `should create a timetable`() {
            // 시간표를 생성할 수 있다
            val (user, token) = dataGenerator.generateUser()
            val request =
                mapOf(
                    "name" to "2025-2 시간표",
                    "year" to 2025,
                    "semester" to "FALL",
                )

            mvc
                .perform(
                    post("/api/v1/timetables")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("2025-2 시간표"))
                .andExpect(jsonPath("$.year").value(2025))
                .andExpect(jsonPath("$.semester").value("FALL"))
        }

        @Test
        fun `should return error when creating timetable with blank name`() {
            // 빈 이름으로 시간표를 생성하면 에러를 반환한다
            val (user, token) = dataGenerator.generateUser()
            val request =
                mapOf(
                    "name" to "   ",
                    "year" to 2025,
                    "semester" to "FALL",
                )

            mvc
                .perform(
                    post("/api/v1/timetables")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return error when creating timetable with invalid year`() {
            // 잘못된 연도로 시간표를 생성하면 에러를 반환한다
            val (user, token) = dataGenerator.generateUser()
            val request =
                mapOf(
                    "name" to "2030 시간표",
                    "year" to 2030,
                    "semester" to "FALL",
                )

            mvc
                .perform(
                    post("/api/v1/timetables")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return error when creating duplicate timetable`() {
            // 중복된 시간표를 생성하면 에러를 반환한다
            val (user, token) = dataGenerator.generateUser()
            dataGenerator.generateTimetable(name = "2025-2 시간표", year = 2025, semester = Semester.FALL, user = user)

            val request =
                mapOf(
                    "name" to "2025-2 시간표",
                    "year" to 2025,
                    "semester" to "FALL",
                )

            mvc
                .perform(
                    post("/api/v1/timetables")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)),
                ).andExpect(status().isConflict)
        }

        // ========== 시간표 전체 조회 테스트 ==========
        @Test
        fun `should retrieve all own timetables`() {
            // 자신의 모든 시간표 목록을 조회할 수 있다
            val (user, token) = dataGenerator.generateUser()
            dataGenerator.generateTimetable(name = "2025-1 시간표", year = 2025, semester = Semester.SPRING, user = user)
            dataGenerator.generateTimetable(name = "2025-2 시간표", year = 2025, semester = Semester.FALL, user = user)

            mvc
                .perform(
                    get("/api/v1/timetables")
                        .header("Authorization", "Bearer $token"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[1].name").exists())
        }

        @Test
        fun `should return empty list when user has no timetables`() {
            // 시간표가 없는 유저는 빈 리스트를 조회한다
            val (user, token) = dataGenerator.generateUser()

            mvc
                .perform(
                    get("/api/v1/timetables")
                        .header("Authorization", "Bearer $token"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        // ========== 시간표 상세 조회 테스트 ==========
        @Test
        fun `should retrieve timetable details`() {
            // 시간표 상세 정보를 조회할 수 있다
            val (user, token) = dataGenerator.generateUser()
            val timetable = dataGenerator.generateTimetable(name = "2025-2 시간표", year = 2025, semester = Semester.FALL, user = user)

            mvc
                .perform(
                    get("/api/v1/timetables/${timetable.id}")
                        .header("Authorization", "Bearer $token"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.timetable.id").value(timetable.id!!))
                .andExpect(jsonPath("$.timetable.name").value("2025-2 시간표"))
                .andExpect(jsonPath("$.courses").isArray)
                .andExpect(jsonPath("$.credits").exists())
        }

        @Test
        fun `should return error when retrieving non-existent timetable`() {
            // 존재하지 않는 시간표를 조회하면 에러를 반환한다
            val (user, token) = dataGenerator.generateUser()

            mvc
                .perform(
                    get("/api/v1/timetables/999999")
                        .header("Authorization", "Bearer $token"),
                ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return correct course list and total credits when retrieving timetable details`() {
            // 시간표 상세 조회 시, 강의 정보 목록과 총 학점이 올바르게 반환된다
            val (user, token) = dataGenerator.generateUser()
            val timetable = dataGenerator.generateTimetable(name = "2025-2 시간표", year = 2025, semester = Semester.FALL, user = user)

            val course1 = dataGenerator.generateCourse(year = 2025, semester = Semester.FALL, courseTitle = "자료구조", credit = 3)
            val course2 = dataGenerator.generateCourse(year = 2025, semester = Semester.FALL, courseTitle = "알고리즘", credit = 3)
            val course3 = dataGenerator.generateCourse(year = 2025, semester = Semester.FALL, courseTitle = "데이터베이스", credit = 4)

            dataGenerator.generateEnroll(timetable, course1)
            dataGenerator.generateEnroll(timetable, course2)
            dataGenerator.generateEnroll(timetable, course3)

            mvc
                .perform(
                    get("/api/v1/timetables/${timetable.id}")
                        .header("Authorization", "Bearer $token"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.courses.length()").value(3))
                .andExpect(jsonPath("$.credits").value(10)) // 3 + 3 + 4 = 10
        }

        // ========== 시간표 수정 테스트 ==========
        @Test
        fun `should update timetable name`() {
            // 시간표 이름을 수정할 수 있다
            val (user, token) = dataGenerator.generateUser()
            val timetable = dataGenerator.generateTimetable(name = "기존 시간표", user = user)
            val request = mapOf("name" to "새로운 시간표")

            mvc
                .perform(
                    patch("/api/v1/timetables/${timetable.id}")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(timetable.id!!))
                .andExpect(jsonPath("$.name").value("새로운 시간표"))
        }

        @Test
        fun `should not update another user's timetable`() {
            // 다른 사람의 시간표는 수정할 수 없다
            val (user1, token1) = dataGenerator.generateUser()
            val (user2, token2) = dataGenerator.generateUser()
            val timetable = dataGenerator.generateTimetable(name = "user1 시간표", user = user1)
            val request = mapOf("name" to "해킹 시도")

            mvc
                .perform(
                    patch("/api/v1/timetables/${timetable.id}")
                        .header("Authorization", "Bearer $token2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)),
                ).andExpect(status().isForbidden)
        }

        @Test
        fun `should return error when updating timetable with blank name`() {
            // 빈 이름으로 시간표를 수정하면 에러를 반환한다
            val (user, token) = dataGenerator.generateUser()
            val timetable = dataGenerator.generateTimetable(name = "기존 시간표", user = user)
            val request = mapOf("name" to "  ")

            mvc
                .perform(
                    patch("/api/v1/timetables/${timetable.id}")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return error when updating non-existent timetable`() {
            // 존재하지 않는 시간표를 수정하면 에러를 반환한다
            val (user, token) = dataGenerator.generateUser()
            val request = mapOf("name" to "새 이름")

            mvc
                .perform(
                    patch("/api/v1/timetables/999999")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)),
                ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return error when updating timetable with duplicate name`() {
            // 중복된 이름으로 시간표를 수정하면 에러를 반환한다
            val (user, token) = dataGenerator.generateUser()
            dataGenerator.generateTimetable(name = "2025-1 시간표", year = 2025, semester = Semester.SPRING, user = user)
            val timetable2 = dataGenerator.generateTimetable(name = "2025-2 시간표", year = 2025, semester = Semester.SPRING, user = user)
            val request = mapOf("name" to "2025-1 시간표")

            mvc
                .perform(
                    patch("/api/v1/timetables/${timetable2.id}")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)),
                ).andExpect(status().isConflict)
        }

        // ========== 시간표 삭제 테스트 ==========
        @Test
        fun `should delete a timetable`() {
            // 시간표를 삭제할 수 있다
            val (user, token) = dataGenerator.generateUser()
            val timetable = dataGenerator.generateTimetable(name = "삭제할 시간표", user = user)

            mvc
                .perform(
                    delete("/api/v1/timetables/${timetable.id}")
                        .header("Authorization", "Bearer $token"),
                ).andExpect(status().isNoContent)
        }

        @Test
        fun `should not delete another user's timetable`() {
            // 다른 사람의 시간표는 삭제할 수 없다
            val (user1, token1) = dataGenerator.generateUser()
            val (user2, token2) = dataGenerator.generateUser()
            val timetable = dataGenerator.generateTimetable(name = "user1 시간표", user = user1)

            mvc
                .perform(
                    delete("/api/v1/timetables/${timetable.id}")
                        .header("Authorization", "Bearer $token2"),
                ).andExpect(status().isForbidden)
        }

        @Test
        fun `should return error when deleting non-existent timetable`() {
            // 존재하지 않는 시간표를 삭제하면 에러를 반환한다
            val (user, token) = dataGenerator.generateUser()

            mvc
                .perform(
                    delete("/api/v1/timetables/999999")
                        .header("Authorization", "Bearer $token"),
                ).andExpect(status().isNotFound)
        }

        // ========== 강의 추가 테스트 ==========
        @Test
        fun `should add a course to timetable`() {
            // 시간표에 강의를 추가할 수 있다
            val (user, token) = dataGenerator.generateUser()
            val timetable = dataGenerator.generateTimetable(name = "2025-2 시간표", year = 2025, semester = Semester.FALL, user = user)
            val course =
                dataGenerator.generateCourse(
                    year = 2025,
                    semester = Semester.FALL,
                    courseTitle = "자료구조",
                    credit = 3,
                    classTimeJson =
                        listOf(
                            ClassPlaceAndTime(day = DayOfWeek.MONDAY, place = "302-308", startMinute = 540, endMinute = 630),
                        ),
                )

            mvc
                .perform(
                    post("/api/v1/timetables/${timetable.id}/courses/${course.id}")
                        .header("Authorization", "Bearer $token"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.courseTitle").value("자료구조"))
                .andExpect(jsonPath("$.credit").value(3))
        }

        @Test
        fun `should return error when adding overlapping course to timetable`() {
            // 시간표에 강의 추가 시, 시간이 겹치면 에러를 반환한다
            val (user, token) = dataGenerator.generateUser()
            val timetable = dataGenerator.generateTimetable(name = "2025-2 시간표", year = 2025, semester = Semester.FALL, user = user)

            val course1 =
                dataGenerator.generateCourse(
                    year = 2025,
                    semester = Semester.FALL,
                    courseTitle = "자료구조",
                    classTimeJson =
                        listOf(
                            ClassPlaceAndTime(day = DayOfWeek.MONDAY, place = "302-308", startMinute = 540, endMinute = 630),
                        ),
                )
            val course2 =
                dataGenerator.generateCourse(
                    year = 2025,
                    semester = Semester.FALL,
                    courseTitle = "알고리즘",
                    classTimeJson =
                        listOf(
                            ClassPlaceAndTime(day = DayOfWeek.MONDAY, place = "302-309", startMinute = 600, endMinute = 690),
                        ),
                )

            // 첫 번째 강의 추가 성공
            mvc
                .perform(
                    post("/api/v1/timetables/${timetable.id}/courses/${course1.id}")
                        .header("Authorization", "Bearer $token"),
                ).andExpect(status().isOk)

            // 두 번째 강의 추가 실패 (시간 겹침)
            mvc
                .perform(
                    post("/api/v1/timetables/${timetable.id}/courses/${course2.id}")
                        .header("Authorization", "Bearer $token"),
                ).andExpect(status().isConflict)
        }

        @Test
        fun `should return error when concurrently adding overlapping course to timetable`() {
            // 시간표에 강의 추가 요청을 동시에 보낼 때, 시간이 겹치면 에러를 반환한다
            val (user, token) = dataGenerator.generateUser()
            val timetable = dataGenerator.generateTimetable(name = "2025-2 시간표", year = 2025, semester = Semester.FALL, user = user)

            val course1 =
                dataGenerator.generateCourse(
                    year = 2025,
                    semester = Semester.FALL,
                    courseTitle = "자료구조",
                    classTimeJson =
                        listOf(
                            ClassPlaceAndTime(day = DayOfWeek.MONDAY, place = "302-308", startMinute = 540, endMinute = 630),
                        ),
                )
            val course2 =
                dataGenerator.generateCourse(
                    year = 2025,
                    semester = Semester.FALL,
                    courseTitle = "알고리즘",
                    classTimeJson =
                        listOf(
                            ClassPlaceAndTime(day = DayOfWeek.MONDAY, place = "302-309", startMinute = 600, endMinute = 690),
                        ),
                )

            val threadPool = Executors.newFixedThreadPool(2)

            val jobs =
                listOf(
                    threadPool.submit<Int> {
                        mvc
                            .perform(
                                post("/api/v1/timetables/${timetable.id}/courses/${course1.id}")
                                    .header("Authorization", "Bearer $token"),
                            ).andReturn()
                            .response.status
                    },
                    threadPool.submit<Int> {
                        mvc
                            .perform(
                                post("/api/v1/timetables/${timetable.id}/courses/${course2.id}")
                                    .header("Authorization", "Bearer $token"),
                            ).andReturn()
                            .response.status
                    },
                )

            val statusCodes = jobs.map { it.get() }
            threadPool.shutdown()

            val successCount = statusCodes.count { it == 200 }
            val conflictCount = statusCodes.count { it == 409 }

            assertThat(successCount).isEqualTo(1)
            assertThat(conflictCount).isEqualTo(1)
        }

        @Test
        fun `should not add a course to another user's timetable`() {
            // 다른 사람의 시간표에는 강의를 추가할 수 없다
            val (user1, token1) = dataGenerator.generateUser()
            val (user2, token2) = dataGenerator.generateUser()
            val timetable = dataGenerator.generateTimetable(name = "user1 시간표", year = 2025, semester = Semester.FALL, user = user1)
            val course = dataGenerator.generateCourse(year = 2025, semester = Semester.FALL, courseTitle = "자료구조")

            mvc
                .perform(
                    post("/api/v1/timetables/${timetable.id}/courses/${course.id}")
                        .header("Authorization", "Bearer $token2"),
                ).andExpect(status().isForbidden)
        }

        @Test
        fun `should return error when adding course with mismatched year or semester`() {
            // 시간표와 년도/학기가 다른 강의를 추가하면 에러를 반환한다
            val (user, token) = dataGenerator.generateUser()
            val timetable = dataGenerator.generateTimetable(name = "2025-2 시간표", year = 2025, semester = Semester.FALL, user = user)
            val course = dataGenerator.generateCourse(year = 2024, semester = Semester.SPRING, courseTitle = "자료구조")

            mvc
                .perform(
                    post("/api/v1/timetables/${timetable.id}/courses/${course.id}")
                        .header("Authorization", "Bearer $token"),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return error when adding non-existent course to timetable`() {
            // 존재하지 않는 강의를 추가하면 에러를 반환한다
            val (user, token) = dataGenerator.generateUser()
            val timetable = dataGenerator.generateTimetable(name = "2025-2 시간표", year = 2025, semester = Semester.FALL, user = user)

            mvc
                .perform(
                    post("/api/v1/timetables/${timetable.id}/courses/999999")
                        .header("Authorization", "Bearer $token"),
                ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return error when adding course to non-existent timetable`() {
            // 존재하지 않는 시간표에 강의를 추가하면 에러를 반환한다
            val (user, token) = dataGenerator.generateUser()
            val course = dataGenerator.generateCourse(year = 2025, semester = Semester.FALL, courseTitle = "자료구조")

            mvc
                .perform(
                    post("/api/v1/timetables/999999/courses/${course.id}")
                        .header("Authorization", "Bearer $token"),
                ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return error when adding duplicate course to timetable`() {
            // 이미 추가된 강의를 다시 추가하면 에러를 반환한다
            val (user, token) = dataGenerator.generateUser()
            val timetable = dataGenerator.generateTimetable(name = "2025-2 시간표", year = 2025, semester = Semester.FALL, user = user)
            val course =
                dataGenerator.generateCourse(
                    year = 2025,
                    semester = Semester.FALL,
                    courseTitle = "자료구조",
                    classTimeJson =
                        listOf(
                            ClassPlaceAndTime(day = DayOfWeek.MONDAY, place = "302-308", startMinute = 540, endMinute = 630),
                        ),
                )

            // 첫 번째 추가 성공
            mvc
                .perform(
                    post("/api/v1/timetables/${timetable.id}/courses/${course.id}")
                        .header("Authorization", "Bearer $token"),
                ).andExpect(status().isOk)

            // 두 번째 추가 실패 (중복)
            mvc
                .perform(
                    post("/api/v1/timetables/${timetable.id}/courses/${course.id}")
                        .header("Authorization", "Bearer $token"),
                ).andExpect(status().isConflict)
        }

        // ========== 강의 삭제 테스트 ==========
        @Test
        fun `should remove a course from timetable`() {
            // 시간표에서 강의를 삭제할 수 있다
            val (user, token) = dataGenerator.generateUser()
            val timetable = dataGenerator.generateTimetable(name = "2025-2 시간표", year = 2025, semester = Semester.FALL, user = user)
            val course = dataGenerator.generateCourse(year = 2025, semester = Semester.FALL, courseTitle = "자료구조")
            dataGenerator.generateEnroll(timetable, course)

            mvc
                .perform(
                    delete("/api/v1/timetables/${timetable.id}/courses/${course.id}")
                        .header("Authorization", "Bearer $token"),
                ).andExpect(status().isNoContent)
        }

        @Test
        fun `should not remove a course from another user's timetable`() {
            // 다른 사람의 시간표에서는 강의를 삭제할 수 없다
            val (user1, token1) = dataGenerator.generateUser()
            val (user2, token2) = dataGenerator.generateUser()
            val timetable = dataGenerator.generateTimetable(name = "user1 시간표", year = 2025, semester = Semester.FALL, user = user1)
            val course = dataGenerator.generateCourse(year = 2025, semester = Semester.FALL, courseTitle = "자료구조")
            dataGenerator.generateEnroll(timetable, course)

            mvc
                .perform(
                    delete("/api/v1/timetables/${timetable.id}/courses/${course.id}")
                        .header("Authorization", "Bearer $token2"),
                ).andExpect(status().isForbidden)
        }

        @Test
        fun `should return error when removing non-existent course from timetable`() {
            // 시간표에 없는 강의를 삭제하면 에러를 반환한다
            val (user, token) = dataGenerator.generateUser()
            val timetable = dataGenerator.generateTimetable(name = "2025-2 시간표", year = 2025, semester = Semester.FALL, user = user)
            val course = dataGenerator.generateCourse(year = 2025, semester = Semester.FALL, courseTitle = "자료구조")

            mvc
                .perform(
                    delete("/api/v1/timetables/${timetable.id}/courses/${course.id}")
                        .header("Authorization", "Bearer $token"),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return error when removing course from non-existent timetable`() {
            // 존재하지 않는 시간표에서 강의를 삭제하면 에러를 반환한다
            val (user, token) = dataGenerator.generateUser()
            val course = dataGenerator.generateCourse(year = 2025, semester = Semester.FALL, courseTitle = "자료구조")

            mvc
                .perform(
                    delete("/api/v1/timetables/999999/courses/${course.id}")
                        .header("Authorization", "Bearer $token"),
                ).andExpect(status().isNotFound)
        }

        // Course 검색 테스트 ====================================================

        @Test
        fun `should search courses by year and semester without keyword`() {
            // 검색어 없이 연도와 학기로 강의를 검색할 수 있다
            val (_, token) = dataGenerator.generateUser()

            repeat(2) {
                dataGenerator.generateCourse(
                    year = 2025,
                    semester = Semester.FALL,
                    courseTitle = "2025 가을 강의 $it",
                )
            }
            repeat(2) {
                dataGenerator.generateCourse(
                    year = 2024,
                    semester = Semester.SPRING,
                    courseTitle = "2024 봄 강의 $it",
                )
            }
            repeat(2) {
                dataGenerator.generateCourse(
                    year = 2025,
                    semester = Semester.SUMMER,
                    courseTitle = "2025 여름 강의 $it",
                )
            }

            mvc
                .perform(
                    get("/api/v1/courses")
                        .header("Authorization", "Bearer $token")
                        .param("year", "2025")
                        .param("semester", "FALL")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.hasNext").value(false))
        }

        @Test
        fun `should search courses by keyword in course title`() {
            // 강의명으로 강의를 검색할 수 있다
            val (_, token) = dataGenerator.generateUser()

            dataGenerator.generateCourse(courseTitle = "데이터구조", instructor = "홍길동")
            dataGenerator.generateCourse(courseTitle = "알고리즘", instructor = "김철수")
            dataGenerator.generateCourse(courseTitle = "데이터베이스", instructor = "박영희")

            mvc
                .perform(
                    get("/api/v1/courses")
                        .header("Authorization", "Bearer $token")
                        .param("year", "2025")
                        .param("semester", "FALL")
                        .param("keyword", "데이터")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.hasNext").value(false))
        }

        @Test
        fun `should search courses by keyword in instructor name`() {
            // 교수명으로 강의를 검색할 수 있다
            val (_, token) = dataGenerator.generateUser()

            dataGenerator.generateCourse(courseTitle = "데이터구조", instructor = "홍길동")
            dataGenerator.generateCourse(courseTitle = "알고리즘", instructor = "김철수")
            dataGenerator.generateCourse(courseTitle = "운영체제", instructor = "홍길동")

            mvc
                .perform(
                    get("/api/v1/courses")
                        .header("Authorization", "Bearer $token")
                        .param("year", "2025")
                        .param("semester", "FALL")
                        .param("keyword", "홍길동")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.hasNext").value(false))
        }

        @Test
        fun `should paginate correctly when searching for courses2`() {
            // 강의 검색 시, 페이지네이션이 올바르게 동작한다
            val (_, token) = dataGenerator.generateUser()

            repeat(50) {
                dataGenerator.generateCourse(courseTitle = "강의 $it", instructor = "교수 $it")
            }

            // 첫 번째 페이지
            val firstResponse =
                mvc
                    .perform(
                        get("/api/v1/courses")
                            .header("Authorization", "Bearer $token")
                            .param("year", "2025")
                            .param("semester", "FALL")
                            .contentType(MediaType.APPLICATION_JSON),
                    ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.data.length()").value(20))
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andReturn()
                    .response
                    .getContentAsString(Charsets.UTF_8)
                    .let {
                        mapper.readValue(it, CourseSearchResponse::class.java)
                    }

            // 두 번째 페이지
            val secondResponse =
                mvc
                    .perform(
                        get("/api/v1/courses")
                            .header("Authorization", "Bearer $token")
                            .param("year", "2025")
                            .param("semester", "FALL")
                            .param("nextId", firstResponse.nextId.toString())
                            .contentType(MediaType.APPLICATION_JSON),
                    ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.data.length()").value(20))
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andReturn()
                    .response
                    .getContentAsString(Charsets.UTF_8)
                    .let {
                        mapper.readValue(it, CourseSearchResponse::class.java)
                    }

            // 세 번째 페이지 (마지막)
            mvc
                .perform(
                    get("/api/v1/courses")
                        .header("Authorization", "Bearer $token")
                        .param("year", "2025")
                        .param("semester", "FALL")
                        .param("nextId", secondResponse.nextId.toString())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.length()").value(10))
                .andExpect(jsonPath("$.hasNext").value(false))
        }

        @Test
        fun `should return error for invalid year`() {
            // 유효하지 않은 연도는 검색할 수 없다 (2013년 이전, 미래 연도)
            val (_, token) = dataGenerator.generateUser()
            val futureYear =
                java.time.LocalDate
                    .now()
                    .year + 1

            // 2012년 (2013년 이전)
            mvc
                .perform(
                    get("/api/v1/courses")
                        .header("Authorization", "Bearer $token")
                        .param("year", "2012")
                        .param("semester", "FALL")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isBadRequest)

            // 미래 연도
            mvc
                .perform(
                    get("/api/v1/courses")
                        .header("Authorization", "Bearer $token")
                        .param("year", futureYear.toString())
                        .param("semester", "FALL")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should retrieve a single course by id`() {
            // 강의 ID로 강의를 단건 조회할 수 있다
            val (_, token) = dataGenerator.generateUser()
            val course = dataGenerator.generateCourse(courseTitle = "데이터구조", instructor = "홍길동", credit = 3L)

            mvc
                .perform(
                    get("/api/v1/courses/${course.id}")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(course.id!!))
                .andExpect(jsonPath("$.courseTitle").value("데이터구조"))
                .andExpect(jsonPath("$.instructor").value("홍길동"))
                .andExpect(jsonPath("$.credit").value(3))
        }

        @Test
        fun `should return 404 when course not found`() {
            // 존재하지 않는 강의 조회 시 404 에러를 반환한다
            val (_, token) = dataGenerator.generateUser()

            mvc
                .perform(
                    get("/api/v1/courses/99999")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isNotFound)
        }
    }
