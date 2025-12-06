package com.wafflestudio.spring2025.postlike.controller

import com.wafflestudio.spring2025.postlike.PostLikeService
import com.wafflestudio.spring2025.postlike.dto.LikePostResponse
import com.wafflestudio.spring2025.postlike.dto.UnlikePostResponse
import com.wafflestudio.spring2025.user.LoggedInUser
import com.wafflestudio.spring2025.user.model.User
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class PostLikeController(
    private val postLikeService: PostLikeService,
) {
    @PostMapping("/api/v1/posts/{postId}/like")
    fun likePost(
        @PathVariable postId: Long,
        @LoggedInUser user: User,
    ): ResponseEntity<LikePostResponse> {
        postLikeService.likePost(postId, user)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/api/v1/posts/{postId}/like")
    fun unlikePost(
        @PathVariable postId: Long,
        @LoggedInUser user: User,
    ): ResponseEntity<UnlikePostResponse> {
        postLikeService.unlikePost(postId, user)
        return ResponseEntity.noContent().build()
    }
}
