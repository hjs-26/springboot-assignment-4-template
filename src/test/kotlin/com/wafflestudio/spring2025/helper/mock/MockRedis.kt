package com.wafflestudio.spring2025.helper.mock

import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

class MockRedis : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        TestPropertyValues
            .of(
                "spring.data.redis.host=${redisContainer.host}",
                "spring.data.redis.port=${redisContainer.getMappedPort(6379)}",
            ).applyTo(applicationContext.environment)
    }

    companion object {
        val redisContainer: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("redis:8.4-alpine"))
                .withExposedPorts(6379)
                .apply { start() }
    }
}
