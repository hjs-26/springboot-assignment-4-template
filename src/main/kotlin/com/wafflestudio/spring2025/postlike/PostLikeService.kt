package com.wafflestudio.spring2025.postlike

import com.wafflestudio.spring2025.post.PostNotFoundException
import com.wafflestudio.spring2025.post.repository.PostRepository
import com.wafflestudio.spring2025.postlike.model.PostLike
import com.wafflestudio.spring2025.postlike.repository.PostLikeRepository
import com.wafflestudio.spring2025.user.model.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PostLikeService(
    private val postLikeRepository: PostLikeRepository,
    private val postRepository: PostRepository,
) {
    @Transactional
    fun likePost(
        postId: Long,
        user: User,
    ) {
        val post = postRepository.findByIdWithLock(postId) ?: throw PostNotFoundException()
        if (postLikeRepository.existsByUserIdAndPostId(user.id!!, postId)) return

        val newLike = PostLike(userId = user.id!!, postId = postId)
        postLikeRepository.save(newLike)
        post.likeCount++
        postRepository.save(post)
    }

    @Transactional
    fun unlikePost(
        postId: Long,
        user: User,
    ) {
        val post = postRepository.findByIdWithLock(postId) ?: throw PostNotFoundException()
        val deleted = postLikeRepository.deleteByUserIdAndPostId(user.id!!, postId)
        if (deleted == 1L) {
            post.likeCount--
        }
        postRepository.save(post)
    }
}
