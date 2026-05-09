package com.example.routerservice.config

import com.example.routerservice.domain.CardRequestMessage
import com.example.routerservice.domain.OutboxMessageDto
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import jakarta.jms.ConnectionFactory
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jms.annotation.EnableJms
import org.springframework.jms.config.DefaultJmsListenerContainerFactory
import org.springframework.jms.support.converter.MappingJackson2MessageConverter
import org.springframework.jms.support.converter.MessageConverter
import org.springframework.jms.support.converter.MessageType

@Configuration
@EnableJms
class JmsConfig {

    private val log = LoggerFactory.getLogger(JmsConfig::class.java)

    @Bean
    fun jmsListenerContainerFactory(connectionFactory: ConnectionFactory): DefaultJmsListenerContainerFactory {
        val factory = DefaultJmsListenerContainerFactory()
        factory.setConnectionFactory(connectionFactory)
        factory.setConcurrency("1-5")
        factory.setSessionTransacted(true)
        factory.setErrorHandler { t ->
            log.error("JMS listener error: ${t.message}", t)
        }
        factory.setMessageConverter(messageConverter())
        return factory
    }

    @Bean
    fun messageConverter(): MessageConverter {
        val converter = MappingJackson2MessageConverter()
        converter.setTypeIdPropertyName("_type")
        converter.setTargetType(MessageType.TEXT)
        val mapper = ObjectMapper().apply {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
        converter.setObjectMapper(mapper)
        converter.setTypeIdMappings(
            mapOf(
                "CardRequestMessage" to CardRequestMessage::class.java,
                "OutboxMessageDto" to OutboxMessageDto::class.java,
            )
        )
        return converter
    }
}
