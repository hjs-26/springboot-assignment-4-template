package com.wafflestudio.spring2025

import com.fasterxml.jackson.databind.ObjectMapper
import com.wafflestudio.spring2025.board.model.Board
import com.wafflestudio.spring2025.helper.DataGenerator
import com.wafflestudio.spring2025.helper.QueryCounter
import com.wafflestudio.spring2025.helper.mock.MockRedis
import com.wafflestudio.spring2025.post.dto.CreatePostRequest
import com.wafflestudio.spring2025.post.dto.PostPagingResponse
import com.wafflestudio.spring2025.post.dto.UpdatePostRequest
import com.wafflestudio.spring2025.post.dto.core.PostDto
import com.wafflestudio.spring2025.post.model.Post
import com.wafflestudio.spring2025.post.service.PostService
import org.junit.jupiter.api.Assertions.assertTrue
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

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureMockMvc
@ContextConfiguration(initializers = [MockRedis::class])
class PostIntegrationTest
    @Autowired
    constructor(
        private val dataGenerator: DataGenerator,
        private val queryCounter: QueryCounter,
        private val mvc: MockMvc,
        private val mapper: ObjectMapper,
        private val postService: PostService,
    ) {
        @Test
        fun `should create a post`() {
            // 게시글을 생성할 수 있다
            // given
            val board = dataGenerator.generateBoard()
            val (user, token) = dataGenerator.generateUser()

            val request =
                mapOf(
                    "title" to "새 게시글 제목",
                    "content" to "새 게시글 내용",
                )

            // when & then
            mvc
                .perform(
                    post("/api/v1/boards/${board.id!!}/posts")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value(request["title"]))
                .andExpect(jsonPath("$.content").value(request["content"]))
                .andExpect(jsonPath("$.user.id").value(user.id!!))
                .andExpect(jsonPath("$.board.id").value(board.id!!))
        }

        @Test
        fun `should not create a post with blank title or content`() {
            // 빈 제목이나 내용으로 게시글을 생성할 수 없다
            // given
            val board = dataGenerator.generateBoard()
            val (user, token) = dataGenerator.generateUser()

            val requestWithBlankTitle =
                CreatePostRequest(
                    title = " ",
                    content = "게시글 내용",
                )

            val requestWithBlankContent =
                CreatePostRequest(
                    title = "게시글 제목",
                    content = " ",
                )

            // when & then
            mvc
                .perform(
                    post("/api/v1/boards/${board.id!!}/posts")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestWithBlankTitle)),
                ).andExpect(status().isBadRequest)

            mvc
                .perform(
                    post("/api/v1/boards/${board.id!!}/posts")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestWithBlankContent)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should retrieve a single post`() {
            // 게시글을 단건 조회할 수 있다
            // given
            val post = dataGenerator.generatePost()
            val (_, token) = dataGenerator.generateUser()

            // when & then
            mvc
                .perform(
                    get("/api/v1/posts/${post.id!!}")
                        .header("Authorization", "Bearer $token"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(post.id!!))
                .andExpect(jsonPath("$.title").value(post.title))
                .andExpect(jsonPath("$.content").value(post.content))
                .andExpect(jsonPath("$.user.id").value(post.userId))
                .andExpect(jsonPath("$.board.id").value(post.boardId))
        }

        @Test
        fun `should update a post`() {
            // 게시글을 수정할 수 있다
            // given
            val (user, token) = dataGenerator.generateUser()
            val post = dataGenerator.generatePost(user = user)
            val request = UpdatePostRequest("new title", null)

            // when & then
            mvc
                .perform(
                    patch("/api/v1/posts/${post.id!!}")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(post.id!!))
                .andExpect(jsonPath("$.title").value(request.title))
                .andExpect(jsonPath("$.content").value(post.content)) // 내용은 그대로
                .andExpect(jsonPath("$.user.id").value(post.userId))
                .andExpect(jsonPath("$.board.id").value(post.boardId))
        }

        @Test
        fun `should not update a post with blank title or content`() {
            // 빈 제목이나 내용으로 게시글을 수정할 수 없다
            // given
            val (user, token) = dataGenerator.generateUser()
            val post = dataGenerator.generatePost(user = user)
            val requestWithBlankTitle = UpdatePostRequest(title = " ", content = "new content")
            val requestWithBlankContent = UpdatePostRequest(title = "new title", content = " ")

            // when & then
            mvc
                .perform(
                    patch("/api/v1/posts/${post.id!!}")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestWithBlankTitle)),
                ).andExpect(status().isBadRequest)

            mvc
                .perform(
                    patch("/api/v1/posts/${post.id!!}")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestWithBlankContent)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should not update a post written by another user`() {
            // 다른 사람이 작성한 게시글은 수정할 수 없다
            // given
            val post = dataGenerator.generatePost() // user1 이 작성
            val (_, otherToken) = dataGenerator.generateUser() // user2 생성
            val request = UpdatePostRequest("new title", null)

            // when & then
            mvc
                .perform(
                    patch("/api/v1/posts/${post.id!!}")
                        .header("Authorization", "Bearer $otherToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)),
                ).andExpect(status().isForbidden)
        }

        @Test
        fun `should delete a post`() {
            // 게시글을 삭제할 수 있다
            // given
            val (user, token) = dataGenerator.generateUser()
            val post = dataGenerator.generatePost(user = user)

            mvc
                .perform(
                    delete("/api/v1/posts/${post.id!!}")
                        .header("Authorization", "Bearer $token"),
                ).andExpect(status().isNoContent)

            // when & then
            mvc
                .perform(
                    get("/api/v1/posts/${post.id!!}")
                        .header("Authorization", "Bearer $token"),
                ).andExpect(status().isNotFound)
        }

        @Test
        fun `should not delete a post written by another user`() {
            // 다른 사람이 작성한 게시글은 삭제할 수 없다
            // given
            val post = dataGenerator.generatePost() // user1 이 작성
            val (_, otherToken) = dataGenerator.generateUser() // user2 생성
            val request = UpdatePostRequest("new title", null)

            // when & then
            mvc
                .perform(
                    delete("/api/v1/posts/${post.id!!}")
                        .header("Authorization", "Bearer $otherToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)),
                ).andExpect(status().isForbidden)
        }

        @Test
        fun `should paginate posts using created_at and id as cursor`() {
            // created_at과 id를 커서로 하여 게시판의 게시글을 페이지네이션한다
            // given
            val board = dataGenerator.generateBoard()
            repeat(40) {
                dataGenerator.generatePost(board = board)
            }
            val (_, token) = dataGenerator.generateUser()

            // when & then
            val response =
                mvc
                    .perform(
                        get("/api/v1/boards/${board.id!!}/posts?limit=20")
                            .header("Authorization", "Bearer $token")
                            .contentType(MediaType.APPLICATION_JSON),
                    ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.paging.hasNext").value(true))
                    .andReturn()
                    .response
                    .getContentAsString(Charsets.UTF_8)
                    .let {
                        mapper.readValue(it, PostPagingResponse::class.java)
                    }
            assertPostsAreSorted(response.data)
            assertPostsAreInBoard(response.data, board)

            val nextResponse =
                mvc
                    .perform(
                        get(
                            "/api/v1/boards/${board.id!!}/posts?limit=20&nextCreatedAt=${response.paging.nextCreatedAt}&nextId=${response.paging.nextId}",
                        ).header("Authorization", "Bearer $token")
                            .contentType(MediaType.APPLICATION_JSON),
                    ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.paging.hasNext").value(false))
                    .andReturn()
                    .response
                    .getContentAsString(Charsets.UTF_8)
                    .let {
                        mapper.readValue(it, PostPagingResponse::class.java)
                    }
            assertPostsAreSorted(nextResponse.data)
            assertPostsAreInBoard(nextResponse.data, board)
            assertTrue((response.data.map { it.id } + nextResponse.data.map { it.id }).toSet().size == 40)
        }

        @Test
        fun `Only two queries are fired during pagination`() {
            val board = dataGenerator.generateBoard()
            val posts =
                List(10) {
                    dataGenerator.generatePost(board = board).also {
                        dataGenerator.likePost(it)
                    }
                }
            val (_, token) = dataGenerator.generateUser()

            val response =
                queryCounter.assertQueryCount(2) {
                    mvc
                        .perform(
                            get("/api/v1/boards/${board.id!!}/posts?limit=20")
                                .header("Authorization", "Bearer $token")
                                .contentType(MediaType.APPLICATION_JSON),
                        ).andExpect(status().isOk)
                        .andReturn()
                        .response
                        .getContentAsString(Charsets.UTF_8)
                        .let {
                            mapper.readValue(it, PostPagingResponse::class.java)
                        }
                }
            assertPostsAreSame(response.data, posts)
        }

        @Test
        fun `should delete a post with comments`() {
            // 댓글이 남겨진 게시글도 삭제할 수 있다
            // given
            val (user, token) = dataGenerator.generateUser()
            val post = dataGenerator.generatePost(user = user)
            dataGenerator.generateComment(content = "comment", post = post)

            // when
            mvc
                .perform(
                    delete("/api/v1/posts/${post.id!!}")
                        .header("Authorization", "Bearer $token"),
                ).andExpect(status().isNoContent)

            // then
            mvc
                .perform(
                    get("/api/v1/posts/${post.id!!}")
                        .header("Authorization", "Bearer $token"),
                ).andExpect(status().isNotFound)
        }

        private fun assertPostsAreSorted(posts: List<PostDto>) {
            if (posts.size <= 1) return

            posts.zipWithNext().forEach { (current, next) ->
                assertTrue(
                    current.createdAt >= next.createdAt,
                    "Posts are not sorted by createdAt DESC. Failed at id ${current.id} -> ${next.id}",
                )

                if (current.createdAt == next.createdAt) {
                    assertTrue(
                        current.id > next.id,
                        "Posts with same createdAt are not sorted by id DESC. Failed at id ${current.id} -> ${next.id}",
                    )
                }
            }
        }

        private fun assertPostsAreInBoard(
            posts: List<PostDto>,
            board: Board,
        ) {
            posts.forEach {
                assertTrue(it.board.id == board.id)
            }
        }

        private fun assertPostsAreSame(
            targetPosts: List<PostDto>,
            originalPosts: List<Post>,
        ) {
            val originalPostDtos = originalPosts.map { postService.get(postId = it.id!!) }
            targetPosts
                .zip(
                    originalPostDtos.sortedByDescending { it.createdAt },
                ).forEach { (targetPost, originalPostDto) ->
                    assertTrue(targetPost == originalPostDto)
                }
        }
    }
