package com.assismohanty.awaken.core.data.ai.dto

import kotlinx.serialization.Serializable

/** Body of `POST /v1/reports` (ARCH §3.8): the reported text and nothing that identifies the hunter. */
@Serializable
data class ReportRequest(
    val promptVersion: String,
    val reason: String,
    val title: String,
    val description: String,
)
