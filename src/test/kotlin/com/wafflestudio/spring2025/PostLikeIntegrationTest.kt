package com.wafflestudio.spring2025

import com.fasterxml.jackson.databind.ObjectMapper
import com.wafflestudio.spring2025.helper.DataGenerator
import com.wafflestudio.spring2025.helper.mock.MockRedis
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
class PostLikeIntegrationTest
    @Autowired
    constructor(
        private val dataGenerator: DataGenerator,
        private val mvc: MockMvc,
        private val mapper: ObjectMapper,
    ) {
        @Test
        fun `should be able to like a post`() {
            // 게시글에 좋아요를 남길 수 있다
            val post = dataGenerator.generatePost()
            val (user1, token1) = dataGenerator.generateUser()

            mvc
                .perform(
                    post("/api/v1/posts/{postId}/like", post.id)
                        .header("Authorization", "Bearer $token1")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)

            val (user2, token2) = dataGenerator.generateUser()
            mvc
                .perform(
                    post("/api/v1/posts/{postId}/like", post.id)
                        .header("Authorization", "Bearer $token2")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)

            mvc
                .perform(
                    get("/api/v1/posts/{postId}", post.id)
                        .header("Authorization", "Bearer $token1")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.likeCount").value(2))
        }

        @Test
        fun `should be able to unlike a post`() {
            // 게시글에 좋아요를 취소할 수 있다
            val post = dataGenerator.generatePost()
            val (user1, token1) = dataGenerator.generateUser()
            val (user2, token2) = dataGenerator.generateUser()
            dataGenerator.likePost(post, user1)
            dataGenerator.likePost(post, user2)

            mvc
                .perform(
                    delete("/api/v1/posts/{postId}/like", post.id)
                        .header("Authorization", "Bearer $token1")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isNoContent)

            mvc
                .perform(
                    delete("/api/v1/posts/{postId}/like", post.id)
                        .header("Authorization", "Bearer $token2")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isNoContent)

            mvc
                .perform(
                    get("/api/v1/posts/{postId}", post.id)
                        .header("Authorization", "Bearer $token1")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.likeCount").value(0))
        }

        @Test
        fun `should increment like count by 1 even when multiple concurrent like requests are made`() {
            // 게시글에 좋아요 등록을 동시에 여러 번 해도 좋아요 수는 1만 올라간다
            val threadPool = Executors.newFixedThreadPool(4)
            val post = dataGenerator.generatePost()
            val (user, token) = dataGenerator.generateUser()

            val jobs =
                List(4) {
                    threadPool.submit {
                        mvc
                            .perform(
                                post("/api/v1/posts/{postId}/like", post.id)
                                    .header("Authorization", "Bearer $token")
                                    .contentType(MediaType.APPLICATION_JSON),
                            ).andExpect(status().isOk)
                    }
                }
            jobs.forEach { it.get() }

            mvc
                .perform(
                    get("/api/v1/posts/{postId}", post.id)
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.likeCount").value(1))
        }

        @Test
        fun `should decrement like count by 1 even when multiple concurrent unlike requests are made`() {
            // 게시글에 좋아요 취소를 동시에 여러 번 해도 좋아요 수는 1만 내려간다
            val threadPool = Executors.newFixedThreadPool(4)
            val post = dataGenerator.generatePost()
            val (user, token) = dataGenerator.generateUser()
            dataGenerator.likePost(post, user)

            val jobs =
                List(4) {
                    threadPool.submit {
                        mvc
                            .perform(
                                delete("/api/v1/posts/{postId}/like", post.id)
                                    .header("Authorization", "Bearer $token")
                                    .contentType(MediaType.APPLICATION_JSON),
                            ).andExpect(status().isNoContent)
                    }
                }
            jobs.forEach { it.get() }

            mvc
                .perform(
                    get("/api/v1/posts/{postId}", post.id)
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.likeCount").value(0))
        }

        @Test
        fun `should not change like count when unliking a post that was not liked by the user`() {
            // 사용자 본인이 좋아요를 누르지 않은 게시글에 좋아요 취소를 해도 좋아요 수가 변하지 않는다
            val post = dataGenerator.generatePost()
            dataGenerator.likePost(post)
            val (user, token) = dataGenerator.generateUser()

            mvc
                .perform(
                    delete("/api/v1/posts/{postId}/like", post.id)
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isNoContent)

            mvc
                .perform(
                    get("/api/v1/posts/{postId}", post.id)
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.likeCount").value(1))
        }
    }
