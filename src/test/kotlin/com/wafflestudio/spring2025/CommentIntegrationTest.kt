package com.wafflestudio.spring2025

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.wafflestudio.spring2025.comment.dto.CreateCommentRequest
import com.wafflestudio.spring2025.comment.dto.ListCommentResponse
import com.wafflestudio.spring2025.comment.dto.UpdateCommentRequest
import com.wafflestudio.spring2025.comment.dto.core.CommentDto
import com.wafflestudio.spring2025.comment.repository.CommentRepository
import com.wafflestudio.spring2025.helper.DataGenerator
import com.wafflestudio.spring2025.helper.QueryCounter
import com.wafflestudio.spring2025.helper.mock.MockRedis
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureMockMvc
@ContextConfiguration(initializers = [MockRedis::class])
class CommentIntegrationTest
    @Autowired
    constructor(
        private val dataGenerator: DataGenerator,
        private val queryCounter: QueryCounter,
        private val mvc: MockMvc,
        private val mapper: ObjectMapper,
        private val commentRepository: CommentRepository,
    ) {
        @Test
        fun `Should fetch all comments of a post, sorted by creation time in descending order`() {
            val post = dataGenerator.generatePost()
            val (user, token) = dataGenerator.generateUser()
            repeat(10) {
                dataGenerator.generateComment(post = post, user = user, content = "댓글 $it")
            }

            val response =
                queryCounter.assertQueryCount(2) {
                    mvc
                        .perform(
                            get("/api/v1/posts/{postId}/comments", post.id)
                                .header("Authorization", "Bearer $token")
                                .contentType(MediaType.APPLICATION_JSON),
                        ).andExpect(status().isOk)
                        .andReturn()
                        .response
                        .getContentAsString(Charsets.UTF_8)
                        .let {
                            mapper.readValue(it, object : TypeReference<ListCommentResponse>() {})
                        }
                }
            assertCommentsAreSorted(response)
        }

        @Test
        fun `should create a comment on a post`() {
            // 게시글에 댓글을 달 수 있다
            val post = dataGenerator.generatePost()
            val (_, token) = dataGenerator.generateUser()
            val request = CreateCommentRequest("댓글")

            mvc
                .perform(
                    post("/api/v1/posts/{postId}/comments", post.id)
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.content").value(request.content))
        }

        @Test
        fun `should not create a comment with blank content`() {
            // 빈 내용으로 댓글을 생성할 수 없다
            val post = dataGenerator.generatePost()
            val (_, token) = dataGenerator.generateUser()
            val request = CreateCommentRequest("   ")

            mvc
                .perform(
                    post("/api/v1/posts/{postId}/comments", post.id)
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should update a comment`() {
            // 댓글을 수정할 수 있다
            val post = dataGenerator.generatePost()
            val (user, token) = dataGenerator.generateUser()
            val comment = dataGenerator.generateComment(post = post, user = user)
            val request = UpdateCommentRequest("댓글 수정")

            mvc
                .perform(
                    put("/api/v1/posts/{postId}/comments/{id}", post.id, comment.id)
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.content").value(request.content))
        }

        @Test
        fun `should not update a comment with blank content`() {
            // 빈 내용으로 댓글을 수정할 수 없다
            val post = dataGenerator.generatePost()
            val (user, token) = dataGenerator.generateUser()
            val comment = dataGenerator.generateComment(post = post, user = user)
            val request = UpdateCommentRequest("   ")

            mvc
                .perform(
                    put("/api/v1/posts/{postId}/comments/{id}", post.id, comment.id)
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should not update a comment written by another user`() {
            // 다른 사람이 작성한 댓글을 수정할 수 없다
            val post = dataGenerator.generatePost()
            val (user, token) = dataGenerator.generateUser()
            val comment = dataGenerator.generateComment(post = post)
            val request = UpdateCommentRequest("댓글 수정")

            mvc
                .perform(
                    put("/api/v1/posts/{postId}/comments/{id}", post.id, comment.id)
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)),
                ).andExpect(status().isForbidden)
        }

        @Test
        fun `should delete a comment`() {
            // 댓글을 삭제할 수 있다
            val post = dataGenerator.generatePost()
            val (user, token) = dataGenerator.generateUser()
            val comment = dataGenerator.generateComment(post = post, user = user)

            mvc
                .perform(
                    delete("/api/v1/posts/{postId}/comments/{id}", post.id, comment.id)
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isNoContent)
        }

        @Test
        fun `should not delete a comment written by another user`() {
            // 다른 사람이 작성한 댓글을 삭제할 수 없다
            val post = dataGenerator.generatePost()
            val (user, token) = dataGenerator.generateUser()
            val comment = dataGenerator.generateComment(post = post)

            mvc
                .perform(
                    delete("/api/v1/posts/{postId}/comments/{id}", post.id, comment.id)
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isForbidden)
        }

        @Test
        fun `should delete comments when post is deleted`() {
            // 게시글 삭제 시 댓글도 함께 삭제된다
            val (user, token) = dataGenerator.generateUser()
            val post = dataGenerator.generatePost(user = user)
            val comment = dataGenerator.generateComment(post = post)
            mvc
                .perform(
                    delete("/api/v1/posts/{postId}", post.id)
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isNoContent)

            assertTrue(commentRepository.findByIdOrNull(comment.id!!) == null)
        }

        private fun assertCommentsAreSorted(comments: List<CommentDto>) {
            if (comments.size <= 1) return

            comments.zipWithNext().forEach { (current, next) ->
                assertTrue(
                    current.createdAt >= next.createdAt,
                    "Posts are not sorted by createdAt DESC. Failed at id ${current.id} -> ${next.id}",
                )
            }
        }
    }
