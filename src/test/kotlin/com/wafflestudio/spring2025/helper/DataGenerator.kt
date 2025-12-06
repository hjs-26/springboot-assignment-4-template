package com.wafflestudio.spring2025.helper

import com.wafflestudio.spring2025.board.model.Board
import com.wafflestudio.spring2025.board.repository.BoardRepository
import com.wafflestudio.spring2025.comment.model.Comment
import com.wafflestudio.spring2025.comment.repository.CommentRepository
import com.wafflestudio.spring2025.common.enum.Semester
import com.wafflestudio.spring2025.course.crawling.ClassPlaceAndTime
import com.wafflestudio.spring2025.course.model.Course
import com.wafflestudio.spring2025.course.repository.CourseRepository
import com.wafflestudio.spring2025.post.model.Post
import com.wafflestudio.spring2025.post.repository.PostRepository
import com.wafflestudio.spring2025.postlike.model.PostLike
import com.wafflestudio.spring2025.postlike.repository.PostLikeRepository
import com.wafflestudio.spring2025.timetable.model.Enroll
import com.wafflestudio.spring2025.timetable.model.Timetable
import com.wafflestudio.spring2025.timetable.repository.EnrollRepository
import com.wafflestudio.spring2025.timetable.repository.TimetableRepository
import com.wafflestudio.spring2025.user.JwtTokenProvider
import com.wafflestudio.spring2025.user.model.User
import com.wafflestudio.spring2025.user.repository.UserRepository
import org.mindrot.jbcrypt.BCrypt
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

@Component
class DataGenerator(
    private val userRepository: UserRepository,
    private val boardRepository: BoardRepository,
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val timetableRepository: TimetableRepository,
    private val courseRepository: CourseRepository,
    private val enrollRepository: EnrollRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val postLikeRepository: PostLikeRepository,
) {
    fun generateUser(
        username: String? = null,
        password: String? = null,
    ): Pair<User, String> {
        val user =
            userRepository.save(
                User(
                    username = username ?: "user-${Random.nextInt(1000000)}",
                    password = BCrypt.hashpw(password ?: "password-${Random.nextInt(1000000)}", BCrypt.gensalt()),
                ),
            )
        return user to jwtTokenProvider.createToken(user.username)
    }

    fun generateBoard(name: String? = null): Board {
        val board =
            boardRepository.save(
                Board(
                    name = name ?: "board-${Random.nextInt(1000000)}",
                ),
            )
        return board
    }

    fun generatePost(
        title: String? = null,
        content: String? = null,
        user: User? = null,
        board: Board? = null,
    ): Post {
        val post =
            postRepository.save(
                Post(
                    title = title ?: "title-${Random.nextInt(1000000)}",
                    content = content ?: "content-${Random.nextInt(1000000)}",
                    userId = (user ?: generateUser().first).id!!,
                    boardId = (board ?: generateBoard()).id!!,
                ),
            )
        return post
    }

    fun generateComment(
        content: String? = null,
        user: User? = null,
        post: Post? = null,
    ): Comment {
        val comment =
            commentRepository.save(
                Comment(
                    content = content ?: "content-${Random.nextInt(1000000)}",
                    userId = (user ?: generateUser().first).id!!,
                    postId = (post ?: generatePost()).id!!,
                ),
            )
        return comment
    }

    fun generateTimetable(
        name: String? = null,
        year: Int? = null,
        semester: Semester? = null,
        user: User? = null,
    ): Timetable {
        val timetable =
            timetableRepository.save(
                Timetable(
                    userId = (user ?: generateUser().first).id!!,
                    name = name ?: "timetable-${Random.nextInt(1000000)}",
                    year = year ?: 2025,
                    semester = semester ?: Semester.FALL,
                ),
            )
        return timetable
    }

    companion object {
        private val idCounter = AtomicLong(1L)
    }

    fun generateCourse(
        year: Int? = null,
        semester: Semester? = null,
        classification: String? = null,
        college: String? = null,
        department: String? = null,
        academicCourse: String? = null,
        academicYear: String? = null,
        courseNumber: String? = null,
        lectureNumber: String? = null,
        courseTitle: String? = null,
        credit: Long? = null,
        instructor: String? = null,
        classTimeJson: List<ClassPlaceAndTime>? = null,
    ): Course {
        val nextId = idCounter.getAndIncrement()
        val course =
            courseRepository.save(
                Course(
                    year = year ?: 2025,
                    semester = semester ?: Semester.FALL,
                    classification = classification,
                    college = college,
                    department = department,
                    academicCourse = academicCourse,
                    academicYear = academicYear,
                    courseNumber = courseNumber ?: "L0000-$nextId",
                    lectureNumber = lectureNumber ?: "001",
                    courseTitle = courseTitle ?: "강의-$nextId",
                    credit = credit ?: 3L,
                    instructor = instructor,
                    classTimeJson = classTimeJson,
                ),
            )
        return course
    }

    fun cleanupCourses() {
        courseRepository.deleteAll()
    }

    fun generateEnroll(
        timetable: Timetable,
        course: Course,
    ): Enroll {
        val enroll =
            enrollRepository.save(
                Enroll(
                    timetableId = timetable.id!!,
                    courseId = course.id!!,
                ),
            )
        return enroll
    }

    fun likePost(
        post: Post? = null,
        user: User? = null,
    ) {
        // 좋아요를 누를 사용자 (없으면 새로 생성)
        val likeUser = user ?: generateUser().first
        // 좋아요를 누를 게시글 (없으면 새로 생성)
        val targetPost = post ?: generatePost()

        // PostLike 객체 생성
        val postLike =
            PostLike(
                userId = likeUser.id!!,
                postId = targetPost.id!!,
            )

        val updatedPost =
            targetPost.apply {
                likeCount++
            }
        // DB에 저장
        postRepository.save(updatedPost)
        postLikeRepository.save(postLike)
    }
}
