package com.example.routerservice.domain

internal enum class OutboxStatus {
    NEW,
    IN_PROGRESS,
    RETRY,
    PUBLISHED,
    DEAD,
}
package com.example.routerservice.domain

internal enum class OutboxStatus {
    NEW,
    IN_PROGRESS,
    SENT,
    FAILED,
}
