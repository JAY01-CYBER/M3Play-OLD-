/*
 * This file is part of M3 Play.
 * License information: see LICENSE in the repository root.
 * Copyright and authorship: see Git history and any notices below.
 */

package com.my.kizzy.gateway.entities.presence

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Activity(
    @SerialName("name")
    val name: String?,
    @SerialName("state")
    val state: String? = null,
    @SerialName("state_url")
    val stateUrl: String? = null,
    @SerialName("details")
    val details: String? = null,
    @SerialName("details_url")
    val detailsUrl: String? = null,
    @SerialName("type")
    val type: Int? = 0,
    @SerialName("status_display_type")
    val statusDisplayType: Int? = 0,
    @SerialName("timestamps")
    val timestamps: Timestamps? = null,
    @SerialName("assets")
    val assets: Assets? = null,
    @SerialName("buttons")
    val buttons: List<String?>? = null,
    @SerialName("metadata")
    val metadata: Metadata? = null,
    @SerialName("application_id")
    val applicationId: String? = null,
    @SerialName("url")
    val url: String? = null,
)
